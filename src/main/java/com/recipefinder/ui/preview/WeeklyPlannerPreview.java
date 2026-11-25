package com.recipefinder.ui.preview;

import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.ui.views.WeeklyPlannerView;
import com.recipefinder.usecases.mealplan.common.MealPlanData;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Development-only launcher for inspecting the weekly Swing layout. */
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

        MealPlanViewModel viewModel = new MealPlanViewModel();
        viewModel.bindUser(PREVIEW_USER_ID);

        WeeklyPlannerView view = new WeeklyPlannerView(
                viewModel,
                monday,
                week -> loadEmptyPreviewWeek(viewModel, week));

        JFrame frame =
                new JFrame("Recipe Finder - Weekly UI preview");

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
            }
        });

        loadEmptyPreviewWeek(viewModel, monday);
        frame.setVisible(true);
    }

    private static void loadEmptyPreviewWeek(
            MealPlanViewModel viewModel,
            LocalDate monday) {

        long requestId = viewModel.beginLoad(monday);

        if (requestId == MealPlanViewModel.NO_REQUEST) {
            return;
        }

        MealPlanData previewPlan = new MealPlanData(
                PREVIEW_USER_ID,
                monday,
                0,
                List.of());

        viewModel.acceptPlan(
                requestId,
                previewPlan,
                "UI preview: changes are not saved.");
    }
}