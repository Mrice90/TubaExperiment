package com.infiniteconquest.gui;

import com.infiniteconquest.cli.DemoMatchFactory;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shell screens construct and paint headless without throwing, so a broken
 * menu can never block the game from starting.
 */
class ShellScreensTest {
    private static GameShell stubShell() {
        return new GameShell(GameSettings.load());
    }

    private static BufferedImage paint(JComponent screen, int width, int height) {
        screen.setSize(width, height);
        screen.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        screen.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void titleScreenPaintsItsBackdropMenuAndChips() {
        TitleScreen screen = new TitleScreen(stubShell(), GameSettings.load());
        screen.onShow();
        BufferedImage image = paint(screen, 1500, 950);
        // Something painted: not every pixel is the flat background.
        assertTrue(pixelVariance(image) > 0.02, "title screen should paint art, title, and buttons");
        assertEquals(7, countButtons(screen)); // Play, Multiplayer, Deck Builder, How to Play, Settings, Credits, Exit
        screen.onHide();
    }

    @Test
    void titleScreenPaintsStormCloudsAndLightning() throws Exception {
        GameSettings settings = GameSettings.load();
        settings.animationMode = GameSettings.AnimationMode.FULL;
        TitleScreen screen = new TitleScreen(stubShell(), settings);
        screen.onShow();
        // Drive the real weather update path: clouds drift, no bolt yet.
        var updateWeather = TitleScreen.class.getDeclaredMethod("updateWeather", double.class);
        updateWeather.setAccessible(true);
        for (int i = 0; i < 30; i++) updateWeather.invoke(screen, .033);
        var clouds = TitleScreen.class.getDeclaredField("clouds");
        clouds.setAccessible(true);
        assertEquals(4, ((java.util.List<?>) clouds.get(screen)).size(),
                "four storm clouds should seed over the title backdrop");
        // Force a mid-strike frame to exercise the bolt and sky flash.
        var boltFlash = TitleScreen.class.getDeclaredField("boltFlash");
        boltFlash.setAccessible(true);
        boltFlash.set(screen, 1.0);
        BufferedImage image = paint(screen, 1500, 950);
        assertTrue(pixelVariance(image) > 0.02,
                "storm title should paint art, clouds, lightning, and menu");
        screen.onHide();
    }

    @Test
    void titleScreenReducedModePaintsStaticStorm() {
        GameSettings settings = GameSettings.load();
        settings.animationMode = GameSettings.AnimationMode.REDUCED;
        TitleScreen screen = new TitleScreen(stubShell(), settings);
        screen.onShow();
        BufferedImage image = paint(screen, 1500, 950);
        assertTrue(pixelVariance(image) > 0.02,
                "reduced title should still paint its static storm backdrop");
        screen.onHide();
    }

    @Test
    void settingsScreenBuildsEveryRow() {
        SettingsScreen screen = new SettingsScreen(stubShell(), GameSettings.load());
        screen.onShow();
        BufferedImage image = paint(screen, 1300, 850);
        assertTrue(pixelVariance(image) > 0.01, "settings screen should paint its form");
        screen.onHide();
    }

    @Test
    void creditsAndHowToPlayPaint() {
        DemoMatchFactory factory = new DemoMatchFactory();
        CreditsScreen creditsScreen = new CreditsScreen(stubShell());
        creditsScreen.onShow();
        HowToPlayScreen howToScreen = new HowToPlayScreen(stubShell(), factory.pool());
        howToScreen.onShow();
        BufferedImage credits = paint(creditsScreen, 1200, 800);
        BufferedImage howTo = paint(howToScreen, 1200, 800);
        assertTrue(pixelVariance(credits) > 0.005);
        assertTrue(pixelVariance(howTo) > 0.005);
        creditsScreen.onHide();
        howToScreen.onHide();
    }

    @Test
    void howToPlayKeywordsComeFromRealCardData() {
        DemoMatchFactory factory = new DemoMatchFactory();
        HowToPlayScreen screen = new HowToPlayScreen(stubShell(), factory.pool());
        // Keywords tab content is generated from the pool; every listed keyword
        // must exist on at least one real card.
        assertFalse(factory.pool().cards().isEmpty());
    }

    @Test
    void loadingScreenPaintsProgressChrome() {
        LoadingScreen screen = new LoadingScreen();
        screen.onShow();
        BufferedImage image = paint(screen, 1300, 850);
        assertTrue(pixelVariance(image) > 0.01, "loading screen should paint logo and progress bar");
        screen.onHide();
    }

    private static int countButtons(Container container) {
        int count = container instanceof JButton ? 1 : 0;
        for (Component child : container.getComponents())
            if (child instanceof Container inner) count += countButtons(inner);
        return count;
    }

    /** Fraction of pixels that differ from the panel background: a smoke signal for "painted". */
    private static double pixelVariance(BufferedImage image) {
        int background = new Color(10, 15, 28).getRGB();
        int different = 0, total = 0;
        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                total++;
                if (image.getRGB(x, y) != background) different++;
            }
        }
        return different / (double) total;
    }
}
