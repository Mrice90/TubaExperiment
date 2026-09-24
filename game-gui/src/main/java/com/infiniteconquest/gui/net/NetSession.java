package com.infiniteconquest.gui.net;

import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.gui.GameSettings;
import com.infiniteconquest.net.DeckDto;
import com.infiniteconquest.net.EmbeddedServer;
import com.infiniteconquest.net.Protocol;
import com.infiniteconquest.net.WsBridge;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

/**
 * One online session from the shell's point of view. The host runs an
 * {@link EmbeddedServer} in-process and connects to it over loopback TCP,
 * exactly like a guest would; the guest connects to the host's address.
 * Lobby and match-start events go to the shell {@link Listener} on the EDT;
 * once the battle frame opens it registers a {@link NetClient.Listener} for
 * snapshots, updates, and game over.
 */
public final class NetSession implements NetClient.Listener, AutoCloseable {
    /** Shell-facing session events, delivered on the EDT. */
    public interface Listener {
        void onLobby(List<Protocol.LobbyPlayer> players);
        void onMatchStarted(GameSnapshot snapshot);
        /** The server is asking the local player for a mulligan decision. */
        default void onMulliganPrompt(GameSnapshot snapshot) {}
        void onError(String message);
        void onDisconnected(String reason);
    }

    private final boolean host;
    private final GameSettings settings;
    private final EmbeddedServer server; // host only
    private final NetClient client;
    private final int localPlayer;
    private final Listener listener;
    private final List<NetClient.Listener> battleListeners = new CopyOnWriteArrayList<>();
    private volatile List<Protocol.LobbyPlayer> lobbyPlayers = List.of();
    private volatile GameSnapshot initialSnapshot;
    private volatile GameSnapshot mulliganSnapshot;
    private volatile String matchId;
    // Tunnel hosting resources (hostWithTunnel only).
    private volatile WsBridge bridge;
    private volatile TunnelManager tunnel;
    private volatile String wssUrl;
    private volatile LobbyLease lobbyLease;

    private NetSession(boolean host, GameSettings settings, EmbeddedServer server,
                       NetTransport transport, int localPlayer, Listener listener) {
        this.host = host;
        this.settings = settings;
        this.server = server;
        this.localPlayer = localPlayer;
        this.listener = listener;
        this.client = new NetClient(transport, SwingUtilities::invokeLater, this);
    }

    /** Starts an embedded server and connects the host client over loopback TCP. */
    public static NetSession host(GameSettings settings, DeckBuild deck, DemoMatchFactory factory,
                                  Listener listener) throws IOException {
        Objects.requireNonNull(deck, "deck");
        EmbeddedServer server = new EmbeddedServer(settings.playerUuid, settings.playerName, deck);
        try {
            server.start(EmbeddedServer.DEFAULT_PORT);
        } catch (IOException e) {
            server.start(0); // default port taken: fall back to ephemeral
        }
        TcpNetTransport transport = new TcpNetTransport("127.0.0.1", server.port());
        transport.connect();
        NetSession session = new NetSession(true, settings, server, transport, 0, listener);
        session.client.hello(settings.playerUuid, settings.playerName, null);
        return session;
    }

    /** The TCP port the embedded server (host only) is listening on. */
    public int hostPort() {
        if (server == null) throw new IllegalStateException("Only the host runs a server");
        return server.port();
    }

    /** Connects to a host and joins the lobby with the player's deck. */
    public static NetSession join(GameSettings settings, DeckBuild deck, String address, int port,
                                  Listener listener) throws IOException {
        Objects.requireNonNull(deck, "deck");
        TcpNetTransport transport = new TcpNetTransport(address, port);
        transport.connect();
        NetSession session = new NetSession(false, settings, null, transport, 1, listener);
        session.client.hello(settings.playerUuid, settings.playerName, toDto(deck));
        return session;
    }

    /**
     * Joins a host through a cloudflared tunnel URL
     * ({@code wss://*.trycloudflare.com}). The tunnel terminates TLS at
     * Cloudflare's edge; neither player learns the other's IP.
     */
    public static NetSession joinViaTunnel(GameSettings settings, DeckBuild deck, String wssUrl,
                                           Listener listener) throws IOException {
        Objects.requireNonNull(deck, "deck");
        WsNetTransport transport = new WsNetTransport(wssUrl);
        transport.connect();
        try {
            transport.awaitReady(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException | InterruptedException e) {
            transport.close();
            throw new IOException("Timed out opening the tunnel connection", e);
        }
        NetSession session = new NetSession(false, settings, null, transport, 1, listener);
        session.client.hello(settings.playerUuid, settings.playerName, toDto(deck));
        return session;
    }

    /** Deck sent to the host at lobby time; the host never forwards it. */
    public static DeckDto toDto(DeckBuild deck) {
        return new DeckDto(deck.name(), deck.primaryFaction(), deck.allyFaction(),
                deck.capital().id(), deck.cards().stream().map(CardDefinition::id).toList());
    }

    /**
     * Starts an embedded server plus a cloudflared tunnel, and optionally
     * registers a public lobby. The host's own client connects over loopback
     * TCP; guests arrive through {@code wss://*.trycloudflare.com} via the
     * WebSocket bridge. {@link #tunnelUrl()} and {@link #lobbyCode()} are
     * available once this returns.
     *
     * @param lobby   lobby service, or null to host without a public listing
     *                (direct tunnel URL sharing or quick-match hosting)
     * @param publish when true, register a public lobby (requires {@code lobby})
     */
    public static NetSession hostWithTunnel(GameSettings settings, DeckBuild deck,
                                            DemoMatchFactory factory, LobbyService lobby,
                                            boolean publish, Listener listener) throws IOException {
        Objects.requireNonNull(deck, "deck");
        EmbeddedServer server = new EmbeddedServer(settings.playerUuid, settings.playerName, deck);
        try {
            server.start(EmbeddedServer.DEFAULT_PORT);
        } catch (IOException e) {
            server.start(0);
        }
        WsBridge bridge;
        try {
            bridge = WsBridge.listen(0, "127.0.0.1", server.port());
        } catch (IOException e) {
            server.close();
            throw new IOException("Could not start the tunnel bridge", e);
        }
        java.util.concurrent.CountDownLatch urlLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> urlRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> errorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        TunnelManager tunnel = new TunnelManager(new TunnelManager.Listener() {
            @Override public void onUrl(String httpsUrl) {
                urlRef.set(httpsUrl);
                urlLatch.countDown();
            }

            @Override public void onError(String message) {
                errorRef.set(message);
                urlLatch.countDown();
            }
        });
        tunnel.start(bridge.port());
        String wssUrl;
        try {
            boolean settled = urlLatch.await(TunnelManager.STARTUP_TIMEOUT_SECONDS + 10,
                    java.util.concurrent.TimeUnit.SECONDS);
            String httpsUrl = urlRef.get();
            if (!settled || httpsUrl == null) {
                tunnel.stop();
                bridge.close();
                server.close();
                String detail = errorRef.get();
                throw new IOException("Tunnel failed: "
                        + (detail != null ? detail : "timed out waiting for cloudflared"));
            }
            wssUrl = TunnelManager.toWssUrl(httpsUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tunnel.stop();
            try { bridge.close(); } catch (Exception ignored) {}
            server.close();
            throw new IOException("Interrupted while starting the tunnel", e);
        }
        LobbyLease lease = null;
        if (lobby != null && publish) {
            try {
                lease = LobbyLease.register(lobby, settings, wssUrl);
            } catch (RuntimeException e) {
                tunnel.stop();
                try { bridge.close(); } catch (Exception ignored) {}
                server.close();
                throw new IOException("Could not register the public lobby", e);
            }
        }
        TcpNetTransport transport = new TcpNetTransport("127.0.0.1", server.port());
        transport.connect();
        NetSession session = new NetSession(true, settings, server, transport, 0, listener);
        session.bridge = bridge;
        session.tunnel = tunnel;
        session.wssUrl = wssUrl;
        session.lobbyLease = lease;
        session.client.hello(settings.playerUuid, settings.playerName, null);
        return session;
    }

    /** Tunnel URL guests use to join (host with tunnel only; null otherwise). */
    public String tunnelUrl() { return wssUrl; }

    /** Public lobby code (host with public lobby only; null otherwise). */
    public String lobbyCode() {
        return lobbyLease == null ? null : lobbyLease.code();
    }

    /**
     * Registers a quick-match pairing for the guest and stops advertising
     * (removes any public lobby). The guest's queue poll will report "ready".
     */
    public void publishQuickMatchPairing(LobbyService lobby, String guestUuid) throws IOException {
        if (!host) throw new IllegalStateException("Only the host can publish a pairing");
        Objects.requireNonNull(lobby, "lobby");
        String code = lobbyCode();
        if (lobbyLease != null) {
            lobbyLease.close();
            lobbyLease = null;
        }
        lobby.publishPairing(settings.playerUuid, guestUuid, wssUrl, code == null ? "" : code,
                settings.playerName, currentRating());
    }

    /** The host's rating for matchmaking display; defaults to 1000 offline. */
    public int currentRating() {
        return Math.max(0, settings.playerRating);
    }

    public boolean isHost() { return host; }
    public int localPlayer() { return localPlayer; }
    public NetClient client() { return client; }
    public List<Protocol.LobbyPlayer> lobbyPlayers() { return lobbyPlayers; }
    public GameSnapshot initialSnapshot() { return initialSnapshot; }
    /** Rating-service match id from the host's match-start snapshot (null before start). */
    public String matchId() { return matchId; }

    /** Host only: starts the match once a guest has joined. */
    public void startMatch() {
        if (!host) throw new IllegalStateException("Only the host can start the match");
        client.startMatch();
    }

    /** Submits this player's mulligan decision: up to 3 of their own opening-hand IDs. */
    public void sendMulligan(java.util.List<java.util.UUID> discardedCardIds) {
        client.sendMulligan(discardedCardIds);
    }

    /** Answers a reaction window: one of the offered commands, or null to pass. */
    public void sendReaction(String command) {
        client.sendReaction(command);
    }

    /**
     * Hands live server events to the battle frame. If the initial snapshot
     * already arrived, it is replayed immediately; otherwise a pending mulligan
     * prompt is replayed so a late-opening battle can still decide.
     */
    public void addBattleListener(NetClient.Listener battle) {
        battleListeners.add(battle);
        GameSnapshot snapshot = initialSnapshot;
        if (snapshot != null) {
            battle.onSnapshot(0, matchId, snapshot);
            return;
        }
        GameSnapshot mulligan = mulliganSnapshot;
        if (mulligan != null) battle.onMulliganPrompt(0, matchId, mulligan);
    }

    // --- NetClient.Listener (already on the EDT) ---

    @Override public void onLobby(List<Protocol.LobbyPlayer> players, String hostUuid) {
        lobbyPlayers = List.copyOf(players);
        listener.onLobby(lobbyPlayers);
    }

    @Override public void onSnapshot(long seq, String matchId, GameSnapshot snapshot) {
        initialSnapshot = snapshot;
        this.matchId = matchId;
        listener.onMatchStarted(snapshot);
    }

    @Override public void onMulliganPrompt(long seq, String matchId, GameSnapshot snapshot) {
        mulliganSnapshot = snapshot;
        if (matchId != null) this.matchId = matchId;
        listener.onMulliganPrompt(snapshot);
    }

    @Override public void onMulliganUpdate(boolean decided0, boolean decided1) {
        for (NetClient.Listener battle : battleListeners)
            battle.onMulliganUpdate(decided0, decided1);
    }

    @Override public void onReactionPrompt(long seq, String matchId, int reactingPlayer,
                                           List<String> commands, int expiresInSeconds) {
        for (NetClient.Listener battle : battleListeners)
            battle.onReactionPrompt(seq, matchId, reactingPlayer, commands, expiresInSeconds);
    }

    @Override public void onReactionTimeout(int player) {
        for (NetClient.Listener battle : battleListeners)
            battle.onReactionTimeout(player);
    }

    @Override public void onStateUpdate(long seq, String command, String result, int actor,
                                        GameSnapshot snapshot) {
        for (NetClient.Listener battle : battleListeners)
            battle.onStateUpdate(seq, command, result, actor, snapshot);
    }

    @Override public void onGameOver(Integer winner, String matchId, GameSnapshot snapshot) {
        if (matchId != null) this.matchId = matchId;
        for (NetClient.Listener battle : battleListeners)
            battle.onGameOver(winner, this.matchId, snapshot);
    }

    @Override public void onError(String message) {
        listener.onError(message);
    }

    @Override public void onDisconnected(String reason) {
        listener.onDisconnected(reason);
    }

    @Override public void close() {
        try { client.close(); } catch (RuntimeException ignored) {}
        LobbyLease lease = lobbyLease;
        lobbyLease = null;
        if (lease != null) lease.close(); // stops heartbeat, unregisters lobby (best-effort)
        TunnelManager tm = tunnel;
        tunnel = null;
        if (tm != null) tm.stop();
        WsBridge b = bridge;
        bridge = null;
        if (b != null) {
            try { b.close(); } catch (Exception ignored) {}
        }
        if (server != null) server.close();
    }
}
