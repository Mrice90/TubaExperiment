package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
