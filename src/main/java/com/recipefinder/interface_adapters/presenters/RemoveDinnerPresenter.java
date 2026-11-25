package com.recipefinder.interface_adapters.presenters;

import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.removedinner.RemoveDinnerOutputBoundary;
import com.recipefinder.usecases.mealplan.removedinner.RemoveDinnerOutputData;

import java.util.Objects;

/** Presents the result of one clear-dinner request. */
public final class RemoveDinnerPresenter
        implements RemoveDinnerOutputBoundary {

    private final MealPlanViewModel viewModel;
    private final UiDispatcher uiDispatcher;
    private final long requestId;

    public RemoveDinnerPresenter(
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
            RemoveDinnerOutputData outputData) {

        Objects.requireNonNull(outputData, "outputData");

        String message = outputData.removed()
                ? "Dinner removed."
                : "That day is already empty.";

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