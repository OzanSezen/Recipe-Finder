package com.recipefinder.ui.weekly;

import com.recipefinder.entities.RecipeKey;
import com.recipefinder.entities.UserId;
import com.recipefinder.frameworks_drivers.images.RecipeDisplayLoader;
import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.ui.views.WeeklyPlannerView;
import com.recipefinder.ui.views.WeeklyPlannerView.SavedRecipeOption;

import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Owns the weekly screen and its background resources.
 * <p>
 * Call open, sessionChanged, beginSavedRecipesLoad, and close on
 * Swing's event thread.
 * <p>
 * Favorites results may be supplied from a background thread.
 */
public final class WeeklyPlannerFeature implements AutoCloseable {

    public static final long NO_FAVORITES_REQUEST = -1L;

    private final MealPlanSession session;
    private final MealPlanViewModel viewModel;
    private final WeeklyPlannerCoordinator coordinator;
    private final WeeklyPlannerView view;
    private final RecipeDisplayLoader displayLoader;
    private final UiDispatcher uiDispatcher;
    private final ExecutorService worker;
    private final ExecutorService displayWorker;

    // These fields are accessed only on Swing's event thread.
    private long favoritesGeneration;
    private String favoritesUserId;
    private boolean favoritesResultReceived;
    private boolean closed;

    WeeklyPlannerFeature(
            MealPlanSession session,
            MealPlanViewModel viewModel,
            WeeklyPlannerCoordinator coordinator,
            WeeklyPlannerView view,
            RecipeDisplayLoader displayLoader,
            UiDispatcher uiDispatcher,
            ExecutorService worker,
            ExecutorService displayWorker) {

        this.session = session;
        this.viewModel = viewModel;
        this.coordinator = coordinator;
        this.view = view;
        this.displayLoader = displayLoader;
        this.uiDispatcher = uiDispatcher;
        this.worker = worker;
        this.displayWorker = displayWorker;
    }

    public WeeklyPlannerView getView() {
        return view;
    }

    /** Call when the application shows the weekly screen. */
    public void open() {
        requireUiThread();

        if (closed) {
            return;
        }

        String userId = currentUserId();

        if (!Objects.equals(
                userId, viewModel.getState().userId())) {
            bindSession(userId);
        }

        loadSelectedWeek();
    }

    /**
     * Call after login, logout, or switching accounts.
     * <p>
     * This also invalidates results from the previous session,
     * including when the same account signs in again.
     */
    public void sessionChanged() {
        requireUiThread();

        if (closed) {
            return;
        }

        bindSession(currentUserId());
        loadSelectedWeek();
    }

    /**
     * Starts a favorites refresh and returns its request ID.
     * <p>
     * Call open or sessionChanged before starting the refresh.
     */
    public long beginSavedRecipesLoad() {
        requireUiThread();

        if (closed) {
            return NO_FAVORITES_REQUEST;
        }

        String userId = currentUserId();

        if (userId == null
                || !Objects.equals(
                userId, viewModel.getState().userId())) {
            return NO_FAVORITES_REQUEST;
        }

        favoritesUserId = userId;
        favoritesResultReceived = false;

        long requestId = ++favoritesGeneration;

        view.showSavedRecipesLoading(userId);

        return requestId;
    }

    /**
     * Supplies the recipe keys returned by a favorites request.
     * <p>
     * Duplicate keys are removed. An empty list is a valid result.
     */
    public void acceptSavedRecipeKeys(
            long requestId,
            List<RecipeKey> recipeKeys) {

        Objects.requireNonNull(recipeKeys, "recipeKeys");

        List<RecipeKey> snapshot =
                List.copyOf(new LinkedHashSet<>(recipeKeys));

        uiDispatcher.dispatch(
                () -> loadRecipeOptions(requestId, snapshot));
    }

    /** Reports a favorites-storage failure for the given request. */
    public void showSavedRecipesError(
            long requestId,
            String message) {

        Objects.requireNonNull(message, "message");

        uiDispatcher.dispatch(() -> {
            if (!isCurrentFavoritesRequest(requestId)
                    || favoritesResultReceived) {
                return;
            }

            favoritesResultReceived = true;

            view.showSavedRecipesError(
                    favoritesUserId,
                    message);
        });
    }

    private void loadRecipeOptions(
            long requestId,
            List<RecipeKey> recipeKeys) {

        if (!isCurrentFavoritesRequest(requestId)
                || favoritesResultReceived) {
            return;
        }

        favoritesResultReceived = true;

        String userId = favoritesUserId;

        if (recipeKeys.isEmpty()) {
            view.setSavedRecipes(userId, List.of());
            return;
        }

        List<SavedRecipeOption> options = new ArrayList<>();

        for (RecipeKey key : recipeKeys) {
            displayLoader.load(key, display -> {
                if (!isCurrentFavoritesRequest(requestId)) {
                    return;
                }

                options.add(new SavedRecipeOption(
                        key.getProvider(),
                        key.getId(),
                        display.title()));

                if (options.size() == recipeKeys.size()) {
                    options.sort(
                            Comparator.comparing(
                                    SavedRecipeOption::title,
                                    String.CASE_INSENSITIVE_ORDER));

                    view.setSavedRecipes(
                            userId,
                            List.copyOf(options));
                }
            });
        }
    }

    private boolean isCurrentFavoritesRequest(long requestId) {
        return !closed
                && requestId != NO_FAVORITES_REQUEST
                && requestId == favoritesGeneration
                && favoritesUserId != null
                && Objects.equals(
                favoritesUserId,
                viewModel.getState().userId())
                && Objects.equals(
                favoritesUserId,
                currentUserId());
    }

    private void bindSession(String userId) {
        favoritesGeneration++;
        favoritesUserId = null;
        favoritesResultReceived = false;

        viewModel.bindUser(userId);
    }

    private void loadSelectedWeek() {
        if (viewModel.getState().userId() != null) {
            coordinator.loadWeek(view.getSelectedWeek());
        }
    }

    private String currentUserId() {
        return session.currentUserId()
                .map(UserId::value)
                .orElse(null);
    }

    private static void requireUiThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(
                    "This operation must run on Swing's event thread.");
        }
    }

    /** Permanently disposes this weekly feature. */
    @Override
    public void close() {
        requireUiThread();

        if (closed) {
            return;
        }

        closed = true;

        favoritesGeneration++;
        favoritesUserId = null;

        // Invalidates pending scheduling results and clears the screen.
        viewModel.bindUser(null);

        view.close();
        displayLoader.close();

        worker.shutdownNow();
        displayWorker.shutdownNow();
    }
}