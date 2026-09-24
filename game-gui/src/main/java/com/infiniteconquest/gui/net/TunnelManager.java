package com.infiniteconquest.gui.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the bundled {@code cloudflared} process for public hosting.
 *
 * <p>When the player hosts a public game, the game spawns
 * {@code cloudflared tunnel --url http://127.0.0.1:<port>} (free, no
 * account), parses the ephemeral {@code https://*.trycloudflare.com} URL
 * from its log output, and hands it to the lobby UI. Guests connect to
 * the {@code wss://} form of that URL; the host's {@code WsBridge} serves
 * the WebSocket endpoint on loopback. The process is killed when the
 * lobby closes, which also retires the tunnel URL.
 */
public final class TunnelManager {
    /** Callbacks for tunnel lifecycle, invoked on a background thread. */
    public interface Listener {
        void onUrl(String httpsUrl);
        void onError(String message);
    }

    private static final Pattern TUNNEL_URL =
            Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");

    /** Timeout waiting for cloudflared to print the tunnel URL. */
    public static final long STARTUP_TIMEOUT_SECONDS = 90;

    private final Listener listener;
    private volatile Process process;
    private volatile BufferedReader activeReader;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean urlFound = new AtomicBoolean();

    public TunnelManager(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Extracts the first {@code https://*.trycloudflare.com} URL from a
     * cloudflared log line, or {@code null} if the line has none. Pure
     * function, unit-tested against real cloudflared output shapes.
     */
    public static String extractTunnelUrl(String line) {
        if (line == null) return null;
        Matcher m = TUNNEL_URL.matcher(line);
        return m.find() ? m.group() : null;
    }

    /** Converts an {@code https://} tunnel URL to its {@code wss://} form for guests. */
    public static String toWssUrl(String httpsUrl) {
        URI uri = URI.create(httpsUrl);
        String scheme = "wss";
        int port = uri.getPort();
        return scheme + "://" + uri.getHost() + (port == -1 ? "" : ":" + port)
                + (uri.getPath() == null ? "" : uri.getPath());
    }

    /**
     * Locates the cloudflared binary: next to the game's jars
     * ({@code app/cloudflared.exe} in the shipped packages), falling back
     * to the system PATH. Returns {@code null} when not found.
     */
    public static File locateBinary() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String name = windows ? "cloudflared.exe" : "cloudflared";
        try {
            URI codeSource = TunnelManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jarDir = Path.of(codeSource).getParent();
            if (jarDir != null) {
                File nextToJars = jarDir.resolve(name).toFile();
                if (nextToJars.isFile() && nextToJars.canExecute()) return nextToJars;
            }
        } catch (Exception ignored) {
        }
        for (String dir : System.getenv("PATH").split(File.pathSeparator)) {
            File candidate = new File(dir, name);
            if (candidate.isFile() && candidate.canExecute()) return candidate;
        }
        return null;
    }

    /**
     * Starts cloudflared forwarding to {@code 127.0.0.1:localPort}.
     * The listener gets {@code onUrl} when the tunnel is up or
     * {@code onError} when it cannot start.
     */
    public void start(int localPort) {
        File binary = locateBinary();
        if (binary == null) {
            listener.onError("cloudflared is missing: reinstall the game or add cloudflared to your PATH.");
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(
                binary.getAbsolutePath(), "tunnel", "--url", "http://127.0.0.1:" + localPort,
                "--no-autoupdate");
        builder.redirectErrorStream(true);
        Thread starter = new Thread(() -> run(binary, builder), "ic-tunnel");
        starter.setDaemon(true);
        starter.start();
    }

    private void run(File binary, ProcessBuilder builder) {
        Process started;
        try {
            started = builder.start();
        } catch (IOException e) {
            listener.onError("Could not start cloudflared: " + e.getMessage());
            return;
        }
        process = started;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
        activeReader = reader;
        // Watchdog: unblock the reader if cloudflared stays silent past the timeout,
        // so the startup wait always terminates.
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(STARTUP_TIMEOUT_SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!urlFound.get() && !stopped.get()) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }, "ic-tunnel-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            String line;
            while (!stopped.get() && (line = reader.readLine()) != null) {
                String url = extractTunnelUrl(line);
                if (url != null) {
                    urlFound.set(true);
                    watchdog.interrupt();
                    listener.onUrl(url);
                    // Keep draining for the life of the tunnel so cloudflared
                    // never blocks on a full stdout pipe. stop() closes the
                    // reader, which ends this loop.
                    drainForever(reader);
                    return;
                }
                if (!started.isAlive()) break;
            }
        } catch (IOException e) {
            if (!stopped.get() && !urlFound.get()) {
                listener.onError("Lost cloudflared output: " + e.getMessage());
                stop();
            }
            return;
        } finally {
            activeReader = null;
            if (!urlFound.get()) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }
        if (!stopped.get() && !urlFound.get()) {
            stop();
            listener.onError("cloudflared did not publish a tunnel URL in time. "
                    + "Check your internet connection and try again.");
        }
    }

    /** Consumes cloudflared's stdout until {@link #stop()} closes the reader. */
    private void drainForever(BufferedReader reader) {
        try {
            while (!stopped.get() && reader.readLine() != null) { /* discard */ }
        } catch (IOException ignored) {
        } finally {
            try { reader.close(); } catch (IOException ignored) {}
        }
    }

    /** Kills the cloudflared process, retiring the tunnel URL. */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            BufferedReader reader = activeReader;
            activeReader = null;
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            Process p = process;
            if (p != null && p.isAlive()) {
                p.destroy();
                try {
                    if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    p.destroyForcibly();
                }
            }
        }
    }
}
