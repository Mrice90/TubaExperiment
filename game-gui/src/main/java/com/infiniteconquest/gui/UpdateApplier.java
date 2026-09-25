package com.infiniteconquest.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads a jars-only update package and stages it for the launcher.
 * The game cannot overwrite its own jars while running on Windows (the JVM
 * holds them open), so new jars land in {@code app/pending/} and the launcher
 * script swaps them in before Java starts. The launcher script itself is never
 * locked, so it is replaced directly.
 *
 * <p>Every downloaded byte is SHA-256-verified against the hash published in
 * the release notes BEFORE anything is staged or backed up; a mismatch (or a
 * release with no published hash) aborts, deletes the download, and logs to
 * app/update.log. Tampered or truncated bytes therefore never reach
 * app/pending/.
 *
 * <p>KNOWN LIMITATION (deliberate, $0 budget): SHA-256 proves the bytes match
 * what the release author published, but GitHub release notes are not signed.
 * A compromised GitHub account or token could publish a matching hash for a
 * malicious package. Full protection would need signed releases (PGP/cosign)
 * with a baked-in verification key; the hash check still defeats corrupted
 * downloads, CDN/proxy corruption, and transport-level tampering, which is
 * the realistic threat on this budget.
 */
final class UpdateApplier {
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /**
     * Downloads {@code info} into a temp file, validates it, backs up the current
     * jars, and stages the new ones. Reports bytes downloaded via {@code progress}.
     *
     * @return the staged install root, ready for {@link #restartToApply(Path)}
     */
    Path stageJarsUpdate(Path root, UpdateChecker.UpdateInfo info, LongConsumer progress)
            throws IOException, InterruptedException {
        if (info.updateZipUrl() == null || info.updateZipUrl().isEmpty()) {
            throw new IOException("This release has no lightweight update package; "
                    + "please reinstall from the release page.");
        }
        Path download = Files.createTempFile("ic-update-", ".zip");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(info.updateZipUrl()))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "InfiniteConquest-Updater/" + GameVersion.VERSION)
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed: HTTP " + response.statusCode());
            }
            long total = info.updateZipBytes();
            long done = 0;
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(download)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    done += n;
                    progress.accept(total > 0 ? done * 100 / total : -1);
                }
            }
            progress.accept(100);

            // Integrity gate: verify BEFORE the zip is even opened, so no
            // byte of an unverified download can reach the staging logic.
            return stageVerifiedDownload(download, root, info);
        } finally {
            Files.deleteIfExists(download);
        }
    }

    /**
     * Verifies a fully-downloaded update zip against its expected SHA-256 and
     * stages it. Package-visible so tests can exercise the verify-before-stage
     * ordering without the network: the download step is the only part skipped.
     */
    static Path stageVerifiedDownload(Path download, Path root, UpdateChecker.UpdateInfo info)
            throws IOException {
        verifyDownload(download, info.updateZipSha256(), root, "update package");
        return stageFromDownload(download, root, info);
    }

    /**
     * Verifies a downloaded file against the expected SHA-256 hex from the
     * release notes. On mismatch — or when the release carries no hash at all
     * (fail-closed: an old release without hashes must not be trusted silently) —
     * logs to app/update.log and throws. The caller's temp file is left for
     * its own finally block to delete; nothing is staged first.
     */
    static void verifyDownload(Path download, String expectedHex, Path root, String what)
            throws IOException {
        String actual;
        try {
            actual = sha256Hex(download);
        } catch (IOException e) {
            appendUpdateLog(root, "ERROR: could not hash downloaded " + what + ": " + e);
            throw new IOException("Download failed verification: "
                    + "could not read the downloaded file.", e);
        }
        if (expectedHex == null || expectedHex.isEmpty()) {
            appendUpdateLog(root, "ERROR: " + what
                    + " has no expected SHA-256 in the release notes; refusing to apply.");
            throw new IOException("Download failed verification: "
                    + "the release notes carry no integrity hash for this package. "
                    + "Please download the new version manually from the release page.");
        }
        if (!actual.equalsIgnoreCase(expectedHex)) {
            appendUpdateLog(root, "ERROR: " + what + " failed SHA-256 verification: expected "
                    + expectedHex + ", got " + actual + ". Download discarded; nothing was changed.");
            throw new IOException("Download failed verification: "
                    + "the downloaded file does not match the SHA-256 hash published with the release. "
                    + "The download was discarded and nothing was changed. "
                    + "Try again, or download manually from the release page.");
        }
        appendUpdateLog(root, what + " passed SHA-256 verification.");
    }

    /**
     * SHA-256 hex digest of a file. MessageDigest is JDK-bundled: no new
     * dependencies. Pure, for tests.
     */
    static String sha256Hex(Path file) throws IOException {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    digest.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK; unreachable in practice.
            throw new IOException("SHA-256 unavailable in this JVM", e);
        }
    }

    /**
     * Opens an already-verified update zip and stages its contents: backs up
     * the running jars, stages new jars (+ cloudflared.exe) into app/pending/,
     * and replaces the launcher script. Package-visible for tests.
     */
    static Path stageFromDownload(Path download, Path root, UpdateChecker.UpdateInfo info)
            throws IOException {
        List<String> jarEntries = new ArrayList<>();
            String launcherEntry = null;
            String cloudflaredEntry = null;
            try (ZipFile zip = new ZipFile(download.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String name = e.getName().replace('\\', '/');
                    if (name.startsWith("app/") && name.endsWith(".jar")) jarEntries.add(name);
                    else if (name.equals("Play Infinite Conquest.vbs")) launcherEntry = name;
                    else if (name.equals("app/cloudflared.exe")) cloudflaredEntry = name;
                }
            }
            if (jarEntries.isEmpty()) {
                throw new IOException("Update package is corrupt (no jars inside).");
            }

            Path app = root.resolve("app");
            // Back up the running jars before anything changes.
            Path backup = app.resolve("backup-" + GameVersion.VERSION);
            Files.createDirectories(backup);
            try (var jars = Files.list(app)) {
                for (Path jar : jars.toList()) {
                    if (jar.getFileName().toString().endsWith(".jar")
                            && !Files.isDirectory(jar)
                            && !jar.startsWith(backup)) {
                        Files.copy(jar, backup.resolve(jar.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Stage the new jars where the launcher will pick them up.
            Path pending = app.resolve("pending");
            deleteTree(pending);
            Files.createDirectories(pending);
            try (ZipFile zip = new ZipFile(download.toFile())) {
                for (String name : jarEntries) {
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                        Files.copy(in, pending.resolve(fileName),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                // cloudflared.exe (internet hosting) rides the same pending
                // mechanism; the launcher moves every staged file into app/.
                if (cloudflaredEntry != null) {
                    try (InputStream in = zip.getInputStream(zip.getEntry(cloudflaredEntry))) {
                        Files.copy(in, pending.resolve("cloudflared.exe"),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                if (launcherEntry != null) {
                    try (InputStream in = zip.getInputStream(zip.getEntry(launcherEntry))) {
                        Files.copy(in, root.resolve("Play Infinite Conquest.vbs"),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            appendUpdateLog(root, "Staged update " + info.version() + ": " + jarEntries.size()
                    + " jar(s)" + (cloudflaredEntry != null ? " + cloudflared.exe" : "")
                    + (launcherEntry != null ? ", launcher script replaced" : ""));
            return root;
    }

    /** Downloads the full installer and launches it, then exits for the upgrade. */
    Path downloadInstaller(UpdateChecker.UpdateInfo info, LongConsumer progress)
            throws IOException, InterruptedException {
        if (info.setupUrl() == null || info.setupUrl().isEmpty()) {
            throw new IOException("This release has no installer download.");
        }
        Path setup = Files.createTempFile("InfiniteConquest-Setup-", ".exe");
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.setupUrl()))
                .timeout(TIMEOUT)
                .header("User-Agent", "InfiniteConquest-Updater/" + GameVersion.VERSION)
                .GET()
                .build();
        HttpResponse<InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(setup)) {
            in.transferTo(out);
        }
        // Same integrity gate as the jars path: a tampered installer must
        // never be left in temp, let alone executed.
        try {
            verifyDownload(setup, info.setupSha256(), UpdateChecker.installRoot(), "installer");
        } catch (IOException e) {
            Files.deleteIfExists(setup);
            throw e;
        }
        progress.accept(100);
        return setup;
    }

    /** Install root-relative path of the portable launcher script. Pure, for tests. */
    static Path launcherScript(Path root) {
        return root.resolve("Play Infinite Conquest.vbs");
    }

    /**
     * Exact command used to relaunch the portable build. Pure, for tests.
     * Two argv entries: no shell quoting is needed even when the install path
     * contains spaces, because ProcessBuilder passes each entry verbatim.
     */
    static List<String> restartCommand(Path root) {
        return List.of("wscript.exe", launcherScript(root).toString());
    }

    /** Diagnostics log next to the pending/ staging dir. Pure, for tests. */
    static Path updateLogPath(Path root) {
        return root.resolve("app").resolve("update.log");
    }

    /**
     * Best-effort append to the update log. Never throws: diagnostics must
     * never break the update itself (e.g. a read-only install dir).
     */
    static void appendUpdateLog(Path root, String message) {
        try {
            Path log = updateLogPath(root);
            Files.createDirectories(log.getParent());
            String line = LocalDateTime.now() + " [updater] " + message
                    + System.lineSeparator();
            Files.writeString(log, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /** Relaunches the portable build through its launcher script and exits. */
    void restartToApply(Path root) throws IOException {
        // NOTE: the launcher is spawned while THIS JVM is still alive. Its
        // .vbs waits for our jar locks to release before swapping pending/
        // into app/ (see the launcher script); do not relaunch javaw
        // directly or the swap is skipped and the old jars keep running.
        List<String> command = restartCommand(root);
        appendUpdateLog(root, "Restarting to apply staged update (build "
                + GameVersion.VERSION + "): command=" + command
                + " cwd=" + root.toAbsolutePath());
        try {
            Process child = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .start();
            appendUpdateLog(root, "Launcher spawned, pid=" + child.pid());
        } catch (IOException e) {
            appendUpdateLog(root, "ERROR spawning launcher: " + e);
            throw e;
        }
        System.exit(0);
    }

    /** Hands off to the NSIS installer and exits so files are free to replace. */
    void handoffToInstaller(Path setupExe) throws IOException {
        Path root = UpdateChecker.installRoot();
        appendUpdateLog(root, "Handing off to installer (build " + GameVersion.VERSION
                + "): command=[" + setupExe + "]"
                + " cwd=" + Path.of(System.getProperty("user.dir")).toAbsolutePath());
        try {
            Process child = new ProcessBuilder(setupExe.toString()).start();
            appendUpdateLog(root, "Installer spawned, pid=" + child.pid());
        } catch (IOException e) {
            appendUpdateLog(root, "ERROR spawning installer: " + e);
            throw e;
        }
        System.exit(0);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
