package com.recipefinder.ui.preview;

import com.recipefinder.entities.UserId;
import com.recipefinder.frameworks_drivers.persistence.json.JsonMealPlanRepository;
import com.recipefinder.frameworks_drivers.session.InMemorySessionManager;
import com.recipefinder.frameworks_drivers.ui.SwingUiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.interfaces.MealPlanRepository;
import com.recipefinder.interfaces.MealPlanSession;
import com.recipefinder.interfaces.SessionManager;
import com.recipefinder.ui.WeeklyPlannerCoordinator;
import com.recipefinder.ui.views.WeeklyPlannerView;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Development launcher using isolated preview data and JSON storage. */
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

        // Development session only. The real application will supply
        // the session established by its login use case.
        SessionManager sessionManager =
                new InMemorySessionManager();

        sessionManager.startSession(
                new UserId(PREVIEW_USER_ID));

        MealPlanSession mealPlanSession =
                sessionManager::getCurrentUserId;

        MealPlanViewModel viewModel = new MealPlanViewModel();
        viewModel.bindUser(PREVIEW_USER_ID);

        ExecutorService worker =
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread =
                            new Thread(task, "weekly-schedule-worker");

                    thread.setDaemon(true);
                    return thread;
                });

        WeeklyPlannerCoordinator coordinator =
                new WeeklyPlannerCoordinator(
                        repository,
                        mealPlanSession,
                        viewModel,
                        new SwingUiDispatcher(),
                        worker);

        WeeklyPlannerView view = new WeeklyPlannerView(
                viewModel,
                monday,
                coordinator::loadWeek);

        JFrame frame =
                new JFrame("Recipe Finder - Weekly JSON preview");

        frame.setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);

        frame.setContentPane(view);
        frame.pack();
        frame.setMinimumSize(new Dimension(1050, 450));
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                view.close();

                // Invalidate any results still waiting to reach the UI.
                viewModel.bindUser(null);
                sessionManager.endSession();

                worker.shutdownNow();
            }
        });

        frame.setVisible(true);
        coordinator.loadWeek(monday);
    }
}