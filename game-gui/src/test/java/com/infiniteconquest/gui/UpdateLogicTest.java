package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure version/update logic: no network, no UI. */
class UpdateLogicTest {

    @Test
    void newerVersionsCompareCorrectly() {
        assertTrue(GameVersion.isNewerThan("0.5.0", "0.6.0"));
        assertTrue(GameVersion.isNewerThan("0.6.0", "0.10.0"));
        assertFalse(GameVersion.isNewerThan("0.6.0", "0.6.0"));
        assertFalse(GameVersion.isNewerThan("0.6.0", "0.5.9"));
    }

    @Test
    void versionCompareIgnoresSuffixesAndTagPrefix() {
        assertEquals(0, GameVersion.compare("v0.6.0-alpha", "0.6.0"));
        assertTrue(GameVersion.compare("0.5.0", "0.6.0") > 0);
        assertTrue(GameVersion.compare("0.6.0", "0.5.0") < 0);
    }

    @Test
    void tagVersionsParseToDottedVersions() {
        assertEquals("0.6.0", UpdateChecker.parseTagVersion("v0.6.0-alpha"));
        assertEquals("0.6.0", UpdateChecker.parseTagVersion("0.6.0"));
        assertEquals("1.2.3", UpdateChecker.parseTagVersion("V1.2.3-beta"));
        assertEquals("", UpdateChecker.parseTagVersion(""));
    }

    @Test
    void releaseTagMatchesVersion() {
        assertEquals("v" + GameVersion.VERSION + "-alpha", GameVersion.TAG);
    }

    @Test
    void installedBuildDetectionLooksForUninstaller() throws Exception {
        var tmp = java.nio.file.Files.createTempDirectory("ic-installroot");
        try {
            assertFalse(UpdateChecker.isInstalledBuild(tmp));
            java.nio.file.Files.createFile(tmp.resolve("Uninstall.exe"));
            assertTrue(UpdateChecker.isInstalledBuild(tmp));
        } finally {
            java.nio.file.Files.deleteIfExists(tmp.resolve("Uninstall.exe"));
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}
