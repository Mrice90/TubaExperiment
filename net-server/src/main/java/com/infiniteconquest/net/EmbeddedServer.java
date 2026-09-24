package com.infiniteconquest.net;

import com.infiniteconquest.cli.CommandProcessor;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.core.BoardGeometry;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Host-authoritative game server embedded in the hosting player's game process.
 *
 * <p>Owns the single authoritative {@link GameState} and runs every command through
 * the same {@link CommandProcessor} as local play. Clients never mutate state
 * optimistically: they render only server-confirmed snapshots. No cloud, VPS, or
 * always-on host is required; the production transport is plain TCP on localhost
 * (the same framing works for later direct-IP play).
 *
 * <p>Session lifecycle: {@code LOBBY} (hello/start) → {@code PLAYING} (commands) →
 * {@code GAME_OVER}. All session state is confined to one single-threaded executor;
 * transports only hand lines to {@link #receive} and are told about disconnects via
 * {@link #peerClosed}. The {@link Peer} interface keeps the session independent of
 * its transport (TCP today, in-process or direct-IP later).
 *
 * <p>Mulligans are auto-kept and reactions auto-pass in net alpha: the server opens
 * no mulligan window and offers no reaction window.
 */
public final class EmbeddedServer implements AutoCloseable {
    /** Game commands the server accepts; read-only queries are rejected (see SECURITY.md). */
    private static final Set<String> COMMANDS =
            Set.of("play", "burrow", "move", "blink", "attack", "activate", "cast", "end");
    private static final int MAX_LINE = 1_000_000;
    /**
     * Default loopback port for same-machine play. If it is taken the host
     * falls back to an ephemeral port and shows it in the lobby.
     */
    public static final int DEFAULT_PORT = 17431;

    private enum SessionPhase { LOBBY, PLAYING, GAME_OVER }

    /**
     * A connected endpoint. The session calls {@link #send} on its game thread;
     * the transport delivers inbound lines via {@link EmbeddedServer#receive} and
     * disconnects via {@link EmbeddedServer#peerClosed}.
     */
    public interface Peer {
        /** Sends one protocol line to this peer. Called on the session thread; must be thread-safe. */
        void send(String line);
        void close();
    }

    private final String hostUuid;
    private final String hostName;
    private final DeckBuild hostDeck;
    private final long seed = new Random().nextLong();
    private final DemoMatchFactory matchFactory = new DemoMatchFactory();
    private final ExecutorService gameThread =
            Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ic-net-game"); t.setDaemon(true); return t; });

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    // All session state below is touched only on gameThread.
    private SessionPhase phase = SessionPhase.LOBBY;
    private final Map<Peer, Seat> seats = new IdentityHashMap<>();
    private final Seat[] slots = new Seat[2];
    private DeckBuild guestDeck;
    private GameState state;
    /** Rating-service match id, generated when a match starts. */
    private String matchId;
    private CommandProcessor commands;
    private long seq;

    private static final class Seat {
        final Peer peer;
        String uuid;
        String name;
        int playerIndex = -1;
        Seat(Peer peer) { this.peer = peer; }
    }

    public EmbeddedServer(String hostUuid, String hostName, DeckBuild hostDeck) {
        this.hostUuid = Objects.requireNonNull(hostUuid, "hostUuid");
        this.hostName = Protocol.sanitizeName(hostName);
        this.hostDeck = Objects.requireNonNull(hostDeck, "hostDeck");
    }

    /** Binds to 127.0.0.1 on an ephemeral port and starts accepting TCP peers. */
    public synchronized void start() throws IOException {
        start(0);
    }

    /**
     * Binds to 127.0.0.1 on the given port (0 = ephemeral) and starts
     * accepting TCP peers.
     */
    public synchronized void start(int port) throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "ic-net-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int port() {
        ServerSocket socket = serverSocket;
        if (socket == null) throw new IllegalStateException("Server is not started");
        return socket.getLocalPort();
    }

    public long seed() { return seed; }

    /**
     * Attaches a non-TCP peer (in-process transports, tests). The peer takes part
     * in the session exactly like a socket peer.
     */
    public void attachPeer(Peer peer) {
        Objects.requireNonNull(peer, "peer");
        gameThread.submit(() -> seats.put(peer, new Seat(peer)));
    }

    /** Called by a transport when one protocol line arrives from a peer. */
    public void receive(Peer peer, String line) {
        if (line == null || line.isBlank()) return;
        if (line.length() > MAX_LINE) {
            gameThread.submit(() -> sendError(peer, "Message too large"));
            return;
        }
        gameThread.submit(() -> handleMessage(peer, line));
    }

    /** Called by a transport when a peer disconnects. */
    public void peerClosed(Peer peer) {
        gameThread.submit(() -> handleDisconnect(peer));
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                SocketPeer peer = new SocketPeer(socket);
                gameThread.submit(() -> seats.put(peer, new Seat(peer)));
                Thread reader = new Thread(() -> readLoop(peer, socket), "ic-net-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void readLoop(SocketPeer peer, Socket socket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) receive(peer, line);
        } catch (IOException ignored) {
        } finally {
            peerClosed(peer);
        }
    }

    private final class SocketPeer implements Peer {
        private final Socket socket;
        private final PrintWriter out;

        SocketPeer(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        @Override public void send(String line) {
            synchronized (out) {
                out.print(line);
                out.print('\n');
                out.flush();
            }
        }

        @Override public void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(Peer peer, String line) {
        Seat seat = seats.get(peer);
        if (seat == null) return;
        String type;
        try {
            type = Protocol.typeOf(line);
        } catch (IllegalArgumentException e) {
            sendError(peer, "Malformed message");
            return;
        }
        try {
            switch (type) {
                case "hello" -> handleHello(seat, Protocol.decode(line, Protocol.Hello.class));
                case "start" -> handleStart(seat);
                case "command" -> handleCommand(seat, Protocol.decode(line, Protocol.PlayerCommand.class).text());
                default -> sendError(peer, "Unexpected message: " + type);
            }
        } catch (IllegalArgumentException e) {
            sendError(peer, "Malformed message");
        }
    }

    private void handleHello(Seat seat, Protocol.Hello hello) {
        if (phase != SessionPhase.LOBBY) { sendError(seat.peer, "Match already started"); return; }
        if (seat.playerIndex != -1) { sendError(seat.peer, "Already joined"); return; }
        if (!Protocol.DATA_VERSION.equals(hello.dataVersion())) {
            sendError(seat.peer, "Version mismatch: this host runs " + Protocol.DATA_VERSION
                    + " but your client sent " + hello.dataVersion()
                    + ". Update the game so both sides match.");
            return;
        }
        String uuid = hello.uuid() == null ? "" : hello.uuid().trim();
        if (uuid.isEmpty()) { sendError(seat.peer, "Missing player id"); return; }
        for (Seat existing : seats.values())
            if (uuid.equals(existing.uuid)) { sendError(seat.peer, "Already joined"); return; }
        int index;
        if (uuid.equals(hostUuid)) {
            if (slots[0] != null) { sendError(seat.peer, "Host seat is taken"); return; }
            index = 0;
        } else {
            if (slots[1] != null) { sendError(seat.peer, "Lobby is full"); return; }
            if (hello.deck() == null) { sendError(seat.peer, "Missing deck list"); return; }
            try {
                guestDeck = resolveDeck(hello.deck());
            } catch (IllegalArgumentException e) {
                sendError(seat.peer, "Deck rejected: " + e.getMessage());
                return;
            }
            index = 1;
        }
        seat.uuid = uuid;
        seat.name = Protocol.sanitizeName(hello.name());
        seat.playerIndex = index;
        slots[index] = seat;
        broadcastLobby();
    }

    private DeckBuild resolveDeck(DeckDto dto) {
        List<String> problems = new ArrayList<>();
        CardDefinition capital = null;
        try {
            capital = matchFactory.capitals().require(dto.capitalId());
        } catch (RuntimeException e) {
            problems.add("Unknown capital: " + dto.capitalId());
        }
        List<CardDefinition> cards = new ArrayList<>();
        if (dto.cardIds() != null) for (String id : dto.cardIds()) {
            try {
                cards.add(matchFactory.pool().require(id));
            } catch (RuntimeException e) {
                problems.add("Unknown card: " + id);
            }
        }
        if (problems.isEmpty())
            problems.addAll(DeckBuild.errors(dto.primaryFaction(), dto.allyFaction(), capital, cards));
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        return new DeckBuild(dto.name(), dto.primaryFaction(), dto.allyFaction(), capital, cards);
    }

    private void handleStart(Seat seat) {
        if (phase != SessionPhase.LOBBY) { sendError(seat.peer, "Match already started"); return; }
        if (seat.playerIndex != 0) { sendError(seat.peer, "Only the host can start the match"); return; }
        if (slots[1] == null || guestDeck == null) { sendError(seat.peer, "Waiting for an opponent to join"); return; }
        try {
            state = matchFactory.create(seed, hostDeck, guestDeck,
                    new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        } catch (RuntimeException e) {
            sendError(seat.peer, "Could not start match: " + e.getMessage());
            return;
        }
        // Net alpha: mulligans are auto-kept, no mulligan window is offered.
        state.mulligan(0, List.of());
        state.mulligan(1, List.of());
        commands = new CommandProcessor(state);
        matchId = java.util.UUID.randomUUID().toString();
        phase = SessionPhase.PLAYING;
        seq = 0;
        broadcastSnapshot();
    }

    private void handleCommand(Seat seat, String text) {
        if (phase != SessionPhase.PLAYING || state == null) { sendError(seat.peer, "No match in progress"); return; }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;
        String head = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(head)) { sendError(seat.peer, "Unsupported command in online play: " + head); return; }
        if (seat.playerIndex != state.activePlayer()) { sendError(seat.peer, "Not your turn"); return; }
        String result;
        try {
            result = commands.execute(trimmed);
        } catch (RuntimeException e) {
            result = "Invalid command: " + e.getMessage();
        }
        seq++;
        broadcastStateUpdate(trimmed, result, seat.playerIndex);
        if (state.winner().isPresent()) {
            phase = SessionPhase.GAME_OVER;
            broadcastGameOver();
        }
    }

    private void handleDisconnect(Peer peer) {
        Seat seat = seats.remove(peer);
        if (seat != null && seat.playerIndex >= 0 && slots[seat.playerIndex] == seat) {
            slots[seat.playerIndex] = null;
            if (seat.playerIndex == 1) guestDeck = null;
        }
        if (phase == SessionPhase.PLAYING) {
            phase = SessionPhase.GAME_OVER;
            int other = seat != null && seat.playerIndex == 0 ? 1 : 0;
            if (slots[other] != null)
                send(slots[other].peer, Protocol.encode(new Protocol.ErrorMessage("Opponent disconnected")));
        } else if (phase == SessionPhase.LOBBY) {
            broadcastLobby();
        }
    }

    private void broadcastLobby() {
        List<Protocol.LobbyPlayer> players = new ArrayList<>();
        for (Seat s : slots) if (s != null) players.add(new Protocol.LobbyPlayer(s.uuid, s.name));
        Protocol.Lobby lobby = new Protocol.Lobby(players, hostUuid);
        for (Seat s : slots) if (s != null) send(s.peer, Protocol.encode(lobby));
    }

    private void broadcastSnapshot() {
        for (int viewer = 0; viewer < 2; viewer++)
            if (slots[viewer] != null)
                send(slots[viewer].peer,
                        Protocol.encode(new Protocol.FullSnapshot(seq, matchId, Redactor.redact(state, viewer))));
    }

    private void broadcastStateUpdate(String command, String result, int actor) {
        for (int viewer = 0; viewer < 2; viewer++)
            if (slots[viewer] != null)
                send(slots[viewer].peer, Protocol.encode(
                        new Protocol.StateUpdate(seq, command, result, actor, Redactor.redact(state, viewer))));
    }

    private void broadcastGameOver() {
        Integer winner = state.winner().isPresent() ? state.winner().getAsInt() : null;
        for (int viewer = 0; viewer < 2; viewer++)
            if (slots[viewer] != null)
                send(slots[viewer].peer, Protocol.encode(
                        new Protocol.GameOver(winner, matchId, Redactor.redact(state, viewer))));
    }

    private void sendError(Peer peer, String message) {
        send(peer, Protocol.encode(new Protocol.ErrorMessage(message)));
    }

    private void send(Peer peer, String line) {
        try {
            peer.send(line);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        gameThread.shutdownNow();
        for (Peer peer : seats.keySet().stream().toList()) {
            try { peer.close(); } catch (RuntimeException ignored) {}
        }
        seats.clear();
    }
}
