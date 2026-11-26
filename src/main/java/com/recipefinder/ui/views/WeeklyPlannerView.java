package com.recipefinder.ui.views;

import com.recipefinder.interface_adapters.controllers.DinnerAssignmentRequest;
import com.recipefinder.interface_adapters.viewmodels.MealPlanState;
import com.recipefinder.interface_adapters.viewmodels.MealPlanViewModel;
import com.recipefinder.ui.components.DateSelectorPanel;
import com.recipefinder.ui.components.MealSlotPanel;
import com.recipefinder.usecases.mealplan.common.MealPlanFailure.Code;
import com.recipefinder.usecases.mealplan.common.PlannedDinnerData;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import java.io.Serial;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

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
    private final Consumer<LocalDate> dinnerRemovalRequested;

    private final Consumer<DinnerAssignmentRequest>
            dinnerAssignmentRequested;

    private final List<MealSlotPanel> slots = new ArrayList<>();

    private final JLabel selectedDateLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    private final JLabel favoritesStatusLabel =
            new JLabel("Saved recipes have not been loaded.");

    private final JComboBox<SavedRecipeOption> recipePicker =
            new JComboBox<>();

    private final JSpinner servingsSpinner =
            new JSpinner(new SpinnerNumberModel(2, 1, 12, 1));

    private final JButton assignButton =
            new JButton("Assign / replace");

    private final JButton clearButton =
            new JButton("Clear dinner");

    private LocalDate selectedDate;
    private LocalDate lastAssignmentDate;
    private String recipeOptionsUserId;

    public WeeklyPlannerView(
            MealPlanViewModel viewModel,
            LocalDate initialWeek,
            Consumer<LocalDate> weekRequested,
            Consumer<LocalDate> dinnerRemovalRequested,
            Consumer<DinnerAssignmentRequest> dinnerAssignmentRequested) {

        this.viewModel = Objects.requireNonNull(
                viewModel, "viewModel");

        this.dinnerRemovalRequested = Objects.requireNonNull(
                dinnerRemovalRequested, "dinnerRemovalRequested");

        this.dinnerAssignmentRequested = Objects.requireNonNull(
                dinnerAssignmentRequested, "dinnerAssignmentRequested");

        Objects.requireNonNull(weekRequested, "weekRequested");

        setLayout(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(246, 248, 250));
        setPreferredSize(new Dimension(1140, 490));

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

        recipePicker.addActionListener(event -> render());

        assignButton.addActionListener(
                event -> requestAssignment());

        clearButton.addActionListener(event -> {
            if (viewModel.getState().canEdit()
                    && selectedDate != null) {
                this.dinnerRemovalRequested.accept(selectedDate);
            }
        });

        viewModel.addPropertyChangeListener(this);
        render();
    }

    /** Supplies a successfully loaded favorites list for one account. */
    public void setSavedRecipes(
            String userId,
            List<SavedRecipeOption> recipes) {

        Objects.requireNonNull(userId, "userId");
        List<SavedRecipeOption> options = List.copyOf(recipes);

        onUiThread(() -> {
            if (!Objects.equals(
                    userId, viewModel.getState().userId())) {
                return;
            }

            recipeOptionsUserId = userId;

            DefaultComboBoxModel<SavedRecipeOption> choices =
                    new DefaultComboBoxModel<>();

            for (SavedRecipeOption option : options) {
                choices.addElement(option);
            }

            recipePicker.setModel(choices);

            favoritesStatusLabel.setText(
                    options.isEmpty()
                            ? "No saved recipes. Save a recipe first."
                            : options.size() + " saved recipes available.");

            favoritesStatusLabel.setForeground(
                    new Color(80, 90, 100));

            render();
        });
    }

    /** Reports a favorites failure separately from an empty list. */
    public void showSavedRecipesError(String userId, String message) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(message, "message");

        onUiThread(() -> {
            if (!Objects.equals(
                    userId, viewModel.getState().userId())) {
                return;
            }

            recipeOptionsUserId = userId;
            recipePicker.setModel(new DefaultComboBoxModel<>());

            favoritesStatusLabel.setText(message);
            favoritesStatusLabel.setForeground(
                    new Color(170, 40, 40));

            render();
        });
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

        JPanel editorHeading =
                new JPanel(new GridLayout(2, 1, 0, 4));

        editorHeading.setOpaque(false);
        editorHeading.add(selectedDateLabel);
        editorHeading.add(favoritesStatusLabel);

        JPanel editor = new JPanel(new BorderLayout(0, 10));
        editor.setOpaque(false);
        editor.add(editorHeading, BorderLayout.NORTH);
        editor.add(controls, BorderLayout.CENTER);
        editor.add(statusLabel, BorderLayout.SOUTH);

        return editor;
    }

    private void requestAssignment() {
        MealPlanState snapshot = viewModel.getState();

        SavedRecipeOption recipe =
                (SavedRecipeOption) recipePicker.getSelectedItem();

        if (!snapshot.canEdit()
                || recipe == null
                || !Objects.equals(
                recipeOptionsUserId, snapshot.userId())) {
            return;
        }

        // Capture the selection before opening a modal dialog.
        LocalDate date = selectedDate;
        int servings =
                ((Number) servingsSpinner.getValue()).intValue();

        boolean occupied = snapshot.plan().dinners().stream()
                .anyMatch(dinner -> dinner.date().equals(date));

        boolean confirmationRequestedByUseCase =
                snapshot.failure() != null
                        && snapshot.failure().code()
                        == Code.REPLACEMENT_REQUIRED
                        && Objects.equals(lastAssignmentDate, date);

        boolean replacementConfirmed = false;

        if (occupied || confirmationRequestedByUseCase) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    "Replace the dinner planned for "
                            + date.format(SELECTED_DATE_FORMAT)
                            + "?",
                    "Replace dinner",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }

            replacementConfirmed = true;
        }

        MealPlanState current = viewModel.getState();

        // The account or selected week may have changed while a dialog
        // was open. Keep the original revision for conflict checking.
        if (!current.canEdit()
                || !Objects.equals(
                current.userId(), snapshot.userId())
                || !Objects.equals(
                current.weekStart(), snapshot.weekStart())) {
            return;
        }

        lastAssignmentDate = date;

        dinnerAssignmentRequested.accept(
                new DinnerAssignmentRequest(
                        snapshot.userId(),
                        snapshot.plan().weekStart(),
                        date,
                        recipe.provider(),
                        recipe.id(),
                        servings,
                        snapshot.plan().revision(),
                        replacementConfirmed));
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (!MealPlanViewModel.STATE_PROPERTY.equals(
                event.getPropertyName())) {
            return;
        }

        onUiThread(() -> {
            String currentUserId = viewModel.getState().userId();

            if (recipeOptionsUserId != null
                    && !Objects.equals(
                    recipeOptionsUserId, currentUserId)) {

                recipeOptionsUserId = null;
                recipePicker.setModel(new DefaultComboBoxModel<>());

                favoritesStatusLabel.setText(
                        "Saved recipes have not been loaded.");

                favoritesStatusLabel.setForeground(
                        new Color(80, 90, 100));
            }

            render();
        });
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

        boolean hasRecipeOptions =
                Objects.equals(
                        recipeOptionsUserId, state.userId())
                        && recipePicker.getItemCount() > 0;

        boolean selectedDayHasDinner = dinners.stream()
                .anyMatch(dinner ->
                        dinner.date().equals(selectedDate));

        weekSelector.setBusy(state.busy());

        recipePicker.setEnabled(
                state.canEdit() && hasRecipeOptions);

        assignButton.setEnabled(
                state.canEdit()
                        && hasRecipeOptions
                        && recipePicker.getSelectedItem() != null);

        clearButton.setEnabled(
                state.canEdit() && selectedDayHasDinner);

        servingsSpinner.setEnabled(state.canEdit());

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

    private static void onUiThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    @Override
    public void close() {
        viewModel.removePropertyChangeListener(this);
    }

    /** Recipe identity and title displayed in the dropdown. */
    public record SavedRecipeOption(
            String provider,
            String id,
            String title) {

        public SavedRecipeOption {
            provider = Objects.requireNonNull(
                    provider, "provider").trim();

            id = Objects.requireNonNull(id, "id").trim();

            title = Objects.requireNonNull(
                    title, "title").trim();

            if (provider.isEmpty()
                    || id.isEmpty()
                    || title.isEmpty()) {
                throw new IllegalArgumentException(
                        "Recipe provider, ID, and title are required.");
            }
        }

        @Override
        public String toString() {
            return title;
        }
    }
}