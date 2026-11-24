package com.recipefinder.interface_adapters.viewmodels;

import com.recipefinder.usecases.mealplan.common.MealPlanData;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure.Code;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds observable weekly scheduling state.
 * Mutating methods must run on the UI thread.
 * Presenters use UiDispatcher to satisfy that requirement.
 */
public final class MealPlanViewModel {

    public static final String STATE_PROPERTY = "state";
    public static final long NO_REQUEST = -1L;

    private final PropertyChangeSupport support =
            new PropertyChangeSupport(this);

    private volatile MealPlanState state = MealPlanState.initial();
    private final AtomicLong generation = new AtomicLong();

    public MealPlanState getState() {
        return state;
    }

    /**
     * Call when an account signs in or out.
     * Passing null clears the screen for logout.
     */
    public void bindUser(String userId) {
        if (userId != null && userId.isBlank()) {
            throw new IllegalArgumentException(
                    "User ID cannot be blank.");
        }

        // Invalidate responses from the previous account/session.
        generation.incrementAndGet();

        update(new MealPlanState(
                userId,
                null,
                null,
                false,
                true,
                "",
                null));
    }

    /** Starts loading the selected Monday-starting week. */
    public long beginLoad(LocalDate weekStart) {
        Objects.requireNonNull(weekStart, "weekStart");

        if (state.busy()) {
            return NO_REQUEST;
        }

        if (state.userId() == null) {
            MealPlanFailure failure = new MealPlanFailure(
                    Code.NOT_SIGNED_IN,
                    "Sign in to view your weekly plan.");

            update(new MealPlanState(
                    null,
                    null,
                    null,
                    false,
                    true,
                    failure.message(),
                    failure));

            return NO_REQUEST;
        }

        long requestId = generation.incrementAndGet();

        update(new MealPlanState(
                state.userId(),
                weekStart,
                state.plan(),
                true,
                true,
                "Loading week...",
                null));

        return requestId;
    }

    /** Starts an assignment, replacement, or removal operation. */
    public long beginChange() {
        if (!state.canEdit()) {
            return NO_REQUEST;
        }

        long requestId = generation.incrementAndGet();

        update(new MealPlanState(
                state.userId(),
                state.weekStart(),
                state.plan(),
                true,
                false,
                "Saving changes...",
                null));

        return requestId;
    }

    public boolean isCurrentRequest(long requestId) {
        return requestId == generation.get() && state.busy();
    }

    /** Applies a complete plan returned after a successful operation. */
    public void acceptPlan(
            long requestId,
            MealPlanData plan,
            String message) {

        Objects.requireNonNull(plan, "plan");

        if (!isCurrentRequest(requestId)) {
            return;
        }

        if (!Objects.equals(state.userId(), plan.userId())
                || !Objects.equals(state.weekStart(), plan.weekStart())) {

            acceptFailure(
                    requestId,
                    new MealPlanFailure(
                            Code.STORAGE_ERROR,
                            "The returned plan does not match the selected account and week."));
            return;
        }

        update(new MealPlanState(
                state.userId(),
                state.weekStart(),
                plan,
                false,
                false,
                message,
                null));
    }

    /** Reports failure while retaining the last successfully loaded plan. */
    public void acceptFailure(
            long requestId,
            MealPlanFailure failure) {

        Objects.requireNonNull(failure, "failure");

        if (!isCurrentRequest(requestId)) {
            return;
        }

        if (failure.code() == Code.NOT_SIGNED_IN) {
            generation.incrementAndGet();

            update(new MealPlanState(
                    null,
                    null,
                    null,
                    false,
                    true,
                    failure.message(),
                    failure));
            return;
        }

        boolean reloadRequired =
                state.reloadRequired()
                        || failure.code() == Code.CONFLICT;

        update(new MealPlanState(
                state.userId(),
                state.weekStart(),
                state.plan(),
                false,
                reloadRequired,
                failure.message(),
                failure));
    }

    /** Clears feedback without changing assignments or their revision. */
    public void clearFeedback() {
        if (state.busy()) {
            return;
        }

        update(new MealPlanState(
                state.userId(),
                state.weekStart(),
                state.plan(),
                false,
                state.reloadRequired(),
                "",
                null));
    }

    public void addPropertyChangeListener(
            PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(
            PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);
    }

    private void update(MealPlanState nextState) {
        MealPlanState previousState = state;
        state = nextState;

        support.firePropertyChange(
                STATE_PROPERTY,
                previousState,
                nextState);
    }
}