package com.recipefinder.ui.components;

import com.recipefinder.entities.RecipeKey;
import com.recipefinder.frameworks_drivers.images.RecipeDisplayLoader;
import com.recipefinder.usecases.mealplan.common.PlannedDinnerData;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.Serial;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Displays a date, its planned dinner, and its recipe thumbnail. */
public final class MealSlotPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Color ACCENT = new Color(32, 116, 104);
    private static final Color NORMAL_BORDER = new Color(220, 225, 230);

    private static final Color SELECTED_BACKGROUND =
            new Color(234, 246, 242);

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final JLabel dayLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();
    private final JLabel imageLabel = new JLabel(" ", JLabel.CENTER);
    private final JTextArea dinnerText = new JTextArea();
    private final JLabel servingsLabel = new JLabel(" ");
    private final JButton selectButton = new JButton("Select day");

    private RecipeDisplayLoader recipeDisplayLoader;
    private LocalDate date;

    // Accessed only on Swing's event thread.
    private long renderVersion;

    public MealSlotPanel(Consumer<LocalDate> selectionListener) {
        Objects.requireNonNull(
                selectionListener, "selectionListener");

        setLayout(new BorderLayout(0, 16));
        setPreferredSize(new Dimension(130, 300));

        dayLabel.setFont(
                dayLabel.getFont().deriveFont(Font.BOLD, 15f));

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 4));
        heading.setOpaque(false);
        heading.add(dayLabel);
        heading.add(dateLabel);

        imageLabel.setPreferredSize(new Dimension(128, 84));

        dinnerText.setEditable(false);
        dinnerText.setFocusable(false);
        dinnerText.setOpaque(false);
        dinnerText.setLineWrap(true);
        dinnerText.setWrapStyleWord(true);
        dinnerText.setRows(4);
        dinnerText.setFont(UIManager.getFont("Label.font"));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(imageLabel, BorderLayout.NORTH);
        content.add(dinnerText, BorderLayout.CENTER);
        content.add(servingsLabel, BorderLayout.SOUTH);

        add(heading, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(selectButton, BorderLayout.SOUTH);

        selectButton.addActionListener(event -> {
            if (date != null) {
                selectionListener.accept(date);
            }
        });
    }

    public void setRecipeDisplayLoader(
            RecipeDisplayLoader recipeDisplayLoader) {

        this.recipeDisplayLoader = Objects.requireNonNull(
                recipeDisplayLoader, "recipeDisplayLoader");
    }

    public void showSlot(
            LocalDate date,
            PlannedDinnerData dinner,
            boolean loaded,
            boolean selected,
            boolean busy) {

        this.date = Objects.requireNonNull(date, "date");

        long currentRender = ++renderVersion;

        dayLabel.setText(
                date.format(DAY_FORMAT).toUpperCase(Locale.ROOT));

        dateLabel.setText(date.format(DATE_FORMAT));

        imageLabel.setIcon(null);
        imageLabel.setText(" ");
        dinnerText.setToolTipText(null);

        if (!loaded) {
            dinnerText.setText(
                    busy ? "Loading..." : "No plan loaded");

            servingsLabel.setText(" ");
        } else if (dinner == null) {
            dinnerText.setText("No dinner planned");
            servingsLabel.setText(" ");
        } else {
            showDinner(dinner, currentRender);
        }

        setBackground(
                selected ? SELECTED_BACKGROUND : Color.WHITE);

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        selected ? ACCENT : NORMAL_BORDER, 2),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));

        selectButton.setText(
                selected ? "Selected" : "Select day");

        selectButton.setEnabled(loaded && !busy);
    }

    private void showDinner(
            PlannedDinnerData dinner,
            long currentRender) {

        RecipeKey key = new RecipeKey(
                dinner.recipeProvider(),
                dinner.recipeId());

        dinnerText.setText(key.toString());

        servingsLabel.setText(
                dinner.numServings() + " servings");

        if (recipeDisplayLoader == null) {
            imageLabel.setText("No image");
            return;
        }

        imageLabel.setText("Loading photo...");

        recipeDisplayLoader.load(key, display -> {
            // Ignore results for a previous week or dinner assignment.
            if (currentRender != renderVersion) {
                return;
            }

            dinnerText.setText(display.title());
            dinnerText.setToolTipText(display.title());

            imageLabel.setIcon(display.thumbnail());

            imageLabel.setText(
                    display.thumbnail() == null
                            ? "Image unavailable"
                            : "");
        });
    }
}