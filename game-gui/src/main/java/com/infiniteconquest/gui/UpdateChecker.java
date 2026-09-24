package com.infiniteconquest.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Talks to the public GitHub releases API to find newer builds. No login needed;
 * the repository is public. Network failures surface as exceptions so callers can
 * stay silent or show a friendly message.
 */
final class UpdateChecker {
    private static final String LATEST_URL =
            "https://api.github.com/repos/Mrice90/TubaExperiment/releases/latest";
    /** Jars-only refresh package published with every release, e.g. InfiniteConquest-Update-0.6.0.zip */
    private static final String UPDATE_ASSET_PREFIX = "InfiniteConquest-Update-";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** What a newer release offers. Null urls mean that package wasn't published. */
    record UpdateInfo(String version, String changelog, String updateZipUrl, long updateZipBytes,
                      String setupUrl) { }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    /** Fetches the latest release and returns its info, or null when it isn't newer. */
    UpdateInfo checkForUpdates() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                .timeout(TIMEOUT)
                .header("User-Agent", "InfiniteConquest-Updater/" + GameVersion.VERSION)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Update server returned HTTP " + response.statusCode());
        }
        JsonNode release = json.readTree(response.body());
        String version = parseTagVersion(release.path("tag_name").asText(""));
        if (version.isEmpty() || !GameVersion.isNewerThan(GameVersion.VERSION, version)) {
            return null;
        }
        String changelog = release.path("body").asText("").trim();
        String updateZipUrl = null;
        long updateZipBytes = 0;
        String setupUrl = null;
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText("");
            String url = asset.path("browser_download_url").asText("");
            if (url.isEmpty()) continue;
            if (name.startsWith(UPDATE_ASSET_PREFIX) && name.endsWith(".zip")) {
                if (isNewerAsset(name, updateZipUrl)) {
                    updateZipUrl = url;
                    updateZipBytes = asset.path("size").asLong(0);
                }
            } else if (name.endsWith("-Setup.exe")) {
                setupUrl = url;
            }
        }
        return new UpdateInfo(version, changelog, updateZipUrl, updateZipBytes, setupUrl);
    }

    /** Prefers the asset whose embedded version is newest. */
    private static boolean isNewerAsset(String name, String currentUrl) {
        if (currentUrl == null) return true;
        return false; // one update asset per release; first one wins
    }

    /** "v0.6.0-alpha" -> "0.6.0". Pure, for tests. */
    static String parseTagVersion(String tag) {
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        int dash = t.indexOf('-');
        if (dash >= 0) t = t.substring(0, dash);
        return t;
    }

    /** Install root: the folder holding app/, jre/, and the launcher script. */
    static Path installRoot() {
        try {
            Path jar = Path.of(GameShell.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path app = jar.getParent();
            if (app != null && app.getParent() != null) return app.getParent();
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /** True for the NSIS-installed build (ships an uninstaller); otherwise portable. */
    static boolean isInstalledBuild(Path root) {
        return Files.exists(root.resolve("Uninstall.exe"));
    }
}
