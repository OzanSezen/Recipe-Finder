package com.recipefinder.interface_adapters;

/** Schedules an update on the application's UI thread. */
@FunctionalInterface
public interface UiDispatcher {

    void dispatch(Runnable action);
}