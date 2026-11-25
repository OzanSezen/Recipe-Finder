package com.recipefinder.interface_adapters.presenters;

import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanOutputBoundary;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanOutputData;

import java.util.Objects;

/** Presents the result of one load-week request. */
public final class LoadMealPlanPresenter
        implements LoadMealPlanOutputBoundary {

    private final MealPlanViewModel viewModel;
    private final UiDispatcher uiDispatcher;
    private final long requestId;

    public LoadMealPlanPresenter(
            MealPlanViewModel viewModel,
            UiDispatcher uiDispatcher,
            long requestId) {

        this.viewModel = Objects.requireNonNull(
                viewModel, "viewModel");

        this.uiDispatcher = Objects.requireNonNull(
                uiDispatcher, "uiDispatcher");

        if (requestId < 1) {
            throw new IllegalArgumentException(
                    "A valid request ID is required.");
        }

        this.requestId = requestId;
    }

    @Override
    public void prepareSuccessView(
            LoadMealPlanOutputData outputData) {

        Objects.requireNonNull(outputData, "outputData");

        uiDispatcher.dispatch(() -> viewModel.acceptPlan(
                requestId,
                outputData.plan(),
                "Week loaded."));
    }

    @Override
    public void prepareFailView(MealPlanFailure failure) {
        Objects.requireNonNull(failure, "failure");

        uiDispatcher.dispatch(() -> viewModel.acceptFailure(
                requestId,
                failure));
    }
}