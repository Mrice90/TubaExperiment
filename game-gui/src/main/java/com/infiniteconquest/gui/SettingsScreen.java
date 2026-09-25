package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Persisted desktop settings, every row wired to something real.
 */
final class SettingsScreen extends SubScreen {
    private final GameShell shell;
    private final GameSettings settings;
    private JComboBox<String> displayMode;
    private JComboBox<String> windowSize;
    private JCheckBox soundEnabled;
    private JSlider volume;
    private JComboBox<String> animationMode;
    private JCheckBox tipsEnabled;
    private JCheckBox checkUpdatesOnStartup;
    private JLabel updateStatus;
    private JTextField lobbyWorkerUrlField;

    SettingsScreen(GameShell shell, GameSettings settings) {
        super(shell, "Settings");
        this.shell = shell;
        this.settings = settings;
    }

    @Override
    protected JComponent buildContent() {
        JPanel card = SubScreen.card();

        displayMode = styledCombo("Windowed", "Fullscreen");
        displayMode.setSelectedItem(settings.fullscreen ? "Fullscreen" : "Windowed");
        windowSize = styledCombo("1280 × 800", "1500 × 980", "1920 × 1080");
        windowSize.setSelectedItem(settings.windowWidth + " × " + settings.windowHeight);
        windowSize.setEnabled(!settings.fullscreen);
        displayMode.addActionListener(e -> windowSize.setEnabled(!isFullscreenSelected()));

        soundEnabled = styledCheck("Sound effects on");
        soundEnabled.setSelected(settings.soundEnabled);
        volume = new JSlider(0, 100, settings.volume);
        styleSlider(volume);
        volume.setEnabled(settings.soundEnabled);
        soundEnabled.addActionListener(e -> volume.setEnabled(soundEnabled.isSelected()));

        animationMode = styledCombo("Full", "Reduced");
        animationMode.setSelectedItem(settings.animationMode == GameSettings.AnimationMode.FULL ? "Full" : "Reduced");
        tipsEnabled = styledCheck("Gameplay tips on the loading screen");
        tipsEnabled.setSelected(settings.tipsEnabled);

        checkUpdatesOnStartup = styledCheck("Check for updates when the game starts");
        checkUpdatesOnStartup.setSelected(settings.checkUpdatesOnStartup);

        JPanel updateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        updateRow.setOpaque(false);
        updateStatus = new JLabel(GameVersion.displayName());
        updateStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        updateStatus.setForeground(new Color(170, 178, 190));
        ShellUi.MenuButton checkNow = new ShellUi.MenuButton("Check for updates");
        checkNow.addActionListener(e -> openUpdateDialog());
        updateRow.add(updateStatus);
        updateRow.add(checkNow);

        card.add(SubScreen.row("Display", displayMode));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Window size", windowSize));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Sound", soundEnabled));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Volume", volume));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Animations", animationMode));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Tips", tipsEnabled));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("Updates", updateRow));
        card.add(Box.createVerticalStrut(8));
        card.add(SubScreen.row("", checkUpdatesOnStartup));
        card.add(Box.createVerticalStrut(8));

        lobbyWorkerUrlField = new JTextField(settings.lobbyWorkerUrl == null ? "" : settings.lobbyWorkerUrl, 24);
        lobbyWorkerUrlField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        lobbyWorkerUrlField.setBackground(new Color(25, 35, 52));
        lobbyWorkerUrlField.setForeground(new Color(232, 236, 244));
        lobbyWorkerUrlField.setCaretColor(new Color(240, 191, 73));
        lobbyWorkerUrlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 85, 110)),
                new EmptyBorder(6, 8, 6, 8)));
        lobbyWorkerUrlField.setMaximumSize(new Dimension(280, 38));
        lobbyWorkerUrlField.setToolTipText("Lobby/rating server URL. Empty uses the built-in Grumpy Goose Studio lobby; direct tunnel links always work.");
        card.add(SubScreen.row("Lobby server", lobbyWorkerUrlField));
        card.add(Box.createVerticalStrut(8));
        JLabel lobbyHint = new JLabel("<html><i>Optional. Powers the lobby browser, quick match, and Elo ratings. Direct tunnel links work without it.</i></html>");
        lobbyHint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        lobbyHint.setForeground(new Color(150, 158, 172));
        card.add(SubScreen.row("", lobbyHint));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        buttons.setOpaque(false);
        ShellUi.MenuButton save = new ShellUi.MenuButton("Save & Apply");
        save.addActionListener(e -> saveAndApply());
        ShellUi.MenuButton defaults = new ShellUi.MenuButton("Defaults");
        defaults.addActionListener(e -> restoreDefaults());
        buttons.add(save);
        buttons.add(defaults);

        JPanel frame = new JPanel(new BorderLayout());
        frame.setOpaque(true);
        frame.setBackground(new Color(14, 20, 34, 225));
        frame.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 191, 73, 90), 1),
                new EmptyBorder(6, 6, 6, 6)));
        // ISSUE 2: the form is taller than small windows (and can be clipped by
        // the centered GridBag wrapper), so the rows scroll while the button
        // bar stays pinned at the bottom, always reachable.
        JScrollPane scroller = new JScrollPane(card,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUnitIncrement(24);
        frame.add(scroller, BorderLayout.CENTER);
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.setOpaque(false);
        buttonBar.setBorder(new EmptyBorder(10, 0, 6, 0));
        buttonBar.add(buttons, BorderLayout.CENTER);
        frame.add(buttonBar, BorderLayout.SOUTH);
        return frame;
    }

    private boolean isFullscreenSelected() {
        return "Fullscreen".equals(displayMode.getSelectedItem());
    }

    private void saveAndApply() {
        settings.fullscreen = isFullscreenSelected();
        String[] size = ((String) windowSize.getSelectedItem()).split("×");
        settings.windowWidth = Integer.parseInt(size[0].trim());
        settings.windowHeight = Integer.parseInt(size[1].trim());
        settings.soundEnabled = soundEnabled.isSelected();
        settings.volume = volume.getValue();
        settings.animationMode = "Full".equals(animationMode.getSelectedItem())
                ? GameSettings.AnimationMode.FULL : GameSettings.AnimationMode.REDUCED;
        settings.tipsEnabled = tipsEnabled.isSelected();
        settings.checkUpdatesOnStartup = checkUpdatesOnStartup.isSelected();
        settings.lobbyWorkerUrl = lobbyWorkerUrlField.getText().trim();
        settings.save();
        shell.applySettings();
        shell.showTitle();
    }

    private void openUpdateDialog() {
        UpdateDialog dialog = new UpdateDialog(shell.window(), settings);
        dialog.checkNow();
        dialog.setVisible(true);
    }

    private void restoreDefaults() {
        settings.resetToDefaults();
        settings.save();
        shell.applySettings();
        shell.showSettings();
    }

    private static JComboBox<String> styledCombo(String... items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        combo.setBackground(new Color(25, 35, 52));
        combo.setForeground(new Color(232, 236, 244));
        combo.setMaximumSize(new Dimension(280, 38));
        return combo;
    }

    private static JCheckBox styledCheck(String text) {
        JCheckBox check = new JCheckBox(text);
        check.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        check.setForeground(new Color(232, 236, 244));
        check.setOpaque(false);
        return check;
    }

    private static void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setForeground(new Color(240, 191, 73));
        slider.setMaximumSize(new Dimension(280, 44));
        slider.setMajorTickSpacing(25);
        slider.setPaintTicks(true);
    }
}
