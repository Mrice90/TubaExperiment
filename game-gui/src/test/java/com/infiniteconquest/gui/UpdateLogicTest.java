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
    void sha256LinesParseFromReleaseBody() {
        String updateHex = "ab".repeat(32);
        String setupHex = "CD".repeat(32); // uppercase tolerated, normalized down
        String body = "## Notes\n\n### Integrity (SHA-256)\n\n"
                + "SHA256-Update: " + updateHex + "  (InfiniteConquest-Update-0.7.5.zip)\n"
                + "SHA256-Setup: " + setupHex + "  (InfiniteConquest-Alpha-0.7.5-Setup.exe)\n";
        assertEquals(updateHex, UpdateChecker.parseSha256(body, "Update"));
        assertEquals(setupHex.toLowerCase(), UpdateChecker.parseSha256(body, "Setup"));
        assertEquals("", UpdateChecker.parseSha256(body, "Portable"));
        assertEquals("", UpdateChecker.parseSha256("no hashes here", "Update"));
    }

    @Test
    void sha256ParseRejectsMalformedHashes() {
        assertEquals("", UpdateChecker.parseSha256("SHA256-Update: not-a-hash", "Update"));
        assertEquals("", UpdateChecker.parseSha256("SHA256-Update: " + "ab".repeat(31), "Update"));
        assertEquals("", UpdateChecker.parseSha256("SHA256-Update: " + "zz".repeat(32), "Update"));
    }

    @Test
    void parseReleaseCarriesExpectedHashes() throws Exception {
        String hex = "01".repeat(32);
        String json = "{\"tag_name\":\"v9.9.9-alpha\","
                + "\"body\":\"notes\\nSHA256-Update: " + hex + "  (f.zip)\\n"
                + "SHA256-Setup: " + hex + "  (s.exe)\","
                + "\"assets\":[{\"name\":\"InfiniteConquest-Update-9.9.9.zip\","
                + "\"browser_download_url\":\"https://example.com/u.zip\",\"size\":42}]}";
        UpdateChecker.UpdateInfo info = UpdateChecker.parseRelease(json);
        assertNotNull(info);
        assertEquals("9.9.9", info.version());
        assertEquals("https://example.com/u.zip", info.updateZipUrl());
        assertEquals(hex, info.updateZipSha256());
        assertEquals(hex, info.setupSha256());
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
