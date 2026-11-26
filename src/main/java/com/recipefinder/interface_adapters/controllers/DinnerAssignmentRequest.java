package com.recipefinder.interface_adapters.controllers;

import java.time.LocalDate;
import java.util.Objects;

/** Captures a weekly assignment action from the UI. */
public record DinnerAssignmentRequest(
        String userId,
        LocalDate weekStart,
        LocalDate date,
        String recipeProvider,
        String recipeId,
        int numServings,
        long expectedRevision,
        boolean replacementConfirmed) {

    public DinnerAssignmentRequest {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(weekStart, "weekStart");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(recipeProvider, "recipeProvider");
        Objects.requireNonNull(recipeId, "recipeId");
    }
}