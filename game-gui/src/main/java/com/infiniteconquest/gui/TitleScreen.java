package com.infiniteconquest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The game's front door: painted storm backdrop, drifting embers, letter-spaced
 * title, and the six ways in. Animated while visible, frozen while hidden.
 */
final class TitleScreen extends JPanel implements ShellScreen {
    private final GameShell shell;
    private final GameSettings settings;
    private final List<ShellUi.MenuButton> menuButtons = new ArrayList<>();
    private final List<FadePanel> fadePanels = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final TitleBanner banner = new TitleBanner();
    private Timer particleTimer;
    private Timer entranceTimer;
    private BufferedImage backdrop;
    private BufferedImage zeusArt;
    private BufferedImage poseidonArt;
    private int selectedIndex;

    TitleScreen(GameShell shell, GameSettings settings) {
        this.shell = shell;
        this.settings = settings;
        setLayout(new GridBagLayout());
        setBackground(new Color(10, 15, 28));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        banner.setAlignmentX(CENTER_ALIGNMENT);
        column.add(banner);
        column.add(Box.createVerticalStrut(26));

        addMenuButton(column, "Play", e -> shell.play());
        addMenuButton(column, "Deck Builder", e -> shell.openDeckBuilder());
        addMenuButton(column, "How to Play", e -> shell.showHowToPlay());
        addMenuButton(column, "Settings", e -> shell.showSettings());
        addMenuButton(column, "Credits", e -> shell.showCredits());
        addMenuButton(column, "Exit", e -> shell.requestExit());

        column.add(Box.createVerticalStrut(30));
        JLabel footer = ShellUi.caption("Alpha 0.5.0  ·  Zeus vs Poseidon  ·  F1 for help, Esc to exit");
        footer.setAlignmentX(CENTER_ALIGNMENT);
        column.add(footer);
        add(column);

        installKeyboard();
    }

    private void addMenuButton(JPanel column, String text, java.util.function.Consumer<ActionEvent> action) {
        ShellUi.MenuButton button = new ShellUi.MenuButton(text);
        button.addActionListener(action::accept);
        FadePanel wrapper = new FadePanel(button);
        wrapper.setAlignmentX(CENTER_ALIGNMENT);
        column.add(wrapper);
        column.add(Box.createVerticalStrut(10));
        menuButtons.add(button);
        fadePanels.add(wrapper);
    }

    private void installKeyboard() {
        InputMap inputs = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = getActionMap();
        inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "menuUp");
        inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "menuDown");
        inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "menuHelp");
        inputs.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "menuExit");
        actions.put("menuUp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveSelection(-1); }
        });
        actions.put("menuDown", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveSelection(1); }
        });
        actions.put("menuHelp", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { shell.showHowToPlay(); }
        });
        actions.put("menuExit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { shell.requestExit(); }
        });
    }

    private void moveSelection(int delta) {
        selectedIndex = (selectedIndex + delta + menuButtons.size()) % menuButtons.size();
        menuButtons.get(selectedIndex).requestFocusInWindow();
    }

    @Override
    public void onShow() {
        if (backdrop == null) {
            backdrop = CardArtFactory.worldBackdrop();
            zeusArt = CardArtFactory.capitalPainting("zeus_capital_olympus_citadel");
            poseidonArt = CardArtFactory.capitalPainting("poseidon_capital_atlantis_nexus");
        }
        selectedIndex = 0;
        boolean animated = settings.animationMode == GameSettings.AnimationMode.FULL;
        banner.entrance = animated ? 0f : 1f;
        for (FadePanel panel : fadePanels) panel.alpha = animated ? 0f : 1f;
        if (animated) {
            long start = System.currentTimeMillis();
            entranceTimer = new Timer(16, null);
            entranceTimer.addActionListener(e -> {
                float elapsed = System.currentTimeMillis() - start;
                banner.entrance = Math.min(1f, elapsed / 550f);
                for (int i = 0; i < fadePanels.size(); i++) {
                    float local = (elapsed - 250 - i * 70) / 320f;
                    fadePanels.get(i).alpha = Math.min(1f, Math.max(0f, local));
                }
                banner.repaint();
                repaint();
                if (banner.entrance >= 1f && fadePanels.get(fadePanels.size() - 1).alpha >= 1f)
                    entranceTimer.stop();
            });
            entranceTimer.start();
            particles.clear();
            for (int i = 0; i < 70; i++) particles.add(spawn(true));
            particleTimer = new Timer(33, e -> {
                for (int i = 0; i < particles.size(); i++) {
                    Particle p = particles.get(i).drift();
                    particles.set(i, p.life() <= 0 ? spawn(false) : p);
                }
                repaint();
            });
            particleTimer.start();
        }
        repaint();
    }

    @Override
    public void onHide() {
        if (particleTimer != null) particleTimer.stop();
        if (entranceTimer != null) entranceTimer.stop();
    }

    private Particle spawn(boolean anywhere) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        float x = random.nextFloat() * w;
        float y = anywhere ? random.nextFloat() * h : h + 10;
        boolean gold = random.nextFloat() < 0.45f;
        return new Particle(x, y,
                (random.nextFloat() - 0.5f) * 0.35f,
                -(0.35f + random.nextFloat() * 0.8f),
                1.5f + random.nextFloat() * 2.6f,
                200 + random.nextInt(220), 200 + random.nextInt(220),
                gold ? new Color(255, 205, 110) : new Color(140, 220, 255));
    }

    private record Particle(float x, float y, float vx, float vy, float size, int life, int maxLife, Color color) {
        Particle drift() {
            return new Particle(x + vx + (float) Math.sin(y * 0.02) * 0.3f, y + vy, vx, vy, size, life - 1, maxLife, color);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        ShellUi.paintBackdrop(g, w, h);
        ShellUi.paintBackdropImage(g, backdrop, w, h, 0.62f);
        for (Particle p : particles) {
            float fade = Math.min(1f, p.life() / (float) p.maxLife() * 2f);
            g.setColor(new Color(p.color().getRed(), p.color().getGreen(), p.color().getBlue(),
                    Math.round(150 * Math.min(1f, fade))));
            g.fillOval(Math.round(p.x()), Math.round(p.y()), Math.round(p.size()), Math.round(p.size()));
        }
        ShellUi.paintVignette(g, w, h);
        paintCapitalChip(g, zeusArt, (int) (w * 0.13), h / 2, "ZEUS");
        paintCapitalChip(g, poseidonArt, (int) (w * 0.87), h / 2, "POSEIDON");
        g.dispose();
    }

    private void paintCapitalChip(Graphics2D g, BufferedImage art, int centerX, int centerY, String label) {
        if (art == null || getWidth() < 1150) return; // keep small windows uncluttered
        int cw = 210, ch = 140;
        int x = centerX - cw / 2, y = centerY - ch / 2;
        g.setColor(new Color(8, 12, 22, 200));
        g.fillRoundRect(x - 6, y - 6, cw + 12, ch + 34, 12, 12);
        g.drawImage(art, x, y, cw, ch, null);
        g.setColor(new Color(240, 191, 73, 160));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x - 6, y - 6, cw + 12, ch + 34, 12, 12);
        g.setFont(ShellUi.EYEBROW_FONT);
        g.setColor(new Color(232, 236, 244));
        g.drawString(label, x + (cw - g.getFontMetrics().stringWidth(label)) / 2, y + ch + 20);
    }

    /** The letter-spaced title block, with a rise-and-fade entrance. */
    private static final class TitleBanner extends JComponent {
        float entrance = 1f;

        TitleBanner() {
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            float w = ShellUi.titleWidth("INFINITE CONQUEST", ShellUi.TITLE_FONT, 10f);
            return new Dimension(Math.round(w) + 40, 150);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float e = entrance * entrance;
            int rise = Math.round((1 - e) * 26);
            Composite saved = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.01f, e)));
            int cx = getWidth() / 2;
            g.setFont(ShellUi.EYEBROW_FONT);
            g.setColor(new Color(240, 191, 73));
            String eyebrow = "A  H E X  &  A L L I E S  S T R A T E G Y  G A M E";
            g.drawString(eyebrow, cx - g.getFontMetrics().stringWidth(eyebrow) / 2, 26 - rise);
            ShellUi.drawTitle(g, "INFINITE CONQUEST", ShellUi.TITLE_FONT, cx, 100 - rise, 10f);
            g.setFont(ShellUi.SUBTITLE_FONT);
            g.setColor(new Color(205, 214, 230));
            String sub = "Zeus clashes with Poseidon for the mortal world";
            g.drawString(sub, cx - g.getFontMetrics().stringWidth(sub) / 2, 140 - rise);
            g.setComposite(saved);
            g.dispose();
        }
    }

    /** Panel that paints its child with a global alpha, for staggered entrances. */
    private static final class FadePanel extends JPanel {
        float alpha = 1f;

        FadePanel(JComponent child) {
            setOpaque(false);
            setLayout(new BorderLayout());
            add(child, BorderLayout.CENTER);
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, Math.max(0f, alpha))));
            super.paintChildren(g);
            g.dispose();
        }
    }
}
