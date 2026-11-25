package com.recipefinder.ui.components;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.io.Serial;

/** Displays and changes the selected Monday-to-Sunday week. */
public final class DateSelectorPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    private final JButton previousButton = new JButton("Previous week");
    private final JButton nextButton = new JButton("Next week");
    private final JLabel weekLabel =
            new JLabel("", SwingConstants.CENTER);

    private final Consumer<LocalDate> weekChangeListener;

    private LocalDate weekStart;

    public DateSelectorPanel(
            LocalDate initialDate,
            Consumer<LocalDate> weekChangeListener) {

        this.weekChangeListener = Objects.requireNonNull(
                weekChangeListener, "weekChangeListener");

        setLayout(new BorderLayout(16, 0));
        setOpaque(false);

        add(previousButton, BorderLayout.WEST);
        add(weekLabel, BorderLayout.CENTER);
        add(nextButton, BorderLayout.EAST);

        previousButton.addActionListener(
                event -> changeWeek(weekStart.minusWeeks(1)));

        nextButton.addActionListener(
                event -> changeWeek(weekStart.plusWeeks(1)));

        showWeek(initialDate);
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    /** Updates the heading without triggering another load request. */
    public void showWeek(LocalDate date) {
        Objects.requireNonNull(date, "date");

        weekStart = date.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        weekLabel.setText(
                weekStart.format(DATE_FORMAT)
                        + " - "
                        + weekStart.plusDays(6).format(DATE_FORMAT));
    }

    public void setBusy(boolean busy) {
        previousButton.setEnabled(!busy);
        nextButton.setEnabled(!busy);
    }

    private void changeWeek(LocalDate date) {
        showWeek(date);
        weekChangeListener.accept(weekStart);
    }
}