package com.recipefinder.interface_adapters.controllers;

import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanInputBoundary;
import com.recipefinder.usecases.mealplan.loadmealplan.LoadMealPlanInputData;

import java.time.LocalDate;
import java.util.Objects;

/** Converts a load-week action into a use-case request. */
public final class LoadMealPlanController {

    private final LoadMealPlanInputBoundary interactor;

    public LoadMealPlanController(
            LoadMealPlanInputBoundary interactor) {

        this.interactor = Objects.requireNonNull(
                interactor, "interactor");
    }

    public void execute(LocalDate weekStart) {
        interactor.execute(
                new LoadMealPlanInputData(weekStart));
    }
}