package com.infiniteconquest.gui;

import java.awt.Dimension;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persisted desktop settings. Stored in {@code ~/.infinite-conquest/settings.properties}.
 * Every setting here is wired to something real: window placement, sound, animation pacing,
 * and loading-screen tips.
 */
final class GameSettings {
    enum AnimationMode { FULL, REDUCED }

    private static final String KEY_FULLSCREEN = "window.fullscreen";
    private static final String KEY_WIDTH = "window.width";
    private static final String KEY_HEIGHT = "window.height";
    private static final String KEY_SOUND = "sound.enabled";
    private static final String KEY_VOLUME = "sound.volume";
    private static final String KEY_ANIMATION = "animation.mode";
    private static final String KEY_TIPS = "tips.enabled";

    boolean fullscreen;
    int windowWidth;
    int windowHeight;
    boolean soundEnabled;
    int volume; // 0..100
    AnimationMode animationMode;
    boolean tipsEnabled;

    private GameSettings() {
        resetToDefaults();
    }

    void resetToDefaults() {
        fullscreen = false;
        windowWidth = 1500;
        windowHeight = 980;
        soundEnabled = true;
        volume = 80;
        animationMode = AnimationMode.FULL;
        tipsEnabled = true;
    }

    Dimension windowSize() {
        return new Dimension(Math.max(1100, windowWidth), Math.max(640, windowHeight));
    }

    /** Full animations run; reduced mode skips transitions and particles. */
    long transitionMillis() {
        return animationMode == AnimationMode.FULL ? 280 : 0;
    }

    static Path settingsFile() {
        return Path.of(System.getProperty("user.home"), ".infinite-conquest", "settings.properties");
    }

    static GameSettings load() {
        GameSettings settings = new GameSettings();
        Path file = settingsFile();
        if (!Files.isRegularFile(file)) return settings;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (Exception ignored) {
            return settings;
        }
        settings.fullscreen = Boolean.parseBoolean(props.getProperty(KEY_FULLSCREEN, "false"));
        settings.windowWidth = parseInt(props.getProperty(KEY_WIDTH), 1500);
        settings.windowHeight = parseInt(props.getProperty(KEY_HEIGHT), 980);
        settings.soundEnabled = Boolean.parseBoolean(props.getProperty(KEY_SOUND, "true"));
        settings.volume = Math.min(100, Math.max(0, parseInt(props.getProperty(KEY_VOLUME), 80)));
        try {
            settings.animationMode = AnimationMode.valueOf(props.getProperty(KEY_ANIMATION, "FULL"));
        } catch (IllegalArgumentException ignored) {
            settings.animationMode = AnimationMode.FULL;
        }
        settings.tipsEnabled = Boolean.parseBoolean(props.getProperty(KEY_TIPS, "true"));
        return settings;
    }

    void save() {
        Properties props = new Properties();
        props.setProperty(KEY_FULLSCREEN, Boolean.toString(fullscreen));
        props.setProperty(KEY_WIDTH, Integer.toString(windowWidth));
        props.setProperty(KEY_HEIGHT, Integer.toString(windowHeight));
        props.setProperty(KEY_SOUND, Boolean.toString(soundEnabled));
        props.setProperty(KEY_VOLUME, Integer.toString(volume));
        props.setProperty(KEY_ANIMATION, animationMode.name());
        props.setProperty(KEY_TIPS, Boolean.toString(tipsEnabled));
        try {
            Files.createDirectories(settingsFile().getParent());
            try (OutputStream out = Files.newOutputStream(settingsFile())) {
                props.store(out, "Infinite Conquest desktop settings");
            }
        } catch (Exception ignored) {
            // Settings are a convenience; a failed save must never block the game.
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
