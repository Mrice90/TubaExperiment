package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Restart-command construction and update-log diagnostics for the in-app
 * updater. Pure logic only: no network, no UI, no process spawning
 * (restartToApply/handoffToInstaller both end in System.exit and are
 * exercised through these seams instead).
 */
class UpdateApplierTest {

    @TempDir
    Path tmp;

    @Test
    void launcherScriptResolvesUnderInstallRoot() {
        Path root = tmp.resolve("InfiniteConquest");
        assertEquals(root.resolve("Play Infinite Conquest.vbs"),
                UpdateApplier.launcherScript(root));
    }

    @Test
    void restartCommandRunsLauncherThroughWscript() {
        // Spaces in the install path must survive: ProcessBuilder gets two
        // verbatim argv entries, so no shell quoting is required or wanted.
        Path root = tmp.resolve("Some Install Dir");
        List<String> cmd = UpdateApplier.restartCommand(root);
        assertEquals(2, cmd.size());
        assertEquals("wscript.exe", cmd.get(0));
        assertEquals(root.resolve("Play Infinite Conquest.vbs").toString(), cmd.get(1));
    }

    @Test
    void updateLogSitsNextToPendingDir() {
        Path root = tmp.resolve("InfiniteConquest");
        Path log = UpdateApplier.updateLogPath(root);
        assertEquals("update.log", log.getFileName().toString());
        // "next to the pending dir": same parent directory as app/pending/
        assertEquals(root.resolve("app").resolve("pending").getParent(), log.getParent());
    }

    @Test
    void appendUpdateLogWritesAndAppends() throws Exception {
        Path root = tmp.resolve("InfiniteConquest");
        Files.createDirectories(root.resolve("app"));
        UpdateApplier.appendUpdateLog(root, "first");
        UpdateApplier.appendUpdateLog(root, "second");
        String log = Files.readString(UpdateApplier.updateLogPath(root));
        assertTrue(log.contains("[updater] first"), "missing first entry:\n" + log);
        assertTrue(log.contains("[updater] second"), "missing second entry:\n" + log);
        assertTrue(log.indexOf("first") < log.indexOf("second"), "entries out of order");
    }

    @Test
    void sha256HexMatchesKnownDigest() throws Exception {
        Path f = tmp.resolve("data.bin");
        Files.write(f, "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // echo -n abc | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UpdateApplier.sha256Hex(f));
    }

    /** Builds a fake jars-only update zip on disk: app/new.jar with given bytes. */
    private static Path fakeUpdateZip(Path dir, byte[] jarBytes) throws Exception {
        Path zip = dir.resolve("update.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("app/new.jar"));
            zos.write(jarBytes);
            zos.closeEntry();
        }
        return zip;
    }

    /** An install root with one existing jar, ready to be updated. */
    private static Path fakeInstallRoot(Path base) throws Exception {
        Path root = base.resolve("game");
        Files.createDirectories(root.resolve("app"));
        Files.write(root.resolve("app").resolve("old.jar"), new byte[]{1, 2, 3});
        return root;
    }

    private static UpdateChecker.UpdateInfo infoWithHash(String hash) {
        return new UpdateChecker.UpdateInfo("9.9.9", "notes", "https://example.com/u.zip",
                10, null, hash, "");
    }

    @Test
    void mismatchedBytesNeverReachPending() throws Exception {
        Path root = fakeInstallRoot(tmp.resolve("t-mismatch"));
        Path zip = fakeUpdateZip(tmp.resolve("t-mismatch"), new byte[]{9, 9, 9});
        UpdateChecker.UpdateInfo info = infoWithHash("ff".repeat(32)); // wrong hash

        IOException e = assertThrows(IOException.class,
                () -> UpdateApplier.stageVerifiedDownload(zip, root, info));
        assertTrue(e.getMessage().contains("failed verification"),
                "user must see a clear verification message, got: " + e.getMessage());
        assertFalse(Files.exists(root.resolve("app").resolve("pending")),
                "tampered bytes must never reach pending/");
        assertFalse(Files.exists(root.resolve("app").resolve("backup-9.9.9")),
                "nothing may be staged or backed up before verification");
        // The untouched install is still intact.
        assertTrue(Files.exists(root.resolve("app").resolve("old.jar")));
        String log = Files.readString(UpdateApplier.updateLogPath(root));
        assertTrue(log.contains("failed SHA-256 verification"), "log must record the failure:\n" + log);
    }

    @Test
    void missingExpectedHashRefusesToStage() throws Exception {
        // Fail-closed: a release without published hashes is not trusted silently.
        Path root = fakeInstallRoot(tmp.resolve("t-nohash"));
        Path zip = fakeUpdateZip(tmp.resolve("t-nohash"), new byte[]{9, 9, 9});
        IOException e = assertThrows(IOException.class,
                () -> UpdateApplier.stageVerifiedDownload(zip, root, infoWithHash("")));
        assertTrue(e.getMessage().contains("failed verification"), e.getMessage());
        assertFalse(Files.exists(root.resolve("app").resolve("pending")));
    }

    @Test
    void correctHashStagesJars() throws Exception {
        Path root = fakeInstallRoot(tmp.resolve("t-ok"));
        Path zip = fakeUpdateZip(tmp.resolve("t-ok"), new byte[]{9, 9, 9});
        String hash = UpdateApplier.sha256Hex(zip);
        Path staged = UpdateApplier.stageVerifiedDownload(zip, root, infoWithHash(hash));
        assertEquals(root, staged);
        assertArrayEquals(new byte[]{9, 9, 9},
                Files.readAllBytes(root.resolve("app").resolve("pending").resolve("new.jar")));
        assertTrue(Files.exists(
                root.resolve("app").resolve("backup-" + GameVersion.VERSION).resolve("old.jar")),
                "running jars must be backed up before staging");
        String log = Files.readString(UpdateApplier.updateLogPath(root));
        assertTrue(log.contains("passed SHA-256 verification"), "log must record success:\n" + log);
    }

    @Test
    void appendUpdateLogNeverThrowsWhenUnwritable(@TempDir Path tmp) throws Exception {
        // Diagnostics are best-effort: an unwritable location must not break
        // the update itself. Here app/ is a regular file, so creating the log
        // directory fails; the helper must swallow that.
        Path root = tmp.resolve("InfiniteConquest");
        Files.createDirectories(root);
        Files.createFile(root.resolve("app"));
        assertDoesNotThrow(() -> UpdateApplier.appendUpdateLog(root, "x"));
    }
}
