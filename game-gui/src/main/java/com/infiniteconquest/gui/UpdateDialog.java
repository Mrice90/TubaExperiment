package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal software-update dialog: shows the new version and changelog, downloads
 * the update with progress, stages it, and restarts into the new build.
 */
final class UpdateDialog extends JDialog {
    private final GameSettings settings;
    private final UpdateChecker checker = new UpdateChecker();
    private final UpdateApplier applier = new UpdateApplier();

    private final JLabel status;
    private final JTextArea changelog;
    private final JProgressBar progress;
    private final JButton actionButton;
    private final JButton closeButton;
    private final JCheckBox skipVersion;

    private UpdateChecker.UpdateInfo pending;
    private PathStaged staged;

    private record PathStaged(java.nio.file.Path root, boolean needsInstaller, java.nio.file.Path installer) { }

    UpdateDialog(Frame owner, GameSettings settings) {
        super(owner, "Software Update", true);
        this.settings = settings;

        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(10, 15, 28));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(14, 20, 34));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(240, 191, 73, 90), 1),
                new EmptyBorder(18, 22, 18, 22)));

        JLabel title = new JLabel("Software Update");
        title.setFont(new Font(Font.SERIF, Font.BOLD, 22));
        title.setForeground(new Color(240, 191, 73));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(10));

        status = new JLabel("Checking for updates…");
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        status.setForeground(new Color(232, 236, 244));
        status.setAlignmentX(CENTER_ALIGNMENT);
        card.add(status);
        card.add(Box.createVerticalStrut(10));

        changelog = new JTextArea(8, 44);
        changelog.setEditable(false);
        changelog.setLineWrap(true);
        changelog.setWrapStyleWord(true);
        changelog.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        changelog.setBackground(new Color(25, 35, 52));
        changelog.setForeground(new Color(210, 216, 226));
        JScrollPane scroll = new JScrollPane(changelog);
        scroll.setVisible(false);
        scroll.setAlignmentX(CENTER_ALIGNMENT);
        card.add(scroll);
        card.add(Box.createVerticalStrut(6));

        skipVersion = new JCheckBox("Skip this version");
        skipVersion.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        skipVersion.setForeground(new Color(170, 178, 190));
        skipVersion.setOpaque(false);
        skipVersion.setVisible(false);
        skipVersion.setAlignmentX(CENTER_ALIGNMENT);
        card.add(skipVersion);
        card.add(Box.createVerticalStrut(8));

        progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        progress.setVisible(false);
        progress.setAlignmentX(CENTER_ALIGNMENT);
        progress.setMaximumSize(new Dimension(420, 26));
        card.add(progress);
        card.add(Box.createVerticalStrut(10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(CENTER_ALIGNMENT);
        actionButton = new JButton("Download & Install");
        styleButton(actionButton);
        actionButton.setEnabled(false);
        actionButton.addActionListener(e -> startDownload());
        closeButton = new JButton("Close");
        styleButton(closeButton);
        closeButton.addActionListener(e -> dispose());
        buttons.add(actionButton);
        buttons.add(closeButton);
        card.add(buttons);

        add(card, BorderLayout.CENTER);
        pack();
        setMinimumSize(new Dimension(520, 300));
        setLocationRelativeTo(owner);
    }

    private static void styleButton(JButton b) {
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        b.setBackground(new Color(240, 191, 73));
        b.setForeground(new Color(10, 15, 28));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
    }

    /** Runs the check on a worker thread and updates the dialog on the EDT. */
    void checkNow() {
        setStatus("Checking for updates…");
        new SwingWorker<UpdateChecker.UpdateInfo, Void>() {
            @Override
            protected UpdateChecker.UpdateInfo doInBackground() throws Exception {
                return checker.checkForUpdates();
            }

            @Override
            protected void done() {
                try {
                    pending = get();
                    settings.lastUpdateCheck = System.currentTimeMillis();
                    settings.save();
                    if (pending == null) {
                        setStatus("You're up to date — " + GameVersion.displayName() + ".");
                    } else {
                        showUpdate(pending);
                    }
                } catch (Exception e) {
                    setStatus("Couldn't reach the update server. Check your connection and try again.");
                }
            }
        }.execute();
    }

    private void showUpdate(UpdateChecker.UpdateInfo info) {
        setStatus("Update available: " + GameVersion.displayName() + "  →  Alpha " + info.version());
        changelog.setText(info.changelog().isEmpty()
                ? "No release notes published for this version."
                : info.changelog());
        changelog.setCaretPosition(0);
        ((JScrollPane) changelog.getParent().getParent()).setVisible(true);
        skipVersion.setVisible(true);
        skipVersion.setSelected(settings.skippedVersion.equals(info.version()));
        actionButton.setEnabled(true);
        pack();
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void startDownload() {
        if (pending == null) return;
        actionButton.setEnabled(false);
        closeButton.setEnabled(false);
        progress.setVisible(true);
        pack();
        setStatus("Downloading…");
        new SwingWorker<PathStaged, Integer>() {
            @Override
            protected PathStaged doInBackground() throws Exception {
                java.nio.file.Path root = UpdateChecker.installRoot();
                if (UpdateChecker.isInstalledBuild(root)) {
                    java.nio.file.Path setup = applier.downloadInstaller(pending,
                            pct -> publish((int) pct));
                    return new PathStaged(root, true, setup);
                }
                applier.stageJarsUpdate(root, pending, pct -> publish((int) pct));
                return new PathStaged(root, false, null);
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int pct = chunks.get(chunks.size() - 1);
                if (pct >= 0) {
                    progress.setValue(pct);
                    progress.setString(pct + "%");
                } else {
                    progress.setIndeterminate(true);
                }
            }

            @Override
            protected void done() {
                try {
                    staged = get();
                    progress.setValue(100);
                    if (staged.needsInstaller) {
                        setStatus("Installer downloaded. The game will quit so it can upgrade.");
                        actionButton.setText("Run Installer & Quit");
                    } else {
                        setStatus("Update ready. Restart to play the new version.");
                        actionButton.setText("Restart Now");
                    }
                    actionButton.setEnabled(true);
                    for (var l : actionButton.getActionListeners()) {
                        actionButton.removeActionListener(l);
                    }
                    actionButton.addActionListener(e -> finishApply());
                } catch (Exception e) {
                    setStatus("Update failed: " + rootMessage(e));
                    actionButton.setEnabled(true);
                    closeButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void finishApply() {
        if (staged == null) return;
        if (skipVersion.isSelected() && pending != null) {
            settings.skippedVersion = pending.version();
            settings.save();
        }
        try {
            if (staged.needsInstaller) {
                applier.handoffToInstaller(staged.installer);
            } else {
                applier.restartToApply(staged.root);
            }
        } catch (Exception e) {
            setStatus("Couldn't restart: " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        return (m == null || m.isBlank()) ? c.getClass().getSimpleName() : m;
    }

    /**
     * Silent startup check: runs off the EDT, pops the dialog only when an
     * update is actually available (and not skipped). Throttled to once a day.
     */
    static void maybeAutoCheck(GameShell shell, GameSettings settings, Frame owner) {
        if (!settings.checkUpdatesOnStartup) return;
        long day = 24L * 60 * 60 * 1000;
        if (System.currentTimeMillis() - settings.lastUpdateCheck < day) return;
        Thread t = new Thread(() -> {
            try {
                UpdateChecker.UpdateInfo info = new UpdateChecker().checkForUpdates();
                settings.lastUpdateCheck = System.currentTimeMillis();
                settings.save();
                if (info != null && !settings.skippedVersion.equals(info.version())) {
                    SwingUtilities.invokeLater(() -> {
                        UpdateDialog dialog = new UpdateDialog(owner, settings);
                        dialog.checkNow();
                        dialog.setVisible(true);
                    });
                }
            } catch (Exception ignored) {
                // Stay silent: no connection is not the player's problem.
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }
}
