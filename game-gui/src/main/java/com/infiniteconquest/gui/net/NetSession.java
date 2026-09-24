package com.infiniteconquest.gui.net;

import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.gui.GameSettings;
import com.infiniteconquest.net.DeckDto;
import com.infiniteconquest.net.EmbeddedServer;
import com.infiniteconquest.net.Protocol;

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

    /** Deck sent to the host at lobby time; the host never forwards it. */
    public static DeckDto toDto(DeckBuild deck) {
        return new DeckDto(deck.name(), deck.primaryFaction(), deck.allyFaction(),
                deck.capital().id(), deck.cards().stream().map(CardDefinition::id).toList());
    }

    public boolean isHost() { return host; }
    public int localPlayer() { return localPlayer; }
    public NetClient client() { return client; }
    public List<Protocol.LobbyPlayer> lobbyPlayers() { return lobbyPlayers; }
    public GameSnapshot initialSnapshot() { return initialSnapshot; }

    /** Host only: starts the match once a guest has joined. */
    public void startMatch() {
        if (!host) throw new IllegalStateException("Only the host can start the match");
        client.startMatch();
    }

    /**
     * Hands live server events to the battle frame. If the initial snapshot
     * already arrived, it is replayed immediately.
     */
    public void addBattleListener(NetClient.Listener battle) {
        battleListeners.add(battle);
        GameSnapshot snapshot = initialSnapshot;
        if (snapshot != null) battle.onSnapshot(0, snapshot);
    }

    // --- NetClient.Listener (already on the EDT) ---

    @Override public void onLobby(List<Protocol.LobbyPlayer> players, String hostUuid) {
        lobbyPlayers = List.copyOf(players);
        listener.onLobby(lobbyPlayers);
    }

    @Override public void onSnapshot(long seq, GameSnapshot snapshot) {
        initialSnapshot = snapshot;
        listener.onMatchStarted(snapshot);
    }

    @Override public void onStateUpdate(long seq, String command, String result, int actor,
                                        GameSnapshot snapshot) {
        for (NetClient.Listener battle : battleListeners)
            battle.onStateUpdate(seq, command, result, actor, snapshot);
    }

    @Override public void onGameOver(Integer winner, GameSnapshot snapshot) {
        for (NetClient.Listener battle : battleListeners)
            battle.onGameOver(winner, snapshot);
    }

    @Override public void onError(String message) {
        listener.onError(message);
    }

    @Override public void onDisconnected(String reason) {
        listener.onDisconnected(reason);
    }

    @Override public void close() {
        try { client.close(); } catch (RuntimeException ignored) {}
        if (server != null) server.close();
    }
}
