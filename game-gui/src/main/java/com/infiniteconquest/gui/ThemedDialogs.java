package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

import static com.infiniteconquest.gui.UiTheme.*;

/**
 * Themed replacements for raw {@link JOptionPane} popups. Every dialog here
 * uses the app's dark-panel / gold-title visual language instead of the
 * platform look, so confirmations and messages feel like part of the game.
 */
final class ThemedDialogs {
    private ThemedDialogs() { }

    /** Blocking yes/no confirmation. Returns true for the yes label. */
    static boolean confirm(Component parent, String title, String htmlBody, String yesLabel, String noLabel) {
        return chooseIndex(parent, title, htmlBody, null,
                new String[] { yesLabel, noLabel }, 0) == 0;
    }

    /** Blocking informational message with a single OK button. */
    static void message(Component parent, String title, String htmlBody, boolean warning) {
        chooseIndex(parent, title, htmlBody, null, new String[] { "OK" }, 0);
    }

    /** Blocking combo-box choice. Returns the selected option, or null when cancelled. */
    static <T> T chooseOption(Component parent, String title, String htmlBody, T[] options, T initial) {
        @SuppressWarnings("unchecked")
        T[] boxed = options;
        JComboBox<T> combo = new JComboBox<>(boxed);
        combo.setSelectedItem(initial);
        combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        combo.setBackground(PANEL_LIGHT);
        combo.setForeground(Color.WHITE);
        int picked = chooseIndex(parent, title, htmlBody, combo,
                new String[] { "Choose", "Cancel" }, 0);
        return picked == 0 ? (T) combo.getSelectedItem() : null;
    }

    /** Blocking confirmation around an arbitrary component. Returns true for the yes label. */
    static boolean confirmComponent(Component parent, String title, JComponent body,
            String yesLabel, String noLabel) {
        return chooseIndex(parent, title, null, body,
                new String[] { yesLabel, noLabel }, 0) == 0;
    }

    /** Blocking dialog around an arbitrary component (stack inspector, etc.). */
    static void showComponent(Component parent, String title, JComponent body) {
        chooseIndex(parent, title, null, body, new String[] { "Close" }, 0);
    }

    /**
     * Blocking option dialog. Returns the index of the chosen button, or -1 when dismissed.
     */
    private static int chooseIndex(Component parent, String title, String htmlBody,
            JComponent extra, String[] buttons, int initial) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(PANEL);
        content.setBorder(new CompoundBorder(new LineBorder(GOLD, 2),
                new EmptyBorder(18, 22, 18, 22)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(GOLD);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(10));

        if (htmlBody != null) {
            JLabel body = new JLabel("<html><div style='width:380px'>" + htmlBody + "</div></html>");
            body.setForeground(new Color(232, 236, 244));
            body.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(body);
        }

        if (extra != null) {
            content.add(Box.createVerticalStrut(10));
            extra.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(extra);
        }
        content.add(Box.createVerticalStrut(16));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int[] result = { -1 };
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            JButton button = themedButton(buttons[i], e -> {
                result[0] = index;
                dialog.dispose();
            });
            row.add(button);
            if (i == initial) dialog.getRootPane().setDefaultButton(button);
        }
        content.add(row);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(440, 120));
        dialog.setLocationRelativeTo(parent);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
        return result[0];
    }

    static JButton themedButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setBackground(new Color(48, 83, 108));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new CompoundBorder(
                new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED),
                new EmptyBorder(7, 16, 7, 16)));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.addActionListener(e -> SoundEffects.play(SoundEffects.Cue.CLICK));
        button.addActionListener(listener);
        return button;
    }

    // --- Toasts ----------------------------------------------------------

    private static JComponent activeToast;
    private static javax.swing.Timer toastTimer;

    /**
     * Transient non-blocking confirmation (e.g. "Deck saved") that slides in
     * at the top of the given window, lingers, and slides away. Safe from any
     * thread. Only one toast shows at a time; a new one replaces the old.
     */
    static void toast(Window window, String htmlMessage) {
        Runnable task = () -> {
            JLayeredPane layers = layeredPaneOf(window);
            if (layers == null) return;
            if (activeToast != null) { layers.remove(activeToast); activeToast = null; }
            if (toastTimer != null) { toastTimer.stop(); toastTimer = null; }
            JLabel label = new JLabel("<html><div style='width:440px;text-align:center'>"
                    + htmlMessage + "</div></html>");
            label.setForeground(Color.WHITE);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            JPanel toast = new JPanel(new BorderLayout());
            toast.setBackground(new Color(20, 32, 48, 235));
            toast.setBorder(new CompoundBorder(new LineBorder(GOLD, 1, true), new EmptyBorder(10, 18, 10, 18)));
            toast.add(label, BorderLayout.CENTER);
            toast.setSize(toast.getPreferredSize());
            int targetY = 14;
            toast.setLocation(Math.max(8, (layers.getWidth() - toast.getWidth()) / 2), -toast.getHeight());
            layers.add(toast, JLayeredPane.POPUP_LAYER);
            layers.revalidate();
            layers.repaint();
            activeToast = toast;
            long[] phaseStart = { System.nanoTime() };
            int[] phase = { 0 }; // 0 = slide in, 1 = linger, 2 = slide out
            toastTimer = new javax.swing.Timer(16, e -> {
                long elapsed = System.nanoTime() - phaseStart[0];
                int y = toast.getY();
                if (phase[0] == 0) {
                    int next = y + Math.max(1, (int) ((targetY - y) * 0.3));
                    if (next >= targetY - 1) {
                        toast.setLocation(toast.getX(), targetY);
                        phase[0] = 1;
                        phaseStart[0] = System.nanoTime();
                    } else toast.setLocation(toast.getX(), next);
                } else if (phase[0] == 1) {
                    if (elapsed > 2_200_000_000L) { phase[0] = 2; phaseStart[0] = System.nanoTime(); }
                } else if (y <= -toast.getHeight() + 4) {
                    ((javax.swing.Timer) e.getSource()).stop();
                    toastTimer = null;
                    layers.remove(toast);
                    if (activeToast == toast) activeToast = null;
                    layers.repaint();
                } else {
                    toast.setLocation(toast.getX(), y - Math.max(2, (int) (Math.abs(y - targetY) * 0.3) + 2));
                }
            });
            toastTimer.start();
        };
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private static JLayeredPane layeredPaneOf(Window window) {
        if (window instanceof JFrame frame) return frame.getLayeredPane();
        if (window instanceof JDialog dialog) return dialog.getLayeredPane();
        return null;
    }
}
