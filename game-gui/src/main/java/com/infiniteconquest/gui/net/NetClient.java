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
        void onSnapshot(long seq, com.infiniteconquest.core.GameSnapshot snapshot);
        void onStateUpdate(long seq, String command, String result, int actor,
                           com.infiniteconquest.core.GameSnapshot snapshot);
        void onGameOver(Integer winner, com.infiniteconquest.core.GameSnapshot snapshot);
        void onError(String message);
        void onDisconnected(String reason);
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
                    dispatch.execute(() -> listener.onSnapshot(full.seq(), full.snapshot()));
                }
                case "state_update" -> {
                    Protocol.StateUpdate update = Protocol.decode(line, Protocol.StateUpdate.class);
                    dispatch.execute(() -> listener.onStateUpdate(
                            update.seq(), update.command(), update.result(), update.actor(), update.snapshot()));
                }
                case "game_over" -> {
                    Protocol.GameOver over = Protocol.decode(line, Protocol.GameOver.class);
                    dispatch.execute(() -> listener.onGameOver(over.winner(), over.snapshot()));
                }
                case "error" -> {
                    Protocol.ErrorMessage error = Protocol.decode(line, Protocol.ErrorMessage.class);
                    dispatch.execute(() -> listener.onError(error.message()));
                }
                default -> dispatch.execute(() -> listener.onError("Unexpected message: " + type));
            }
        } catch (IllegalArgumentException e) {
            dispatch.execute(() -> listener.onError("Malformed message from host"));
        }
    }
}
