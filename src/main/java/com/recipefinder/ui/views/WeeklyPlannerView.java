package com.recipefinder.ui.views;

import com.recipefinder.interface_adapters.viewmodels.MealPlanState;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.ui.components.DateSelectorPanel;
import com.recipefinder.ui.components.MealSlotPanel;
import com.recipefinder.usecases.mealplan.common.PlannedDinnerData;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.io.Serial;

/** Swing view for the weekly dinner schedule. */
public final class WeeklyPlannerView extends JPanel
        implements PropertyChangeListener, AutoCloseable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter SELECTED_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "EEEE, d MMM", Locale.ENGLISH);

    private final MealPlanViewModel viewModel;
    private final DateSelectorPanel weekSelector;
    private final List<MealSlotPanel> slots = new ArrayList<>();

    private final JLabel selectedDateLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    private final JComboBox<String> recipePicker =
            new JComboBox<>(new String[]{
                    "Favorites integration pending"
            });

    private final JSpinner servingsSpinner =
            new JSpinner(new SpinnerNumberModel(2, 1, 12, 1));

    private final JButton assignButton =
            new JButton("Assign / replace");

    private final JButton clearButton =
            new JButton("Clear dinner");

    private LocalDate selectedDate;

    public WeeklyPlannerView(
            MealPlanViewModel viewModel,
            LocalDate initialWeek,
            Consumer<LocalDate> weekRequested) {

        this.viewModel = Objects.requireNonNull(
                viewModel, "viewModel");

        Objects.requireNonNull(weekRequested, "weekRequested");

        setLayout(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(246, 248, 250));
        setPreferredSize(new Dimension(1140, 470));

        JLabel title = new JLabel("Weekly dinner schedule");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));

        weekSelector = new DateSelectorPanel(initialWeek, monday -> {
            selectedDate = monday;
            weekRequested.accept(monday);
        });

        selectedDate = weekSelector.getWeekStart();

        JPanel heading = new JPanel(new BorderLayout(0, 16));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(weekSelector, BorderLayout.SOUTH);

        JPanel calendar = new JPanel(new GridLayout(1, 7, 10, 0));
        calendar.setOpaque(false);

        for (int index = 0; index < 7; index++) {
            MealSlotPanel slot = new MealSlotPanel(date -> {
                selectedDate = date;
                render();
            });

            slots.add(slot);
            calendar.add(slot);
        }

        add(heading, BorderLayout.NORTH);
        add(calendar, BorderLayout.CENTER);
        add(createEditor(), BorderLayout.SOUTH);

        // These controls will be connected in the next integration step.
        recipePicker.setEnabled(false);
        assignButton.setEnabled(false);
        clearButton.setEnabled(false);

        viewModel.addPropertyChangeListener(this);
        render();
    }

    private JPanel createEditor() {
        recipePicker.setPreferredSize(new Dimension(250, 30));
        servingsSpinner.setPreferredSize(new Dimension(65, 30));

        JPanel controls =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        controls.setOpaque(false);
        controls.add(new JLabel("Saved recipe:"));
        controls.add(recipePicker);
        controls.add(new JLabel("Servings:"));
        controls.add(servingsSpinner);
        controls.add(assignButton);
        controls.add(clearButton);

        JPanel editor = new JPanel(new BorderLayout(0, 10));
        editor.setOpaque(false);
        editor.add(selectedDateLabel, BorderLayout.NORTH);
        editor.add(controls, BorderLayout.CENTER);
        editor.add(statusLabel, BorderLayout.SOUTH);

        return editor;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (!MealPlanViewModel.STATE_PROPERTY.equals(
                event.getPropertyName())) {
            return;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            render();
        } else {
            SwingUtilities.invokeLater(this::render);
        }
    }

    private void render() {
        MealPlanState state = viewModel.getState();

        if (state.weekStart() != null) {
            weekSelector.showWeek(state.weekStart());
        }

        LocalDate monday = weekSelector.getWeekStart();

        if (selectedDate == null
                || selectedDate.isBefore(monday)
                || selectedDate.isAfter(monday.plusDays(6))) {
            selectedDate = monday;
        }

        boolean loaded = state.hasLoadedWeek()
                && Objects.equals(
                state.plan().weekStart(), monday);

        List<PlannedDinnerData> dinners = state.visibleDinners();

        for (int index = 0; index < slots.size(); index++) {
            LocalDate date = monday.plusDays(index);

            PlannedDinnerData dinner = dinners.stream()
                    .filter(item -> item.date().equals(date))
                    .findFirst()
                    .orElse(null);

            slots.get(index).showSlot(
                    date,
                    dinner,
                    loaded,
                    date.equals(selectedDate),
                    state.busy());
        }

        selectedDateLabel.setText(
                "Selected day: "
                        + selectedDate.format(SELECTED_DATE_FORMAT));

        weekSelector.setBusy(state.busy());
        servingsSpinner.setEnabled(loaded && !state.busy());

        if (!state.message().isBlank()) {
            statusLabel.setText(state.message());
        } else if (state.userId() == null) {
            statusLabel.setText(
                    "Sign in to load your weekly plan.");
        } else if (!loaded) {
            statusLabel.setText("No week loaded.");
        } else {
            statusLabel.setText("Select a day to plan dinner.");
        }

        statusLabel.setForeground(
                state.failure() == null
                        ? new Color(80, 90, 100)
                        : new Color(170, 40, 40));
    }

    @Override
    public void close() {
        viewModel.removePropertyChangeListener(this);
    }
}