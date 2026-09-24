package com.infiniteconquest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * First thing the player sees: logo, progress bar, live status line, and rotating
 * gameplay tips while the card catalog, decks, and art cache load on a worker thread.
 */
final class LoadingScreen extends JPanel implements ShellScreen {
    /** One named unit of startup work. Runs off the EDT. */
    record InitTask(String label, ThrowingWork work) { }

    @FunctionalInterface
    interface ThrowingWork {
        void run() throws Exception;
    }

    private static final String[] TIPS = {
            "Tip: Lands and Structures are played on your turn number — plan two turns ahead.",
            "Tip: Zeus rewards aggression; Poseidon rewards patience. Pick your tempo.",
            "Tip: Turrets punish anything that wanders into range. Scout first.",
            "Tip: Your Capital's passive fires all game — build around it.",
            "Tip: Ally decks let Zeus and Poseidon fight side by side.",
            "Tip: Watchtowers extend your reach; Sanctuaries mend your wounded.",
            "Tip: Gold carries between turns. Saving for an apex card wins games.",
    };

    private final List<String> labels = new java.util.ArrayList<>();
    private int completed;
    private String status = "Waking the arena…";
    private String tip = TIPS[0];
    private int tipIndex;
    private Timer tipTimer;
    private BufferedImage backdrop;

    LoadingScreen() {
        setBackground(new Color(10, 15, 28));
        setLayout(new BorderLayout());
    }

    /** Runs the tasks on a worker thread, then calls {@code onDone} on the EDT. */
    void run(List<InitTask> tasks, GameSettings settings, Runnable onDone) {
        for (InitTask task : tasks) labels.add(task.label());
        tipTimer = new Timer(2600, e -> {
            if (!settings.tipsEnabled) {
                if (!tip.isEmpty()) { tip = ""; repaint(); }
                return;
            }
            tipIndex = (tipIndex + 1) % TIPS.length;
            tip = TIPS[tipIndex];
            repaint();
        });
        tipTimer.start();
        if (!settings.tipsEnabled) tip = "";

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (InitTask task : tasks) {
                    publish(task.label());
                    task.work().run();
                    completed++;
                    // Keep the bar readable even when a step is instant.
                    Thread.sleep(120);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                status = chunks.get(chunks.size() - 1);
                repaint();
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException | ExecutionException failure) {
                    status = "Something went wrong while loading: " + rootMessage(failure);
                    repaint();
                    return;
                }
                status = "Ready.";
                repaint();
                // Let the player see "Ready" for a beat before the title fades in.
                Timer beat = new Timer(350, e -> onDone.run());
                beat.setRepeats(false);
                beat.start();
            }
        };
        worker.execute();
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @Override
    public void onShow() {
        if (backdrop == null) backdrop = CardArtFactory.worldBackdrop();
        if (tipTimer != null && !tipTimer.isRunning()) tipTimer.start();
    }

    @Override
    public void onHide() {
        if (tipTimer != null) tipTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        ShellUi.paintBackdrop(g, w, h);
        ShellUi.paintBackdropImage(g, backdrop, w, h, 0.82f);
        ShellUi.paintVignette(g, w, h);

        int cx = w / 2;
        g.setFont(ShellUi.EYEBROW_FONT);
        g.setColor(new Color(240, 191, 73));
        String eyebrow = "A  H E X  &  A L L I E S  S T R A T E G Y  G A M E";
        g.drawString(eyebrow, cx - g.getFontMetrics().stringWidth(eyebrow) / 2, h / 2 - 150);
        ShellUi.drawTitle(g, "INFINITE CONQUEST", ShellUi.TITLE_FONT, cx, h / 2 - 70, 10f);

        // Progress bar.
        int barW = Math.min(460, w - 120);
        int barX = cx - barW / 2, barY = h / 2 - 10;
        g.setColor(new Color(20, 30, 48, 220));
        g.fillRoundRect(barX, barY, barW, 14, 7, 7);
        float progress = labels.isEmpty() ? 0f : Math.min(1f, completed / (float) labels.size());
        if (progress > 0) {
            GradientPaint fill = new GradientPaint(barX, 0, new Color(255, 214, 120),
                    barX + barW, 0, new Color(196, 138, 32));
            g.setPaint(fill);
            g.fillRoundRect(barX, barY, Math.max(14, (int) (barW * progress)), 14, 7, 7);
        }
        g.setColor(new Color(140, 160, 190, 120));
        g.drawRoundRect(barX, barY, barW, 14, 7, 7);

        g.setFont(ShellUi.SUBTITLE_FONT);
        g.setColor(new Color(220, 226, 236));
        g.drawString(status, cx - g.getFontMetrics().stringWidth(status) / 2, barY + 44);

        if (!tip.isEmpty()) {
            g.setFont(ShellUi.SMALL_FONT);
            g.setColor(new Color(150, 164, 186));
            int tipW = g.getFontMetrics().stringWidth(tip);
            g.drawString(tip, cx - tipW / 2, h - 48);
        }
        g.dispose();
    }
}
