package com.recipefinder.frameworks_drivers.ui;

import com.recipefinder.interface_adapters.UiDispatcher;

import javax.swing.SwingUtilities;
import java.util.Objects;

/** Executes UI updates on Swing's event dispatch thread. */
public final class SwingUiDispatcher implements UiDispatcher {

    @Override
    public void dispatch(Runnable action) {
        Objects.requireNonNull(action, "action");

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}