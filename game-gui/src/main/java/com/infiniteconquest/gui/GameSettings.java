package com.infiniteconquest.gui;

import com.infiniteconquest.cli.BotDifficulty;

import java.awt.Dimension;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persisted desktop settings. Stored in {@code ~/.infinite-conquest/settings.properties}.
 * Every setting here is wired to something real: window placement, sound, animation pacing,
 * loading-screen tips, and the player's online identity (random UUID + display name).
 */
public final class GameSettings {
    enum AnimationMode { FULL, REDUCED }

    private static final String KEY_FULLSCREEN = "window.fullscreen";
    private static final String KEY_WIDTH = "window.width";
    private static final String KEY_HEIGHT = "window.height";
    private static final String KEY_SOUND = "sound.enabled";
    private static final String KEY_VOLUME = "sound.volume";
    private static final String KEY_ANIMATION = "animation.mode";
    private static final String KEY_TIPS = "tips.enabled";
    private static final String KEY_CHECK_UPDATES = "updates.checkOnStartup";
    private static final String KEY_LAST_CHECK = "updates.lastCheck";
    private static final String KEY_SKIPPED = "updates.skippedVersion";
    private static final String KEY_SETTINGS_VERSION = "settings.version";
    private static final String KEY_PLAYER_UUID = "player.uuid";
    private static final String KEY_PLAYER_NAME = "player.name";
    private static final String KEY_LOBBY_WORKER_URL = "lobby.worker.url";
    private static final String KEY_PLAYER_RATING = "player.rating";
    private static final String KEY_BOT_DIFFICULTY = "bot.difficulty";
    /** Rating assigned by the lobby service (Elo, starts at 1000). */
    public static final int DEFAULT_RATING = 1000;
    /** Maximum display-name length, matching the netcode design. */
    public static final int MAX_NAME_LENGTH = 24;
    /**
     * Built-in lobby/rating Worker URL shipped with the game, so players get
     * the lobby browser, quick match, and ratings with zero setup. The
     * Settings field overrides this when non-blank (empty field = use this).
     * Set to the studio's deployed Worker origin once it exists.
     */
    public static final String DEFAULT_LOBBY_WORKER_URL = "";

    public boolean fullscreen;
    int windowWidth;
    int windowHeight;
    boolean soundEnabled;
    int volume; // 0..100
    AnimationMode animationMode;
    boolean tipsEnabled;
    boolean checkUpdatesOnStartup;
    long lastUpdateCheck; // epoch millis of the last check
    String skippedVersion; // update version the player asked not to be reminded about

    /** Persistent random player UUID (never hardware-derived). Public for the net package. */
    public String playerUuid;
    /** Player-chosen display name, sanitized, max 24 chars. Shown to other players. */
    public String playerName;
    /**
     * Lobby/rating Worker origin, e.g. {@code https://ic-lobby.workers.dev}.
     * Blank means "use the built-in default" ({@link #DEFAULT_LOBBY_WORKER_URL});
     * direct tunnel links work without any lobby service, but the lobby
     * browser, quick match, and ratings need one of the two.
     */
    public String lobbyWorkerUrl;
    /** Last known Elo rating from the lobby service; 1000 until the first report. */
    public int playerRating;
    /**
     * Bot skill level for single-player battles. HERO is the classic
     * challenge and the default for fresh installs.
     */
    public BotDifficulty botDifficulty;

    /**
     * The lobby Worker URL actually in effect: the player's Settings override
     * when set, otherwise the built-in default shipped with the game.
     */
    public String effectiveLobbyWorkerUrl() {
        if (lobbyWorkerUrl != null && !lobbyWorkerUrl.isBlank()) return lobbyWorkerUrl.trim();
        return DEFAULT_LOBBY_WORKER_URL;
    }

    private GameSettings() {
        resetToDefaults();
    }

    void resetToDefaults() {
        fullscreen = true;
        windowWidth = 1500;
        windowHeight = 980;
        soundEnabled = true;
        volume = 80;
        animationMode = AnimationMode.FULL;
        tipsEnabled = true;
        checkUpdatesOnStartup = true;
        lastUpdateCheck = 0;
        skippedVersion = "";
        playerUuid = java.util.UUID.randomUUID().toString();
        playerName = "Player";
        playerRating = DEFAULT_RATING;
        botDifficulty = BotDifficulty.HERO;
        lobbyWorkerUrl = "";
    }

    /**
     * Sets the display name: strips control characters, trims, caps at
     * {@link #MAX_NAME_LENGTH}, and falls back to "Player" when blank.
     * The name is public to other players; the UUID stays private.
     */
    public void setPlayerName(String name) {
        playerName = sanitizeName(name);
    }

    /** Sanitizes a display name the same way the server does. */
    public static String sanitizeName(String name) {
        if (name == null) return "Player";
        String cleaned = name.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.length() > MAX_NAME_LENGTH) cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        if (cleaned.isEmpty()) cleaned = "Player";
        return cleaned;
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

    public static GameSettings load() {
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
        settings.checkUpdatesOnStartup =
                Boolean.parseBoolean(props.getProperty(KEY_CHECK_UPDATES, "true"));
        settings.lastUpdateCheck = parseLong(props.getProperty(KEY_LAST_CHECK), 0);
        settings.skippedVersion = props.getProperty(KEY_SKIPPED, "");
        String uuid = props.getProperty(KEY_PLAYER_UUID, "").trim();
        try {
            if (uuid.isEmpty()) throw new IllegalArgumentException("missing");
            java.util.UUID.fromString(uuid);
            settings.playerUuid = uuid;
        } catch (IllegalArgumentException e) {
            settings.playerUuid = java.util.UUID.randomUUID().toString();
        }
        settings.playerName = sanitizeName(props.getProperty(KEY_PLAYER_NAME, "Player"));
        try {
            settings.playerRating = Integer.parseInt(props.getProperty(KEY_PLAYER_RATING, "1000"));
        } catch (NumberFormatException e) {
            settings.playerRating = DEFAULT_RATING;
        }
        try {
            settings.botDifficulty = BotDifficulty.valueOf(props.getProperty(KEY_BOT_DIFFICULTY, "HERO"));
        } catch (IllegalArgumentException e) {
            settings.botDifficulty = BotDifficulty.HERO;
        }
        settings.lobbyWorkerUrl = props.getProperty(KEY_LOBBY_WORKER_URL, "").trim();
        // 0.6.0 changed the display default: pre-0.6.0 settings files adopt fullscreen once.
        if (!props.containsKey(KEY_SETTINGS_VERSION)) {
            settings.fullscreen = true;
        }
        return settings;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty(KEY_FULLSCREEN, Boolean.toString(fullscreen));
        props.setProperty(KEY_WIDTH, Integer.toString(windowWidth));
        props.setProperty(KEY_HEIGHT, Integer.toString(windowHeight));
        props.setProperty(KEY_SOUND, Boolean.toString(soundEnabled));
        props.setProperty(KEY_VOLUME, Integer.toString(volume));
        props.setProperty(KEY_ANIMATION, animationMode.name());
        props.setProperty(KEY_TIPS, Boolean.toString(tipsEnabled));
        props.setProperty(KEY_CHECK_UPDATES, Boolean.toString(checkUpdatesOnStartup));
        props.setProperty(KEY_LAST_CHECK, Long.toString(lastUpdateCheck));
        props.setProperty(KEY_SKIPPED, skippedVersion);
        props.setProperty(KEY_PLAYER_UUID, playerUuid);
        props.setProperty(KEY_PLAYER_NAME, playerName);
        props.setProperty(KEY_PLAYER_RATING, Integer.toString(playerRating));
        props.setProperty(KEY_BOT_DIFFICULTY, botDifficulty == null ? BotDifficulty.HERO.name() : botDifficulty.name());
        props.setProperty(KEY_LOBBY_WORKER_URL, lobbyWorkerUrl == null ? "" : lobbyWorkerUrl);
        props.setProperty(KEY_SETTINGS_VERSION, GameVersion.VERSION);
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

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
