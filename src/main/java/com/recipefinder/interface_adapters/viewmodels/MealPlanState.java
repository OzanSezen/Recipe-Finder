package com.recipefinder.interface_adapters.viewmodels;

import com.recipefinder.usecases.mealplan.common.MealPlanData;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.common.PlannedDinnerData;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable display state for the weekly scheduling screen. */
public record MealPlanState(
        String userId,
        LocalDate weekStart,
        MealPlanData plan,
        boolean busy,
        boolean reloadRequired,
        String message,
        MealPlanFailure failure) {

    public MealPlanState {
        message = message == null ? "" : message;
    }

    public static MealPlanState initial() {
        return new MealPlanState(
                null,
                null,
                null,
                false,
                true,
                "",
                null);
    }

    /** Whether the loaded plan belongs to the selected user and week. */
    public boolean hasLoadedWeek() {
        return plan != null
                && Objects.equals(userId, plan.userId())
                && Objects.equals(weekStart, plan.weekStart());
    }

    public boolean canEdit() {
        return hasLoadedWeek() && !busy && !reloadRequired;
    }

    /** Prevents displaying an old week's dinners under a new week heading. */
    public List<PlannedDinnerData> visibleDinners() {
        return hasLoadedWeek() ? plan.dinners() : List.of();
    }
}