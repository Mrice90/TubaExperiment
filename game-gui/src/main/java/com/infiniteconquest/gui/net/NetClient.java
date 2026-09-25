package com.infiniteconquest.gui.net;

import com.infiniteconquest.net.DeckDto;
import com.infiniteconquest.net.Protocol;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Protocol client for online play. Speaks the {@link Protocol} envelopes over a
 * {@link NetTransport} and decodes server messages into typed callbacks.
 * Callbacks are delivered on the given executor (the Swing EDT in the game,
 * a direct executor in tests). The client never mutates game state itself;
 * it only forwards commands and reports server-confirmed snapshots.
 */
public final class NetClient {
    /** Typed server events, delivered on the client's executor. */
    public interface Listener {
        void onLobby(List<Protocol.LobbyPlayer> players, String hostUuid);
        void onSnapshot(long seq, String matchId, com.infiniteconquest.core.GameSnapshot snapshot);
        void onStateUpdate(long seq, String command, String result, int actor,
                           com.infiniteconquest.core.GameSnapshot snapshot);
        void onGameOver(Integer winner, String matchId, com.infiniteconquest.core.GameSnapshot snapshot);
        void onError(String message);
        void onDisconnected(String reason);
        /** Server asks this player for a mulligan decision on the enclosed hand. */
        default void onMulliganPrompt(long seq, String matchId, com.infiniteconquest.core.GameSnapshot snapshot) {}
        /** Mulligan progress in seat order; the match starts when both are true. */
        default void onMulliganUpdate(boolean decided0, boolean decided1) {}
        /**
         * Server opened a reaction window for the local player: answer with one
         * of {@code commands} or pass. {@code expiresInSeconds} is the
         * server-side auto-pass timeout.
         */
        default void onReactionPrompt(long seq, String matchId, int reactingPlayer,
                                      List<String> commands, int expiresInSeconds) {}
        /** A reaction window expired unanswered and was auto-passed. */
        default void onReactionTimeout(int player) {}
        /**
         * The other seat is reacting: show a lightweight waiting indicator
         * and lock spell/attack input until the window resolves. Carries no
         * card data. Only delivered to the non-reacting player.
         */
        default void onReactionWaiting(int reactingPlayer, int secondsLeft) {}
    }

    private final NetTransport transport;
    private final Executor dispatch;
    private final Listener listener;
    private volatile boolean closed;

    public NetClient(NetTransport transport, Executor dispatch, Listener listener) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.listener = Objects.requireNonNull(listener, "listener");
        transport.setListener(new NetTransport.Listener() {
            @Override public void onLine(String line) { handleLine(line); }
            @Override public void onClosed(Throwable cause) {
                if (!closed) dispatch.execute(() -> listener.onDisconnected(
                        cause == null ? "Disconnected" : cause.getMessage()));
            }
        });
    }

    public void hello(String uuid, String name, DeckDto deck) {
        transport.sendEnvelope(new Protocol.Hello(uuid, name, deck));
    }

    public void startMatch() {
        transport.sendEnvelope(new Protocol.StartMatch());
    }

    public void sendCommand(String command) {
        transport.sendEnvelope(new Protocol.PlayerCommand(command));
    }

    public void sendMulligan(java.util.List<java.util.UUID> discardedCardIds) {
        transport.sendEnvelope(new Protocol.MulliganDecision(discardedCardIds));
    }

    /** Answers a reaction window: one of the offered commands, or null/blank to pass. */
    public void sendReaction(String command) {
        transport.sendEnvelope(new Protocol.ReactionDecision(command));
    }

    public void close() {
        closed = true;
        transport.close();
    }

    private void handleLine(String line) {
        String type;
        try {
            type = Protocol.typeOf(line);
        } catch (IllegalArgumentException e) {
            dispatch.execute(() -> listener.onError("Malformed message from host"));
            return;
        }
        try {
            switch (type) {
                case "lobby" -> {
                    Protocol.Lobby lobby = Protocol.decode(line, Protocol.Lobby.class);
                    dispatch.execute(() -> listener.onLobby(lobby.players(), lobby.hostUuid()));
                }
                case "snapshot" -> {
                    Protocol.FullSnapshot full = Protocol.decode(line, Protocol.FullSnapshot.class);
                    dispatch.execute(() -> listener.onSnapshot(full.seq(), full.matchId(), full.snapshot()));
                }
                case "state_update" -> {
                    Protocol.StateUpdate update = Protocol.decode(line, Protocol.StateUpdate.class);
                    dispatch.execute(() -> listener.onStateUpdate(
                            update.seq(), update.command(), update.result(), update.actor(), update.snapshot()));
                }
                case "game_over" -> {
                    Protocol.GameOver over = Protocol.decode(line, Protocol.GameOver.class);
                    dispatch.execute(() -> listener.onGameOver(over.winner(), over.matchId(), over.snapshot()));
                }
                case "error" -> {
                    Protocol.ErrorMessage error = Protocol.decode(line, Protocol.ErrorMessage.class);
                    dispatch.execute(() -> listener.onError(error.message()));
                }
                case "mulligan_prompt" -> {
                    Protocol.MulliganPrompt prompt = Protocol.decode(line, Protocol.MulliganPrompt.class);
                    dispatch.execute(() -> listener.onMulliganPrompt(
                            prompt.seq(), prompt.matchId(), prompt.snapshot()));
                }
                case "mulligan_update" -> {
                    Protocol.MulliganUpdate update = Protocol.decode(line, Protocol.MulliganUpdate.class);
                    dispatch.execute(() -> listener.onMulliganUpdate(update.decided0(), update.decided1()));
                }
                case "reaction_prompt" -> {
                    Protocol.ReactionPrompt prompt = Protocol.decode(line, Protocol.ReactionPrompt.class);
                    dispatch.execute(() -> listener.onReactionPrompt(prompt.seq(), prompt.matchId(),
                            prompt.reactingPlayer(), prompt.commands(), prompt.expiresInSeconds()));
                }
                case "reaction_timeout" -> {
                    Protocol.ReactionTimeout timeout = Protocol.decode(line, Protocol.ReactionTimeout.class);
                    dispatch.execute(() -> listener.onReactionTimeout(timeout.player()));
                }
                case "reaction_waiting" -> {
                    Protocol.ReactionWaiting waiting = Protocol.decode(line, Protocol.ReactionWaiting.class);
                    dispatch.execute(() -> listener.onReactionWaiting(
                            waiting.reactingPlayer(), waiting.secondsLeft()));
                }
                default -> dispatch.execute(() -> listener.onError("Unexpected message: " + type));
            }
        } catch (IllegalArgumentException e) {
            dispatch.execute(() -> listener.onError("Malformed message from host"));
        }
    }
}
