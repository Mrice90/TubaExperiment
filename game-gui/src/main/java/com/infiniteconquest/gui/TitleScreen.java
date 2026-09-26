package com.infiniteconquest.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
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
    /** Drifting storm clouds layered over the backdrop. */
    private final List<StormCloud> clouds = new ArrayList<>();
    private BufferedImage cloudSprite;
    /** Lightning state: 1 at the strike, easing back to 0. */
    private double boltFlash;
    private double nextBolt = 3;
    private long boltSeed;
    private float boltX = .2f;

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
        addMenuButton(column, "Multiplayer", e -> shell.showMultiplayer());
        addMenuButton(column, "Deck Builder", e -> shell.openDeckBuilder());
        addMenuButton(column, "How to Play", e -> shell.showHowToPlay());
        addMenuButton(column, "Settings", e -> shell.showSettings());
        addMenuButton(column, "Credits", e -> shell.showCredits());
        addMenuButton(column, "Exit", e -> shell.requestExit());

        column.add(Box.createVerticalStrut(30));
        JLabel footer = ShellUi.caption(GameVersion.displayName() + "  ·  Zeus vs Poseidon  ·  F1 for help, Esc to exit");
        footer.setAlignmentX(CENTER_ALIGNMENT);
        column.add(footer);
        add(column);

        installKeyboard();
    }

    /** A soft procedural storm cloud: position, scale, drift speed, base alpha. */
    private static final class StormCloud {
        float x, y, scale, speed, alpha;
        StormCloud(float x, float y, float scale, float speed, float alpha) {
            this.x = x; this.y = y; this.scale = scale; this.speed = speed; this.alpha = alpha;
        }
    }

    /** Builds the shared cloud puff: layered soft blobs tinted storm blue-grey. */
    private static BufferedImage makeCloudSprite() {
        BufferedImage sprite = new BufferedImage(480, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sprite.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(7);
        for (int i = 0; i < 18; i++) {
            int cx = 60 + r.nextInt(360), cy = 55 + r.nextInt(70);
            int rad = 40 + r.nextInt(70);
            int base = 46 + r.nextInt(22);
            Color core = new Color(base, base + 10, base + 30, 30);
            Color edge = new Color(base, base + 10, base + 30, 0);
            RadialGradientPaint paint = new RadialGradientPaint(cx, cy, rad,
                    new float[]{0f, .7f, 1f},
                    new Color[]{core, new Color(base, base + 10, base + 30, 12), edge});
            g.setPaint(paint);
            g.fillOval(cx - rad, cy - rad, rad * 2, rad * 2);
        }
        g.dispose();
        return sprite;
    }

    private void seedClouds() {
        clouds.clear();
        for (int i = 0; i < 4; i++) {
            clouds.add(new StormCloud(random.nextFloat() * 1.2f, .03f + random.nextFloat() * .28f,
                    .8f + random.nextFloat() * 1.1f, .010f + random.nextFloat() * .018f,
                    .10f + random.nextFloat() * .12f));
        }
    }

    /** Advances clouds and lightning; called from the animation timer. */
    private void updateWeather(double dt) {
        for (StormCloud cloud : clouds) {
            cloud.x -= cloud.speed * dt;
            if (cloud.x < -.45f) cloud.x = 1.35f;
        }
        nextBolt -= dt;
        if (nextBolt <= 0) {
            boltFlash = 1;
            boltSeed = random.nextLong();
            boltX = random.nextBoolean() ? .04f + random.nextFloat() * .24f : .72f + random.nextFloat() * .24f;
            nextBolt = 4 + random.nextDouble() * 6;
            // Distant lightning stays silent: no thunder cue in the library.
        }
        boltFlash = Math.max(0, boltFlash - dt / .9);
    }
    private void addMenuButton(JPanel column, String text, java.util.function.Consumer<ActionEvent> action) {
        ShellUi.MenuButton button = new ShellUi.MenuButton(text);
        button.addActionListener(action::accept);
        FadePanel wrapper = new FadePanel(button);
        wrapper.setAlignmentX(CENTER_ALIGNMENT);
        // The column is as wide as the letter-spaced title; without a cap the
        // buttons stretch under the Zeus/Poseidon art chips at the sides.
        wrapper.setMaximumSize(new Dimension(560, Integer.MAX_VALUE));
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
        if (cloudSprite == null) cloudSprite = makeCloudSprite();
        seedClouds();
        boltFlash = 0;
        nextBolt = 3;
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
                updateWeather(.033);
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
        paintClouds(g, w, h);
        for (Particle p : particles) {
            float fade = Math.min(1f, p.life() / (float) p.maxLife() * 2f);
            g.setColor(new Color(p.color().getRed(), p.color().getGreen(), p.color().getBlue(),
                    Math.round(150 * Math.min(1f, fade))));
            g.fillOval(Math.round(p.x()), Math.round(p.y()), Math.round(p.size()), Math.round(p.size()));
        }
        paintBolt(g, w, h);
        ShellUi.paintVignette(g, w, h);
        paintCapitalChip(g, zeusArt, (int) (w * 0.13), h / 2, "ZEUS");
        paintCapitalChip(g, poseidonArt, (int) (w * 0.87), h / 2, "POSEIDON");
        g.dispose();
    }

    /** Layers the drifting storm clouds over the backdrop; lightning brightens them. */
    private void paintClouds(Graphics2D g, int w, int h) {
        if (cloudSprite == null || w <= 0) return;
        float brighten = (float) Math.min(.28, boltFlash * .28);
        for (StormCloud cloud : clouds) {
            int cw = Math.round(w * .55f * cloud.scale);
            int ch = Math.round(cw * cloudSprite.getHeight() / (float) cloudSprite.getWidth());
            int x = Math.round(cloud.x * w - cw / 2f);
            int y = Math.round(cloud.y * h);
            g.setComposite(AlphaComposite.SrcOver.derive(Math.min(.55f, cloud.alpha + brighten)));
            g.drawImage(cloudSprite, x, y, cw, ch, null);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Draws the distant lightning bolt and the sky flash that follows it. */
    private void paintBolt(Graphics2D g, int w, int h) {
        float fade = Fx.easeOutCubic((float) Math.max(0, Math.min(1, boltFlash)));
        if (fade <= 0f) return;
        Random r = new Random(boltSeed);
        Path2D bolt = new Path2D.Float();
        int x = Math.round(boltX * w), y = 0;
        bolt.moveTo(x, y);
        while (y < h * .55) {
            y += 30 + r.nextInt(50);
            x += r.nextInt(70) - 35;
            bolt.lineTo(x, y);
        }
        g.setComposite(AlphaComposite.SrcOver.derive(.8f * fade));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(150, 190, 255, 90));
        g.draw(bolt);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(225, 235, 255));
        g.draw(bolt);
        g.setComposite(AlphaComposite.SrcOver.derive(.20f * fade));
        g.setColor(new Color(190, 210, 255));
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver);
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
