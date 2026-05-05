package com.matthewOSRS;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * RandomTimerPanel.java
 *
 * Sidebar panel with:
 *   - Status + elapsed time display
 *   - Min / Max time inputs (overrides config while the panel is open)
 *   - Start and Stop buttons in plain system font
 *
 * PluginPanel extends JPanel — RuneLite mounts it in the left sidebar.
 */
public class RandomTimerPanel extends PluginPanel
{
    private final RandomTimerPlugin plugin;

    // --- Status display ---
    private final JLabel statusLabel;
    private final JLabel elapsedLabel;

    // --- Time inputs ---
    private final JSpinner minSpinner; // Spinner = a number field with up/down arrows
    private final JSpinner maxSpinner;

    // --- Buttons ---
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton testSoundButton; // Lets the user preview whatever sound is configured

    // Refreshes elapsed time label every second on the Swing (UI) thread
    private final Timer refreshTimer;

    public RandomTimerPanel(RandomTimerPlugin plugin)
    {
        this.plugin = plugin;

        // BorderLayout: NORTH = title, CENTER = inputs + status, SOUTH = buttons
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // ----------------------------------------------------------------
        // NORTH — Title
        // ----------------------------------------------------------------
        JLabel titleLabel = new JLabel("Random Timer", SwingConstants.CENTER);
        // deriveFont keeps the system default font family, just sets bold + size.
        // Avoids using "Runescape" font by name which can render oddly on some systems.
        titleLabel.setFont(getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(Color.ORANGE);
        titleLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        add(titleLabel, BorderLayout.NORTH);

        // ----------------------------------------------------------------
        // CENTER — Status info + time configuration inputs
        // ----------------------------------------------------------------
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // --- Status row ---
        JPanel statusRow = buildLabelRow("Status:");
        statusLabel = new JLabel("INACTIVE");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(getFont().deriveFont(Font.BOLD, 12f));
        statusRow.add(statusLabel);
        centerPanel.add(statusRow);
        centerPanel.add(Box.createVerticalStrut(4));

        // --- Elapsed time row ---
        JPanel elapsedRow = buildLabelRow("Running:");
        elapsedLabel = new JLabel("\u2014"); // Em dash character
        elapsedLabel.setForeground(Color.LIGHT_GRAY);
        elapsedRow.add(elapsedLabel);
        centerPanel.add(elapsedRow);
        centerPanel.add(Box.createVerticalStrut(4));

        // Reminder that the target is secret
        JLabel noteLabel = new JLabel("<html><i>Target time is hidden.</i></html>");
        noteLabel.setForeground(Color.GRAY);
        noteLabel.setFont(getFont().deriveFont(Font.ITALIC, 10f));
        centerPanel.add(noteLabel);
        centerPanel.add(Box.createVerticalStrut(10));

        // --- Time configuration section ---
        // TitledBorder draws a labelled box around the input fields — like a fieldset in HTML
        JPanel timePanel = new JPanel(new GridLayout(2, 2, 6, 6));
        timePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Timer Range",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                getFont().deriveFont(Font.BOLD, 11f),
                Color.LIGHT_GRAY
        ));

        JLabel minLabel = new JLabel("Min (sec):");
        minLabel.setForeground(Color.WHITE);

        // SpinnerNumberModel(initialValue, min, max, stepSize)
        // Seeds the spinner with whatever is currently saved in the RuneLite config
        minSpinner = new JSpinner(new SpinnerNumberModel(
                120,   // default: 2 minutes
                1,     // can't go below 1 second
                86400, // can't exceed 24 hours
                1      // arrows increment/decrement by 1
        ));
        styleSpinner(minSpinner);

        JLabel maxLabel = new JLabel("Max (sec):");
        maxLabel.setForeground(Color.WHITE);

        maxSpinner = new JSpinner(new SpinnerNumberModel(
                600,   // default: 10 minutes
                1,
                86400,
                1
        ));
        styleSpinner(maxSpinner);

        timePanel.add(minLabel);
        timePanel.add(minSpinner);
        timePanel.add(maxLabel);
        timePanel.add(maxSpinner);

        centerPanel.add(timePanel);
        centerPanel.add(Box.createVerticalStrut(6));

        // --- Test Sound button ---
        // Sits inside the center panel so it's grouped with settings, not actions
        testSoundButton = new JButton("Test Sound");
        testSoundButton.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        testSoundButton.setForeground(Color.WHITE);
        testSoundButton.setBackground(new Color(60, 80, 120)); // Muted blue — distinct from Start/Stop
        testSoundButton.setFocusPainted(false);
        testSoundButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        testSoundButton.setToolTipText("Preview the alarm sound that will play when the timer fires");
        testSoundButton.addActionListener(e -> onTestSoundClicked());

        // Make the button fill the full width of the center panel
        testSoundButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, testSoundButton.getPreferredSize().height));
        centerPanel.add(testSoundButton);

        add(centerPanel, BorderLayout.CENTER);

        // ----------------------------------------------------------------
        // SOUTH — Start / Stop buttons
        // ----------------------------------------------------------------
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 6));

        // deriveFont(Font.PLAIN) explicitly sets plain weight on the system font.
        // This prevents any inherited bold/custom font from making buttons look odd.
        Font buttonFont = getFont().deriveFont(Font.PLAIN, 12f);

        startButton = new JButton("Start Timer");
        startButton.setFont(buttonFont);
        startButton.setForeground(Color.WHITE);
        startButton.setBackground(new Color(0, 120, 0));
        startButton.setFocusPainted(false);
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> onStartClicked());

        stopButton = new JButton("Stop Timer");
        stopButton.setFont(buttonFont);
        stopButton.setForeground(Color.WHITE);
        stopButton.setBackground(new Color(140, 0, 0));
        stopButton.setFocusPainted(false);
        stopButton.setEnabled(false); // Disabled until timer is running
        stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopButton.addActionListener(e -> plugin.stopTimer());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // ----------------------------------------------------------------
        // Refresh timer — ticks every 1 second to update elapsed display
        // ----------------------------------------------------------------
        refreshTimer = new Timer(1000, e -> updateElapsed());
        refreshTimer.start();
    }

    // ----------------------------------------------------------------
    // Event handlers
    // ----------------------------------------------------------------

    /**
     * Called when the Test Sound button is clicked.
     * Plays whichever sound would fire when the alarm triggers —
     * either the user's custom .wav file or the generated beep fallback.
     * Temporarily disables the button while playing to prevent double-triggers.
     */
    private void onTestSoundClicked()
    {
        // Disable button briefly so the user doesn't spam-click it
        testSoundButton.setEnabled(false);
        testSoundButton.setText("Playing...");

        // Play on a background thread — audio can block for a moment while loading
        new Thread(() ->
        {
            plugin.testSound();

            // Re-enable button on the Swing thread after a short delay
            // 2000ms gives the beep time to finish before the button comes back
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            SwingUtilities.invokeLater(() ->
            {
                testSoundButton.setEnabled(true);
                testSoundButton.setText("Test Sound");
            });
        }).start();
    }

    /**
     * Called when the Start button is clicked.
     * Validates that min < max, then passes values to the plugin.
     */
    private void onStartClicked()
    {
        int min = (int) minSpinner.getValue();
        int max = (int) maxSpinner.getValue();

        // Validate before starting — show a friendly warning if invalid
        if (min >= max)
        {
            JOptionPane.showMessageDialog(
                    this,
                    "Minimum time must be less than Maximum time.",
                    "Invalid Range",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Hand the panel's values to the plugin — it picks the random time from here
        plugin.startTimer(min, max);
    }

    // ----------------------------------------------------------------
    // Public API — called by RandomTimerPlugin
    // ----------------------------------------------------------------

    /**
     * Flips the panel into active or inactive state.
     * Always dispatched on the Swing thread — safe to call from game thread.
     *
     * @param active true = timer just started, false = timer stopped or fired
     */
    public void setStatus(boolean active)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (active)
            {
                statusLabel.setText("ACTIVE");
                statusLabel.setForeground(Color.GREEN);
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                // Lock spinners while running — no changing values mid-countdown
                minSpinner.setEnabled(false);
                maxSpinner.setEnabled(false);
            }
            else
            {
                statusLabel.setText("INACTIVE");
                statusLabel.setForeground(Color.RED);
                elapsedLabel.setText("\u2014");
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                minSpinner.setEnabled(true);
                maxSpinner.setEnabled(true);
            }
        });
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Builds a left-aligned row with a text label on the left.
     * Value components are added to the returned panel by the caller.
     */
    private JPanel buildLabelRow(String labelText)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel label = new JLabel(labelText + "  ");
        label.setForeground(Color.WHITE);
        row.add(label);
        return row;
    }

    /**
     * Styles a JSpinner to match RuneLite's dark theme.
     * The spinner has two parts: the outer widget and the inner text field editor —
     * both need to be styled separately.
     */
    private void styleSpinner(JSpinner spinner)
    {
        spinner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        spinner.setForeground(Color.WHITE);

        // Cast to DefaultEditor to access the inner JTextField
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        editor.getTextField().setForeground(Color.WHITE);
        editor.getTextField().setCaretColor(Color.WHITE);
        editor.getTextField().setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    }

    /**
     * Called every second by the Swing Timer to update the elapsed time display.
     */
    private void updateElapsed()
    {
        if (!plugin.isTimerActive())
        {
            return;
        }

        long millis  = plugin.getElapsedMillis();
        long hours   = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        elapsedLabel.setText(String.format("%d:%02d:%02d", hours, minutes, seconds));
    }
}