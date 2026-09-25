package com.infiniteconquest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A hand-card button that lifts toward the viewer on hover or keyboard focus:
 * a soft shadow blooms beneath it while the card rises a few pixels and grows
 * slightly. The motion is quick ({@link Fx#HOVER_MS}) and eased; REDUCED
 * animation mode applies the end state instantly with no visible travel.
 */
final class HoverLiftButton extends JButton {
    private static final int LIFT_PX = 6;
    private static final double GROW = 1.02;

    private final GameSettings settings;
    private float hover;
    private float target;
    private Timer timer;

    HoverLiftButton(String text, Icon icon, GameSettings settings) {
        super(text, icon);
        this.settings = settings;
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { setHovered(true); }
            @Override public void mouseExited(MouseEvent event) { setHovered(false); }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { setHovered(true); }
            @Override public void focusLost(FocusEvent event) { setHovered(false); }
        });
    }

    private void setHovered(boolean hovered) {
        target = hovered ? 1f : 0f;
        if (Fx.reduced(settings)) {
            hover = target;
            repaint();
            return;
        }
        if (timer == null) {
            timer = new Timer(16, event -> {
                float step = 16f / Fx.HOVER_MS;
                if (hover < target) hover = Math.min(target, hover + step);
                else if (hover > target) hover = Math.max(target, hover - step);
                else ((Timer) event.getSource()).stop();
                repaint();
            });
            timer.setRepeats(true);
        }
        if (!timer.isRunning() && hover != target) timer.start();
    }

    @Override protected void paintComponent(Graphics graphics) {
        float eased = Fx.easeOutCubic(Math.max(0f, Math.min(1f, hover)));
        if (eased <= 0f) {
            super.paintComponent(graphics);
            return;
        }
        double scale = 1 + (GROW - 1) * eased;
        float lift = LIFT_PX * eased;
        // The card rises and grows about its center, like a card picked up
        // off the table; the top slides slightly out of its slot.
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(getWidth() / 2.0, getHeight() / 2.0);
        g.scale(scale, scale);
        g.translate(-getWidth() / 2.0, -getHeight() / 2.0);
        g.translate(0, -lift);
        super.paintComponent(g);
        g.dispose();
        // Soft contact shadow in the strip the card vacated, drawn after the
        // card so the (opaque) button background cannot cover it.
        float bottomEdge = getHeight() - lift + (float) ((scale - 1) * getHeight() / 2);
        Graphics2D shadow = (Graphics2D) graphics.create();
        shadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int shadowW = getWidth() - 24;
        int shadowTop = Math.round(bottomEdge) - 3;
        int shadowBottom = getHeight() + 1;
        shadow.setPaint(new GradientPaint(0, shadowTop, new Color(0, 0, 0, 0),
                0, shadowBottom, new Color(0, 0, 0, Math.round(95 * eased))));
        shadow.fillRoundRect((getWidth() - shadowW) / 2, shadowTop,
                shadowW, shadowBottom - shadowTop, 8, 8);
        shadow.dispose();
    }
}
