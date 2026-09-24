package com.infiniteconquest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Swaps the shell's screens with a soft crossfade. The outgoing screen is snapshotted,
 * the incoming screen is installed instantly underneath, and the snapshot fades out
 * above it — one clean motion, no layout thrash mid-transition.
 */
final class ScreenManager {
    private final JFrame frame;
    private final GameSettings settings;
    private final FadeOverlay overlay;
    private JComponent current;

    ScreenManager(JFrame frame, GameSettings settings) {
        this.frame = frame;
        this.settings = settings;
        this.overlay = new FadeOverlay();
        overlay.setVisible(false);
        frame.setGlassPane(overlay);
    }

    void show(JComponent next) {
        show(next, true);
    }

    void show(JComponent next, boolean animate) {
        JComponent old = current;
        if (old == next) return;
        BufferedImage snapshot = (old != null && animate && settings.transitionMillis() > 0)
                ? snapshot(old) : null;
        if (old instanceof ShellScreen screen) screen.onHide();
        current = next;
        frame.setContentPane(next);
        frame.revalidate();
        if (next instanceof ShellScreen screen) screen.onShow();
        if (snapshot != null) overlay.fadeFrom(snapshot, settings.transitionMillis());
        frame.repaint();
    }

    private static BufferedImage snapshot(JComponent component) {
        int width = Math.max(1, component.getWidth());
        int height = Math.max(1, component.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Glass-pane overlay that paints a fading snapshot of the outgoing screen. */
    private static final class FadeOverlay extends JComponent {
        private BufferedImage image;
        private float alpha;

        void fadeFrom(BufferedImage image, long durationMillis) {
            this.image = image;
            this.alpha = 1f;
            setVisible(true);
            Timer timer = new Timer(16, null);
            long start = System.currentTimeMillis();
            timer.addActionListener(event -> {
                float progress = Math.min(1f, (System.currentTimeMillis() - start) / (float) durationMillis);
                // Ease out: fast start, gentle landing.
                float remaining = 1f - progress;
                alpha = remaining * remaining;
                repaint();
                if (progress >= 1f) {
                    timer.stop();
                    setVisible(false);
                    this.image = null;
                }
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (image == null || alpha <= 0f) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, Math.max(0f, alpha))));
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            g.dispose();
        }
    }
}
