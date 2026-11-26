package com.recipefinder.ui.preview;

import com.recipefinder.entities.FavoriteCollection;
import com.recipefinder.entities.RecipeKey;
import com.recipefinder.entities.UserId;
import com.recipefinder.frameworks_drivers.api.themealdb.TheMealDBRecipeGateway;
import com.recipefinder.frameworks_drivers.images.RecipeDisplayLoader;
import com.recipefinder.frameworks_drivers.persistence.json.JsonMealPlanRepository;
import com.recipefinder.frameworks_drivers.session.InMemorySessionManager;
import com.recipefinder.frameworks_drivers.ui.SwingUiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.interfaces.RecipeGateway;
import com.recipefinder.interfaces.SavedRecipeLookup;
import com.recipefinder.interfaces.SessionManager;
import com.recipefinder.ui.WeeklyPlannerCoordinator;
import com.recipefinder.ui.views.WeeklyPlannerView;
import com.recipefinder.ui.views.WeeklyPlannerView.SavedRecipeOption;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Development launcher with preview favorites, real recipe details,
 * and isolated JSON meal-plan storage.
 */
public final class WeeklyPlannerPreview {

    private static final String PREVIEW_USER_ID = "ui-preview";

    private WeeklyPlannerPreview() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                WeeklyPlannerPreview::showWindow);
    }

    private static void showWindow() {
        LocalDate monday = LocalDate.now().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Path storageFile = Path.of(
                "data",
                "weekly-preview",
                "meal-plans.json").toAbsolutePath();

        System.out.println(
                "Weekly preview storage: " + storageFile);

        MealPlanRepository repository =
                new JsonMealPlanRepository(storageFile);

        UserId previewUser = new UserId(PREVIEW_USER_ID);

        SessionManager sessionManager =
                new InMemorySessionManager();

        sessionManager.startSession(previewUser);

        MealPlanSession mealPlanSession =
                sessionManager::getCurrentUserId;

        MealPlanViewModel viewModel = new MealPlanViewModel();
        viewModel.bindUser(PREVIEW_USER_ID);

        // Development favorites until real favorites are integrated.
        RecipeKey firstRecipe =
                new RecipeKey("themealdb", "52772");

        RecipeKey secondRecipe =
                new RecipeKey("themealdb", "52795");

        FavoriteCollection previewFavorites =
                new FavoriteCollection(
                        previewUser,
                        Set.of(firstRecipe, secondRecipe));

        SavedRecipeLookup savedRecipeLookup = (userId, recipeKey) ->
                previewFavorites.userId().equals(userId)
                        && previewFavorites.contains(recipeKey);

        // Scheduling and JSON work use their own worker.
        ExecutorService worker =
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread =
                            new Thread(task, "weekly-schedule-worker");

                    thread.setDaemon(true);
                    return thread;
                });

        // API and photo downloads cannot delay JSON saves.
        ExecutorService displayWorker =
                Executors.newFixedThreadPool(2, task -> {
                    Thread thread =
                            new Thread(task, "weekly-recipe-display-worker");

                    thread.setDaemon(true);
                    return thread;
                });

        SwingUiDispatcher uiDispatcher =
                new SwingUiDispatcher();

        RecipeGateway recipeGateway =
                new TheMealDBRecipeGateway();

        RecipeDisplayLoader displayLoader =
                new RecipeDisplayLoader(
                        recipeGateway,
                        uiDispatcher,
                        displayWorker);

        WeeklyPlannerCoordinator coordinator =
                new WeeklyPlannerCoordinator(
                        repository,
                        mealPlanSession,
                        viewModel,
                        uiDispatcher,
                        worker,
                        savedRecipeLookup);

        WeeklyPlannerView view = new WeeklyPlannerView(
                viewModel,
                monday,
                coordinator::loadWeek,
                coordinator::removeDinner,
                coordinator::assignDinner);

        view.setRecipeDisplayLoader(displayLoader);

        loadRecipeOptions(
                view,
                displayLoader,
                List.of(firstRecipe, secondRecipe));

        JFrame frame = new JFrame(
                "Recipe Finder - Weekly development preview");

        frame.setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);

        frame.setContentPane(view);
        frame.pack();
        frame.setMinimumSize(new Dimension(1050, 560));
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                view.close();
                displayLoader.close();

                viewModel.bindUser(null);
                sessionManager.endSession();

                worker.shutdownNow();
                displayWorker.shutdownNow();
            }
        });

        frame.setVisible(true);
        coordinator.loadWeek(monday);
    }

    private static void loadRecipeOptions(
            WeeklyPlannerView view,
            RecipeDisplayLoader displayLoader,
            List<RecipeKey> recipeKeys) {

        List<SavedRecipeOption> options = new ArrayList<>();

        for (RecipeKey key : recipeKeys) {
            displayLoader.load(key, display -> {
                // These callbacks run on Swing's event thread.
                options.add(new SavedRecipeOption(
                        key.getProvider(),
                        key.getId(),
                        display.title()));

                if (options.size() == recipeKeys.size()) {
                    options.sort(
                            Comparator.comparing(
                                    SavedRecipeOption::title));

                    view.setSavedRecipes(
                            PREVIEW_USER_ID,
                            List.copyOf(options));
                }
            });
        }
    }
}