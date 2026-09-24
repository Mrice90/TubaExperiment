package com.infiniteconquest.net;

import com.infiniteconquest.cli.ActionHints;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Host-authoritative game server embedded in the hosting player's game process.
 *
 * <p>Owns the single authoritative {@link GameState} and runs every command through
 * the same {@link CommandProcessor} as local play. Clients never mutate state
 * optimistically: they render only server-confirmed snapshots. No cloud, VPS, or
 * always-on host is required; the production transport is plain TCP on localhost
 * (the same framing works for later direct-IP play).
 *
 * <p>Session lifecycle: {@code LOBBY} (hello/start) → {@code MULLIGAN} (both
 * players submit a real mulligan decision) → {@code PLAYING} (commands, with
 * reaction windows for the inactive player after eligible actions) →
 * {@code GAME_OVER}. All session state is confined to one single-threaded executor;
 * transports only hand lines to {@link #receive} and are told about disconnects via
 * {@link #peerClosed}. The {@link Peer} interface keeps the session independent of
 * its transport (TCP today, in-process or direct-IP later).
 *
 * <p>Reaction windows: after an eligible active-player action the server inspects
 * the inactive player's legal {@code react ...} options and, when any exist,
 * pauses normal play and prompts only the reacting player. The prompt lists the
 * exact legal command strings; the server honors an answer only by exact match,
 * auto-passes after a generous timeout with a visible notice, and blocks the
 * active player until the window closes.
 */
public final class EmbeddedServer implements AutoCloseable {
    /** Game commands the server accepts; read-only queries are rejected (see SECURITY.md). */
    private static final Set<String> COMMANDS =
            Set.of("play", "burrow", "move", "blink", "attack", "activate", "cast", "end");
    private static final int MAX_LINE = 1_000_000;
    /**
     * How long a reaction window stays open before the server auto-passes.
     * Generous on purpose: the reactor may be reading several spell options.
     */
    static final int REACTION_WINDOW_SECONDS = 60;
    /**
     * Default loopback port for same-machine play. If it is taken the host
     * falls back to an ephemeral port and shows it in the lobby.
     */
    public static final int DEFAULT_PORT = 17431;

    private enum SessionPhase { LOBBY, MULLIGAN, PLAYING, GAME_OVER }

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
    private volatile boolean closed;
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
    private final ActionHints hints = new ActionHints();
    private long seq;
    /** Mulligan decisions received, in seat order; the match starts when both are true. */
    private final boolean[] mulliganDecided = new boolean[2];
    /** Non-null while a reaction window is open for the inactive player. */
    private PendingReaction reaction;
    /** Fires reaction timeouts back onto the game thread; daemon, shut down on close. */
    private final ScheduledExecutorService reactionTimers =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ic-net-reaction-timer");
                t.setDaemon(true);
                return t;
            });

    /** One open reaction window: who must answer and which exact commands are legal. */
    private static final class PendingReaction {
        final int reactingPlayer;
        final List<String> commands;
        ScheduledFuture<?> timeout;

        PendingReaction(int reactingPlayer, List<String> commands) {
            this.reactingPlayer = reactingPlayer;
            this.commands = List.copyOf(commands);
        }
    }

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
                case "mulligan" -> handleMulligan(seat, Protocol.decode(line, Protocol.MulliganDecision.class));
                case "reaction" -> handleReaction(seat, Protocol.decode(line, Protocol.ReactionDecision.class));
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
        // Real networked mulligans: each player decides keep vs. redraw on their
        // own opening hand. The match starts only after both decisions arrive.
        commands = new CommandProcessor(state);
        matchId = java.util.UUID.randomUUID().toString();
        phase = SessionPhase.MULLIGAN;
        mulliganDecided[0] = false;
        mulliganDecided[1] = false;
        seq = 0;
        seq++;
        for (int viewer = 0; viewer < 2; viewer++)
            if (slots[viewer] != null)
                send(slots[viewer].peer, Protocol.encode(
                        new Protocol.MulliganPrompt(seq, matchId, Redactor.redact(state, viewer))));
    }

    private void handleMulligan(Seat seat, Protocol.MulliganDecision decision) {
        if (phase != SessionPhase.MULLIGAN || state == null) {
            sendError(seat.peer, "No mulligan window is open");
            return;
        }
        int player = seat.playerIndex;
        if (player < 0 || player > 1) { sendError(seat.peer, "You are not seated"); return; }
        if (mulliganDecided[player]) { sendError(seat.peer, "Mulligan already submitted"); return; }
        List<UUID> discarded = decision.discardedCardIds() == null ? List.of() : decision.discardedCardIds();
        try {
            // GameState validates: at most 3 IDs, every ID in this player's
            // opening hand, and no duplicate decisions.
            state.mulligan(player, discarded);
        } catch (RuntimeException e) {
            sendError(seat.peer, "Mulligan rejected: " + e.getMessage());
            return;
        }
        mulliganDecided[player] = true;
        seq++;
        broadcast(new Protocol.MulliganUpdate(mulliganDecided[0], mulliganDecided[1]));
        if (mulliganDecided[0] && mulliganDecided[1]) {
            phase = SessionPhase.PLAYING;
            seq++;
            broadcastSnapshot();
        }
    }

    private void handleCommand(Seat seat, String text) {
        if (phase != SessionPhase.PLAYING || state == null) {
            sendError(seat.peer, phase == SessionPhase.MULLIGAN
                    ? "Mulligans are not complete yet" : "No match in progress");
            return;
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;
        String head = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.contains(head)) { sendError(seat.peer, "Unsupported command in online play: " + head); return; }
        PendingReaction pending = reaction;
        if (pending != null) {
            // A reaction window pauses normal play: the reactor answers through
            // the window, and the active player waits for it to close.
            sendError(seat.peer, seat.playerIndex == pending.reactingPlayer
                    ? "Answer your reaction window first" : "Waiting for the opponent's reaction");
            return;
        }
        if (seat.playerIndex != state.activePlayer()) { sendError(seat.peer, "Not your turn"); return; }
        int activeBefore = state.activePlayer();
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
            return;
        }
        // After an eligible active-player action, the inactive player may react.
        // No window opens across a turn boundary ("end") or after a rejected command.
        if (result.startsWith("OK") && state.activePlayer() == activeBefore) maybeOpenReactionWindow();
    }

    /**
     * Opens a reaction window for the inactive player when they have at least
     * one legal {@code react ...} option. The prompt goes only to the reacting
     * player; normal play stays paused until they answer or the window times out.
     */
    private void maybeOpenReactionWindow() {
        if (reaction != null || state.winner().isPresent()) return;
        int reactor = 1 - state.activePlayer();
        List<String> options = hints.spellActionsForPlayer(state, reactor);
        if (options.isEmpty()) return;
        reaction = new PendingReaction(reactor, options);
        seq++;
        Seat seat = slots[reactor];
        if (seat != null)
            send(seat.peer, Protocol.encode(new Protocol.ReactionPrompt(
                    seq, matchId, reactor, options, REACTION_WINDOW_SECONDS)));
        reaction.timeout = reactionTimers.schedule(
                () -> {
                    try {
                        gameThread.submit(this::expireReactionWindow);
                    } catch (java.util.concurrent.RejectedExecutionException ignored) {
                        // Server closed while the timer was in flight; nothing to expire.
                    }
                },
                REACTION_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    private void handleReaction(Seat seat, Protocol.ReactionDecision decision) {
        PendingReaction pending = reaction;
        if (pending == null || phase != SessionPhase.PLAYING) {
            sendError(seat.peer, "No reaction window is open");
            return;
        }
        if (seat.playerIndex != pending.reactingPlayer) {
            sendError(seat.peer, "That reaction window is not yours");
            return;
        }
        String command = decision.command() == null ? "" : decision.command().trim();
        if (!command.isEmpty() && !pending.commands.contains(command)) {
            // Exact-match only: a forged or stale command string is rejected.
            sendError(seat.peer, "Unknown reaction choice");
            return;
        }
        closeReactionWindow();
        String result;
        if (command.isEmpty()) {
            result = "OK: Reaction passed.";
        } else {
            try {
                result = commands.execute(command);
            } catch (RuntimeException e) {
                result = "Invalid command: " + e.getMessage();
            }
        }
        seq++;
        broadcastStateUpdate(command.isEmpty() ? "pass" : command, result, pending.reactingPlayer);
        if (state.winner().isPresent()) {
            phase = SessionPhase.GAME_OVER;
            broadcastGameOver();
        }
        // Deliberately no chained window: one reaction per trigger, like local play.
    }

    /** A reaction window expired with no answer: auto-pass with a visible notice. */
    private void expireReactionWindow() {
        PendingReaction pending = reaction;
        if (pending == null || phase != SessionPhase.PLAYING) return;
        closeReactionWindow();
        seq++;
        broadcast(new Protocol.ReactionTimeout(pending.reactingPlayer));
        seq++;
        broadcastStateUpdate("pass", "OK: Reaction window expired \u2014 auto-passed.", pending.reactingPlayer);
        if (state.winner().isPresent()) {
            phase = SessionPhase.GAME_OVER;
            broadcastGameOver();
        }
    }

    /** Closes the open reaction window, cancelling its timeout. Runs on the game thread. */
    private void closeReactionWindow() {
        PendingReaction pending = reaction;
        reaction = null;
        if (pending != null && pending.timeout != null) pending.timeout.cancel(false);
    }

    private void broadcast(Object envelope) {
        String line = Protocol.encode(envelope);
        for (int viewer = 0; viewer < 2; viewer++)
            if (slots[viewer] != null) send(slots[viewer].peer, line);
    }

    private void handleDisconnect(Peer peer) {
        Seat seat = seats.remove(peer);
        if (seat != null && seat.playerIndex >= 0 && slots[seat.playerIndex] == seat) {
            slots[seat.playerIndex] = null;
            if (seat.playerIndex == 1) guestDeck = null;
        }
        if (phase == SessionPhase.PLAYING || phase == SessionPhase.MULLIGAN) {
            closeReactionWindow();
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
        if (closed) return; // idempotent: tests and the shell may both close
        closed = true;
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        reactionTimers.shutdownNow();
        try {
            // Serialize the reaction cleanup behind any in-flight game work.
            gameThread.submit(this::closeReactionWindow).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.RejectedExecutionException
                | java.util.concurrent.TimeoutException ignored) {
        }
        gameThread.shutdownNow();
        for (Peer peer : seats.keySet().stream().toList()) {
            try { peer.close(); } catch (RuntimeException ignored) {}
        }
        seats.clear();
    }
}
