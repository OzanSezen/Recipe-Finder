package com.recipefinder.ui.weekly;

import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.controllers.DinnerAssignmentRequest;
import com.recipefinder.interface_adapters.controllers.LoadMealPlanController;
import com.recipefinder.interface_adapters.controllers.RemoveDinnerController;
import com.recipefinder.interface_adapters.controllers.SetDinnerController;
import com.recipefinder.interface_adapters.presenters.LoadMealPlanPresenter;
import com.recipefinder.interface_adapters.presenters.RemoveDinnerPresenter;
import com.recipefinder.interface_adapters.presenters.SetDinnerPresenter;
import com.recipefinder.interface_adapters.viewmodels.MealPlanState;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.interfaces.SavedRecipeLookup;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure.Code;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanInteractor;
import com.recipefinder.usecases.mealplan.removedinner.RemoveDinnerInteractor;
import com.recipefinder.usecases.mealplan.setdinner.SetDinnerInteractor;

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
    private final SavedRecipeLookup savedRecipes;

    public WeeklyPlannerCoordinator(
            MealPlanRepository repository,
            MealPlanSession session,
            MealPlanViewModel viewModel,
            UiDispatcher uiDispatcher,
            Executor backgroundExecutor,
            SavedRecipeLookup savedRecipes) {

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

        this.savedRecipes = Objects.requireNonNull(
                savedRecipes, "savedRecipes");
    }

    public void loadWeek(LocalDate weekStart) {
        Objects.requireNonNull(weekStart, "weekStart");

        uiDispatcher.dispatch(() -> startLoad(weekStart));
    }

    public void removeDinner(LocalDate date) {
        Objects.requireNonNull(date, "date");

        uiDispatcher.dispatch(() -> startRemove(date));
    }

    public void assignDinner(DinnerAssignmentRequest request) {
        Objects.requireNonNull(request, "request");

        uiDispatcher.dispatch(() -> startAssignment(request));
    }

    private void startLoad(LocalDate weekStart) {
        String expectedUserId = viewModel.getState().userId();

        long requestId = viewModel.beginLoad(weekStart);

        if (requestId == MealPlanViewModel.NO_REQUEST) {
            return;
        }

        LoadMealPlanPresenter presenter = new LoadMealPlanPresenter(
                viewModel, uiDispatcher, requestId);

        LoadMealPlanInteractor interactor = new LoadMealPlanInteractor(
                repository,
                sessionFor(expectedUserId),
                presenter);

        LoadMealPlanController controller =
                new LoadMealPlanController(interactor);

        runInBackground(
                requestId,
                () -> controller.execute(weekStart),
                "The week could not be loaded. Please try again.");
    }

    private void startRemove(LocalDate date) {
        MealPlanState current = viewModel.getState();

        long requestId = viewModel.beginChange();

        if (requestId == MealPlanViewModel.NO_REQUEST) {
            return;
        }

        RemoveDinnerPresenter presenter = new RemoveDinnerPresenter(
                viewModel, uiDispatcher, requestId);

        RemoveDinnerInteractor interactor = new RemoveDinnerInteractor(
                repository,
                sessionFor(current.userId()),
                presenter);

        RemoveDinnerController controller =
                new RemoveDinnerController(interactor);

        runInBackground(
                requestId,
                () -> controller.execute(
                        current.plan().weekStart(),
                        date,
                        current.plan().revision()),
                "The dinner could not be removed. Please try again.");
    }

    private void startAssignment(DinnerAssignmentRequest request) {
        MealPlanState current = viewModel.getState();

        // Discard a request belonging to a screen that has changed.
        if (!Objects.equals(current.userId(), request.userId())
                || !Objects.equals(
                current.weekStart(), request.weekStart())) {
            return;
        }

        long requestId = viewModel.beginChange();

        if (requestId == MealPlanViewModel.NO_REQUEST) {
            return;
        }

        SetDinnerPresenter presenter = new SetDinnerPresenter(
                viewModel, uiDispatcher, requestId);

        SetDinnerInteractor interactor = new SetDinnerInteractor(
                repository,
                sessionFor(request.userId()),
                savedRecipes,
                presenter);

        SetDinnerController controller =
                new SetDinnerController(interactor);

        runInBackground(
                requestId,
                () -> controller.execute(
                        request.weekStart(),
                        request.date(),
                        request.recipeProvider(),
                        request.recipeId(),
                        request.numServings(),
                        request.expectedRevision(),
                        request.replacementConfirmed()),
                "The dinner could not be saved. Please try again.");
    }

    private MealPlanSession sessionFor(String expectedUserId) {
        return () -> session.currentUserId().filter(userId ->
                Objects.equals(expectedUserId, userId.value()));
    }

    private void runInBackground(
            long requestId,
            Runnable action,
            String failureMessage) {

        try {
            backgroundExecutor.execute(() -> {
                if (!viewModel.isCurrentRequest(requestId)) {
                    return;
                }

                try {
                    action.run();
                } catch (RuntimeException exception) {
                    reportUnexpectedFailure(
                            requestId, failureMessage, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            reportUnexpectedFailure(
                    requestId, failureMessage, exception);
        }
    }

    private void reportUnexpectedFailure(
            long requestId,
            String message,
            RuntimeException exception) {

        LOGGER.log(
                System.Logger.Level.ERROR,
                "Unexpected weekly scheduling failure.",
                exception);

        MealPlanFailure failure =
                new MealPlanFailure(Code.STORAGE_ERROR, message);

        uiDispatcher.dispatch(() ->
                viewModel.acceptFailure(requestId, failure));
    }
}