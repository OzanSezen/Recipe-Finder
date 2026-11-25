package com.recipefinder.ui;

import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.controllers.LoadMealPlanController;
import com.recipefinder.interface_adapters.presenters.LoadMealPlanPresenter;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure.Code;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanInputBoundary;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanInteractor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Connects weekly UI requests to the scheduling use cases. */
public final class WeeklyPlannerCoordinator {

    private static final System.Logger LOGGER =
            System.getLogger(WeeklyPlannerCoordinator.class.getName());

    private final MealPlanRepository repository;
    private final MealPlanSession session;
    private final MealPlanViewModel viewModel;
    private final UiDispatcher uiDispatcher;
    private final Executor backgroundExecutor;

    public WeeklyPlannerCoordinator(
            MealPlanRepository repository,
            MealPlanSession session,
            MealPlanViewModel viewModel,
            UiDispatcher uiDispatcher,
            Executor backgroundExecutor) {

        this.repository = Objects.requireNonNull(
                repository, "repository");

        this.session = Objects.requireNonNull(
                session, "session");

        this.viewModel = Objects.requireNonNull(
                viewModel, "viewModel");

        this.uiDispatcher = Objects.requireNonNull(
                uiDispatcher, "uiDispatcher");

        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor, "backgroundExecutor");
    }

    public void loadWeek(LocalDate weekStart) {
        Objects.requireNonNull(weekStart, "weekStart");

        uiDispatcher.dispatch(() -> startLoad(weekStart));
    }

    private void startLoad(LocalDate weekStart) {
        String expectedUserId = viewModel.getState().userId();

        long requestId = viewModel.beginLoad(weekStart);

        if (requestId == MealPlanViewModel.NO_REQUEST) {
            return;
        }

        // A queued request must not switch to another user's account.
        MealPlanSession requestSession = () ->
                session.currentUserId().filter(userId ->
                        Objects.equals(
                                expectedUserId, userId.value()));

        LoadMealPlanPresenter presenter = new LoadMealPlanPresenter(
                viewModel,
                uiDispatcher,
                requestId);

        LoadMealPlanInputBoundary interactor =
                new LoadMealPlanInteractor(
                        repository,
                        requestSession,
                        presenter);

        LoadMealPlanController controller =
                new LoadMealPlanController(interactor);

        try {
            backgroundExecutor.execute(() -> {
                if (!viewModel.isCurrentRequest(requestId)) {
                    return;
                }

                try {
                    controller.execute(weekStart);
                } catch (RuntimeException exception) {
                    reportUnexpectedFailure(requestId, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            reportUnexpectedFailure(requestId, exception);
        }
    }

    private void reportUnexpectedFailure(
            long requestId,
            RuntimeException exception) {

        LOGGER.log(
                System.Logger.Level.ERROR,
                "Unexpected failure while loading a weekly plan.",
                exception);

        MealPlanFailure failure = new MealPlanFailure(
                Code.STORAGE_ERROR,
                "The week could not be loaded. Please try again.");

        uiDispatcher.dispatch(() ->
                viewModel.acceptFailure(requestId, failure));
    }
}