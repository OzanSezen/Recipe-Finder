package com.recipefinder.ui.preview;

import com.recipefinder.entities.FavoriteCollection;
import com.recipefinder.entities.RecipeKey;
import com.recipefinder.entities.UserId;
import com.recipefinder.frameworks_drivers.api.themealdb.TheMealDBRecipeGateway;
import com.recipefinder.frameworks_drivers.persistence.json.JsonMealPlanRepository;
import com.recipefinder.frameworks_drivers.session.InMemorySessionManager;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.interfaces.SavedRecipeLookup;
import com.recipefinder.interfaces.SessionManager;
import com.recipefinder.ui.weekly.WeeklyPlannerFactory;
import com.recipefinder.ui.weekly.WeeklyPlannerFeature;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Development launcher for the reusable weekly scheduling feature. */
public final class WeeklyPlannerPreview {

    private static final String PREVIEW_USER_ID = "ui-preview";

    private WeeklyPlannerPreview() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                WeeklyPlannerPreview::showWindow);
    }

    private static void showWindow() {
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

        List<RecipeKey> previewKeys = List.of(
                new RecipeKey("themealdb", "52772"),
                new RecipeKey("themealdb", "52795"));

        AtomicReference<FavoriteCollection> previewFavorites =
                new AtomicReference<>(
                        new FavoriteCollection(
                                previewUser,
                                Set.copyOf(previewKeys)));

        SavedRecipeLookup savedRecipeLookup = (userId, recipeKey) -> {
            FavoriteCollection favorites = previewFavorites.get();

            return favorites.userId().equals(userId)
                    && favorites.contains(recipeKey);
        };

        WeeklyPlannerFeature feature =
                WeeklyPlannerFactory.create(
                        repository,
                        mealPlanSession,
                        savedRecipeLookup,
                        new TheMealDBRecipeGateway(),
                        LocalDate.now());

        JButton reloadFavorites =
                new JButton("Reload favorites");

        JToggleButton emptyFavorites =
                new JToggleButton("Empty favorites");

        JButton sessionButton =
                new JButton("Sign out");

        reloadFavorites.addActionListener(event ->
                supplyPreviewFavorites(
                        feature,
                        previewFavorites.get()));

        emptyFavorites.addActionListener(event -> {
            Set<RecipeKey> keys = emptyFavorites.isSelected()
                    ? Set.of()
                    : Set.copyOf(previewKeys);

            FavoriteCollection updatedFavorites =
                    new FavoriteCollection(previewUser, keys);

            previewFavorites.set(updatedFavorites);

            supplyPreviewFavorites(
                    feature,
                    updatedFavorites);
        });

        sessionButton.addActionListener(event -> {
            boolean signedIn =
                    sessionManager.getCurrentUserId().isPresent();

            if (signedIn) {
                sessionManager.endSession();
            } else {
                sessionManager.startSession(previewUser);
            }

            feature.sessionChanged();

            boolean nowSignedIn =
                    sessionManager.getCurrentUserId().isPresent();

            sessionButton.setText(
                    nowSignedIn
                            ? "Sign out"
                            : "Sign in preview");

            if (nowSignedIn) {
                supplyPreviewFavorites(
                        feature,
                        previewFavorites.get());
            }
        });

        JPanel developmentControls =
                new JPanel(new FlowLayout(FlowLayout.LEFT));

        developmentControls.add(reloadFavorites);
        developmentControls.add(emptyFavorites);
        developmentControls.add(sessionButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(developmentControls, BorderLayout.NORTH);
        content.add(feature.getView(), BorderLayout.CENTER);

        JFrame frame = new JFrame(
                "Recipe Finder - Weekly development preview");

        frame.setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);

        frame.setContentPane(content);
        frame.pack();
        frame.setMinimumSize(new Dimension(1050, 600));
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                feature.close();

                // This launcher owns its development session.
                sessionManager.endSession();
            }
        });

        frame.setVisible(true);

        feature.open();

        supplyPreviewFavorites(
                feature,
                previewFavorites.get());
    }

    private static void supplyPreviewFavorites(
            WeeklyPlannerFeature feature,
            FavoriteCollection favorites) {

        long requestId = feature.beginSavedRecipesLoad();

        if (requestId == WeeklyPlannerFeature.NO_FAVORITES_REQUEST) {
            return;
        }

        feature.acceptSavedRecipeKeys(
                requestId,
                List.copyOf(favorites.recipeKeys()));
    }
}