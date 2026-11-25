package com.recipefinder.interface_adapters.presenters;

import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.setdinner.SetDinnerOutputBoundary;
import com.recipefinder.usecases.mealplan.setdinner.SetDinnerOutputData;

import java.util.Objects;

/** Presents the result of one assign/replace request. */
public final class SetDinnerPresenter
        implements SetDinnerOutputBoundary {

    private final MealPlanViewModel viewModel;
    private final UiDispatcher uiDispatcher;
    private final long requestId;

    public SetDinnerPresenter(
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
            SetDinnerOutputData outputData) {

        Objects.requireNonNull(outputData, "outputData");

        String message = outputData.replaced()
                ? "Dinner replaced."
                : "Dinner assigned.";

        uiDispatcher.dispatch(() -> viewModel.acceptPlan(
                requestId,
                outputData.plan(),
                message));
    }

    @Override
    public void prepareFailView(MealPlanFailure failure) {
        Objects.requireNonNull(failure, "failure");

        uiDispatcher.dispatch(() -> viewModel.acceptFailure(
                requestId,
                failure));
    }
}