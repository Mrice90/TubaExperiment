package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The online identity: a persistent random UUID (never hardware-derived) and
 * a user-editable, sanitized display name. Both live in settings.properties.
 */
class PlayerIdentityTest {
    private static final String ORIGINAL_HOME = System.getProperty("user.home");

    private Path fakeHome() throws Exception {
        Path home = Files.createTempDirectory("ic-identity");
        System.setProperty("user.home", home.toString());
        return home;
    }

    private void restoreHome() {
        System.setProperty("user.home", ORIGINAL_HOME);
    }

    @Test void freshInstallGetsRandomUuidAndDefaultName() throws Exception {
        fakeHome();
        try {
            GameSettings settings = GameSettings.load();
            assertDoesNotThrow(() -> UUID.fromString(settings.playerUuid));
            assertEquals("Player", settings.playerName);
        } finally {
            restoreHome();
        }
    }

    @Test void uuidSurvivesRoundTrip() throws Exception {
        fakeHome();
        try {
            GameSettings first = GameSettings.load();
            String uuid = first.playerUuid;
            first.save();
            GameSettings second = GameSettings.load();
            assertEquals(uuid, second.playerUuid);
        } finally {
            restoreHome();
        }
    }

    @Test void corruptUuidIsRegenerated() throws Exception {
        Path home = fakeHome();
        try {
            Path file = GameSettings.settingsFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "player.uuid=not-a-uuid\n");
            GameSettings settings = GameSettings.load();
            assertDoesNotThrow(() -> UUID.fromString(settings.playerUuid));
            assertNotEquals("not-a-uuid", settings.playerUuid);
        } finally {
            restoreHome();
        }
    }

    @Test void displayNameIsSanitized() {
        assertEquals("Player", GameSettings.sanitizeName(null));
        assertEquals("Player", GameSettings.sanitizeName("   "));
        assertEquals("Mathew", GameSettings.sanitizeName("  Mathew  "));
        assertEquals("EvilName", GameSettings.sanitizeName("Evil\u0000Name\u0007"));
        String longName = "abcdefghijklmnopqrstuvwxyz";
        assertEquals(24, GameSettings.sanitizeName(longName).length());
        assertEquals("abcdefghijklmnopqrstuvwx", GameSettings.sanitizeName(longName));
    }

    @Test void displayNameRoundTrips() throws Exception {
        fakeHome();
        try {
            GameSettings settings = GameSettings.load();
            settings.setPlayerName("  ZeusFan42  ");
            assertEquals("ZeusFan42", settings.playerName);
            settings.save();
            assertEquals("ZeusFan42", GameSettings.load().playerName);
        } finally {
            restoreHome();
        }
    }

    @Test void uuidIsRandomNotHardwareDerived() throws Exception {
        fakeHome();
        try {
            String first = GameSettings.load().playerUuid;
            Files.deleteIfExists(GameSettings.settingsFile());
            String second = GameSettings.load().playerUuid;
            assertNotEquals(first, second, "fresh installs must not derive the same id");
        } finally {
            restoreHome();
        }
    }
}
