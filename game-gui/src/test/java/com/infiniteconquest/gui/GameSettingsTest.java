package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Settings persist, validate, and degrade gracefully. */
class GameSettingsTest {
    @Test
    void defaultsAreSaneAndTransitionsAnimate() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-settings-defaults");
        System.setProperty("user.home", fakeHome.toString());
        try {
            GameSettings settings = GameSettings.load();
            assertEquals(new Dimension(1500, 980), settings.windowSize());
            assertTrue(settings.fullscreen);
            assertTrue(settings.soundEnabled);
            assertEquals(80, settings.volume);
            assertEquals(GameSettings.AnimationMode.FULL, settings.animationMode);
            assertTrue(settings.tipsEnabled);
            assertTrue(settings.checkUpdatesOnStartup);
            assertEquals(0, settings.lastUpdateCheck);
            assertEquals("", settings.skippedVersion);
            assertTrue(settings.transitionMillis() > 0);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void roundTripPreservesEveryField() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-settings-test");
        System.setProperty("user.home", fakeHome.toString());
        try {
            GameSettings settings = GameSettings.load();
            settings.fullscreen = true;
            settings.windowWidth = 1920;
            settings.windowHeight = 1080;
            settings.soundEnabled = false;
            settings.volume = 37;
            settings.animationMode = GameSettings.AnimationMode.REDUCED;
            settings.tipsEnabled = false;
            settings.save();

            GameSettings reloaded = GameSettings.load();
            assertTrue(reloaded.fullscreen);
            assertEquals(new Dimension(1920, 1080), reloaded.windowSize());
            assertFalse(reloaded.soundEnabled);
            assertEquals(37, reloaded.volume);
            assertEquals(GameSettings.AnimationMode.REDUCED, reloaded.animationMode);
            assertFalse(reloaded.tipsEnabled);
            assertEquals(0, reloaded.transitionMillis());
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void ratingRoundTripsWithDefault() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-settings-rating-test");
        System.setProperty("user.home", fakeHome.toString());
        try {
            GameSettings settings = GameSettings.load();
            assertEquals(GameSettings.DEFAULT_RATING, settings.playerRating);
            settings.playerRating = 1016;
            settings.save();

            GameSettings reloaded = GameSettings.load();
            assertEquals(1016, reloaded.playerRating);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void corruptSettingsFallBackToDefaults() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-settings-corrupt");
        System.setProperty("user.home", fakeHome.toString());
        try {
            Path file = GameSettings.settingsFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "window.width=banana\nsound.volume=999\nanimation.mode=BOGUS\n");
            GameSettings settings = GameSettings.load();
            assertEquals(1500, settings.windowWidth);
            assertEquals(100, settings.volume); // clamped, not crashed
            assertEquals(GameSettings.AnimationMode.FULL, settings.animationMode);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }
}
