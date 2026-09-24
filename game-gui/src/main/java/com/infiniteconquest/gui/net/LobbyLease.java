package com.infiniteconquest.gui.net;

import com.infiniteconquest.gui.GameSettings;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A registered public lobby with a heartbeat. Lobby entries expire after
 * ~2 minutes on the service, so this re-registers every 90 seconds while
 * the host is up. Closing stops the heartbeat and deletes the entry
 * (best-effort; the entry would expire on its own anyway).
 */
final class LobbyLease implements AutoCloseable {
    /** Re-register interval; lobby entries expire after 120s server-side. */
    private static final long HEARTBEAT_SECONDS = 90;

    private final LobbyService service;
    private final String code;
    private final String hostUuid;
    private final ScheduledExecutorService heartbeat;
    private final ScheduledFuture<?> heartbeatTask;
    private volatile boolean closed;

    private LobbyLease(LobbyService service, String code, String hostUuid,
                       ScheduledExecutorService heartbeat, ScheduledFuture<?> heartbeatTask) {
        this.service = service;
        this.code = code;
        this.hostUuid = hostUuid;
        this.heartbeat = heartbeat;
        this.heartbeatTask = heartbeatTask;
    }

    /**
     * Registers a lobby and starts the heartbeat. Returns the lease with the
     * service-assigned code; if the caller passed a hint code the service may
     * reuse it.
     */
    static LobbyLease register(LobbyService service, GameSettings settings, String wssUrl) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(settings, "settings");
        try {
            String code = service.registerLobby(null, settings.playerUuid, settings.playerName,
                    settings.playerRating, wssUrl);
            ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ic-lobby-heartbeat");
                t.setDaemon(true);
                return t;
            });
            ScheduledFuture<?> task = heartbeat.scheduleWithFixedDelay(() -> {
                try {
                    service.registerLobby(code, settings.playerUuid, settings.playerName,
                            settings.playerRating, wssUrl);
                } catch (Exception ignored) {
                    // Transient network failure: the entry expires server-side,
                    // so the worst case is a briefly stale listing.
                }
            }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            return new LobbyLease(service, code, settings.playerUuid, heartbeat, task);
        } catch (Exception e) {
            throw new RuntimeException("Could not register the public lobby", e);
        }
    }

    /** The join code guests enter (or find in the lobby browser). */
    String code() { return code; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (heartbeat != null) heartbeat.shutdownNow();
        // Best-effort unregister on a daemon thread: never block the caller.
        Thread unregister = new Thread(() -> {
            try {
                service.deleteLobby(code, hostUuid);
            } catch (Exception ignored) {}
        }, "ic-lobby-unregister");
        unregister.setDaemon(true);
        unregister.start();
    }
}
