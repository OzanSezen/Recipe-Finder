package com.recipefinder.ui.weekly;

import com.recipefinder.frameworks_drivers.images.RecipeDisplayLoader;
import com.recipefinder.frameworks_drivers.ui.SwingUiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.interfaces.RecipeGateway;
import com.recipefinder.interfaces.SavedRecipeLookup;
import com.recipefinder.ui.views.WeeklyPlannerView;

import javax.swing.SwingUtilities;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Assembles the weekly scheduling UI and its dependencies. */
public final class WeeklyPlannerFactory {

    private WeeklyPlannerFactory() {
    }

    public static WeeklyPlannerFeature create(
            MealPlanRepository repository,
            MealPlanSession session,
            SavedRecipeLookup savedRecipes,
            RecipeGateway recipeGateway,
            LocalDate initialDate) {

        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(savedRecipes, "savedRecipes");
        Objects.requireNonNull(recipeGateway, "recipeGateway");
        Objects.requireNonNull(initialDate, "initialDate");

        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "Create the weekly screen on Swing's event thread.");
        }

        MealPlanViewModel viewModel = new MealPlanViewModel();

        SwingUiDispatcher uiDispatcher =
                new SwingUiDispatcher();

        ExecutorService worker =
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(
                            task,
                            "weekly-schedule-worker");

                    thread.setDaemon(true);
                    return thread;
                });

        ExecutorService displayWorker =
                Executors.newFixedThreadPool(2, task -> {
                    Thread thread = new Thread(
                            task,
                            "weekly-recipe-display-worker");

                    thread.setDaemon(true);
                    return thread;
                });

        RecipeDisplayLoader displayLoader =
                new RecipeDisplayLoader(
                        recipeGateway,
                        uiDispatcher,
                        displayWorker);

        WeeklyPlannerCoordinator coordinator =
                new WeeklyPlannerCoordinator(
                        repository,
                        session,
                        viewModel,
                        uiDispatcher,
                        worker,
                        savedRecipes);

        WeeklyPlannerView view = new WeeklyPlannerView(
                viewModel,
                initialDate,
                coordinator::loadWeek,
                coordinator::removeDinner,
                coordinator::assignDinner);

        view.setRecipeDisplayLoader(displayLoader);

        return new WeeklyPlannerFeature(
                session,
                viewModel,
                coordinator,
                view,
                displayLoader,
                uiDispatcher,
                worker,
                displayWorker);
    }
}
