package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static com.infiniteconquest.gui.UiTheme.*;

/**
 * Shared painting kit for the shell screens: backdrop, letter-spaced titles,
 * and the menu button style. Keeps every menu speaking the same visual language.
 */
final class ShellUi {
    static final Font TITLE_FONT = new Font(Font.SERIF, Font.BOLD, 64);
    static final Font SUBTITLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    static final Font EYEBROW_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    static final Font BUTTON_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 19);
    static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    private ShellUi() { }

    /** Dark stormy gradient every shell screen starts from. */
    static void paintBackdrop(Graphics2D g, int width, int height) {
        GradientPaint sky = new GradientPaint(0, 0, new Color(10, 15, 28),
                0, height, new Color(22, 30, 50));
        g.setPaint(sky);
        g.fillRect(0, 0, width, height);
    }

    /** Draws {@code image} cover-fit and dimmed, like a backdrop painting. */
    static void paintBackdropImage(Graphics2D g, BufferedImage image, int width, int height, float dim) {
        if (image == null) return;
        double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
        int dw = (int) (image.getWidth() * scale);
        int dh = (int) (image.getHeight() * scale);
        int dx = (width - dw) / 2;
        int dy = (height - dh) / 2;
        g.drawImage(image, dx, dy, dw, dh, null);
        g.setColor(new Color(8, 12, 24, Math.round(255 * dim)));
        g.fillRect(0, 0, width, height);
    }

    /** Soft vignette so centered content always reads. */
    static void paintVignette(Graphics2D g, int width, int height) {
        RadialGradientPaint vignette = new RadialGradientPaint(
                new Point(width / 2, height / 2), Math.max(width, height) * 0.75f,
                new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(4, 6, 12, 190)});
        g.setPaint(vignette);
        g.fillRect(0, 0, width, height);
    }

    /** Draws centered text with letter tracking, in the title's gold gradient. */
    static void drawTitle(Graphics2D g, String text, Font font, int centerX, int baselineY, float tracking) {
        FontRenderContext context = g.getFontRenderContext();
        float total = 0;
        for (int i = 0; i < text.length(); i++) {
            Rectangle2D bounds = font.getStringBounds(text.substring(i, i + 1), context);
            total += bounds.getWidth() + tracking;
        }
        total -= tracking;
        float x = centerX - total / 2;
        g.setFont(font);
        GradientPaint gold = new GradientPaint(0, baselineY - 52, new Color(255, 232, 160),
                0, baselineY + 8, new Color(214, 158, 44));
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            Rectangle2D bounds = font.getStringBounds(ch, context);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawString(ch, x + 3, baselineY + 4);
            g.setPaint(gold);
            g.drawString(ch, x, baselineY);
            x += bounds.getWidth() + tracking;
        }
    }

    /** Width of a tracked string, for layout. */
    static float titleWidth(String text, Font font, float tracking) {
        FontRenderContext context = new FontRenderContext(null, true, true);
        float total = 0;
        for (int i = 0; i < text.length(); i++)
            total += font.getStringBounds(text.substring(i, i + 1), context).getWidth() + tracking;
        return total - tracking;
    }

    /** The shell's signature button: dark pill, gold edge on hover, quiet otherwise. */
    static final class MenuButton extends JButton {
        private float hover = 0f;
        private final Timer hoverTimer;

        MenuButton(String text) {
            super(text);
            setFont(BUTTON_FONT);
            setForeground(new Color(235, 238, 244));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(12, 34, 12, 34));
            hoverTimer = new Timer(16, null);
            hoverTimer.addActionListener(e -> {
                float target = getModel().isRollover() || isFocusOwner() ? 1f : 0f;
                hover += Math.signum(target - hover) * 0.12f;
                hover = Math.min(1f, Math.max(0f, hover));
                repaint();
                if (hover == target) hoverTimer.stop();
            });
            addChangeListener(e -> { if (!hoverTimer.isRunning()) hoverTimer.start(); });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            float glow = getModel().isPressed() ? 1f : hover;
            g.setColor(new Color(16, 24, 40, 205));
            g.fillRoundRect(0, 0, w, h, 14, 14);
            if (glow > 0.01f) {
                g.setColor(new Color(240, 191, 73, Math.round(90 + 90 * glow)));
                g.setStroke(new BasicStroke(1.5f + glow));
                g.drawRoundRect(1, 1, w - 2, h - 2, 14, 14);
                g.setColor(new Color(240, 191, 73, Math.round(28 * glow)));
                g.fillRoundRect(0, 0, w, h, 14, 14);
            } else {
                g.setColor(new Color(120, 140, 170, 70));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(1, 1, w - 2, h - 2, 14, 14);
            }
            g.setColor(glow > 0.5f ? new Color(255, 244, 214) : getForeground());
            FontMetrics metrics = g.getFontMetrics();
            String text = getText();
            int tx = (w - metrics.stringWidth(text)) / 2;
            int ty = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, tx, ty);
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = Math.max(size.width, 300);
            return size;
        }
    }

    /** Small gold-on-dark label for eyebrows and footers. */
    static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(new Color(168, 182, 200));
        return label;
    }
}
