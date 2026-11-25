package com.recipefinder.interface_adapters.controllers;

import com.recipefinder.usecases.mealplan.setdinner.SetDinnerInputBoundary;
import com.recipefinder.usecases.mealplan.setdinner.SetDinnerInputData;

import java.time.LocalDate;
import java.util.Objects;

/** Converts an assign/replace action into a use-case request. */
public final class SetDinnerController {

    private final SetDinnerInputBoundary interactor;

    public SetDinnerController(
            SetDinnerInputBoundary interactor) {

        this.interactor = Objects.requireNonNull(
                interactor, "interactor");
    }

    public void execute(
            LocalDate weekStart,
            LocalDate date,
            String recipeProvider,
            String recipeId,
            int numServings,
            long expectedRevision,
            boolean replacementConfirmed) {

        SetDinnerInputData inputData = new SetDinnerInputData(
                weekStart,
                date,
                recipeProvider,
                recipeId,
                numServings,
                expectedRevision,
                replacementConfirmed);

        interactor.execute(inputData);
    }
}