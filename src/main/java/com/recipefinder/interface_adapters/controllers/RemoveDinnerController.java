package com.recipefinder.interface_adapters.controllers;

import com.recipefinder.usecases.mealplan.removedinner.RemoveDinnerInputBoundary;
import com.recipefinder.usecases.mealplan.removedinner.RemoveDinnerInputData;

import java.time.LocalDate;
import java.util.Objects;

/** Converts a clear-dinner action into a use-case request. */
public final class RemoveDinnerController {

    private final RemoveDinnerInputBoundary interactor;

    public RemoveDinnerController(RemoveDinnerInputBoundary interactor) {

        this.interactor = Objects.requireNonNull(
                interactor, "interactor");
    }

    public void execute(
            LocalDate weekStart,
            LocalDate date,
            long expectedRevision) {

        RemoveDinnerInputData inputData = new RemoveDinnerInputData(
                weekStart,
                date,
                expectedRevision);

        interactor.execute(inputData);
    }
}