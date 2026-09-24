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
import java.time.Duration;
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
            return root;
        } finally {
            Files.deleteIfExists(download);
        }
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
        progress.accept(100);
        return setup;
    }

    /** Relaunches the portable build through its launcher script and exits. */
    void restartToApply(Path root) throws IOException {
        Path launcher = root.resolve("Play Infinite Conquest.vbs");
        new ProcessBuilder("wscript.exe", launcher.toString())
                .directory(root.toFile())
                .start();
        System.exit(0);
    }

    /** Hands off to the NSIS installer and exits so files are free to replace. */
    void handoffToInstaller(Path setupExe) throws IOException {
        new ProcessBuilder(setupExe.toString()).start();
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
