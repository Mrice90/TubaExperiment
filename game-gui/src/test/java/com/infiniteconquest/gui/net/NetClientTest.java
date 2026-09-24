package com.infiniteconquest.gui.net;

import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.core.Zone;
import com.infiniteconquest.net.DeckDto;
import com.infiniteconquest.net.EmbeddedServer;
import com.infiniteconquest.net.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end protocol test without TCP: two NetClients talk to an embedded
 * server through in-memory transports. Covers hello/lobby, start/snapshot,
 * command/state_update, the command allowlist, per-recipient redaction, and
 * game-over/disconnect decoding.
 */
class NetClientTest {
    private EmbeddedServer server;
    private final String hostUuid = UUID.randomUUID().toString();
    private final String guestUuid = UUID.randomUUID().toString();
    private final DemoMatchFactory factory = new DemoMatchFactory();

    private DeckBuild deck(String faction) {
        List<CardDefinition> cards = new FactionDecks(factory.pool()).starter(faction);
        return new DeckBuild(faction + " Test", faction, null,
                factory.capitals().forFaction(faction).get(0), cards);
    }

    private DeckDto dto(DeckBuild build) {
        return NetSession.toDto(build);
    }

    /** Records typed NetClient events; the test thread takes them with timeouts. */
    private static final class Recorder implements NetClient.Listener {
        final BlockingQueue<Object> events = new LinkedBlockingQueue<>();

        record Lobby(List<Protocol.LobbyPlayer> players) {}
        record Snapshot(long seq, String matchId, GameSnapshot snapshot) {}
        record Update(long seq, String command, String result, int actor, GameSnapshot snapshot) {}
        record GameOver(Integer winner, String matchId, GameSnapshot snapshot) {}
        record Error(String message) {}
        record Disconnected(String reason) {}

        @Override public void onLobby(List<Protocol.LobbyPlayer> players, String hostUuid) {
            events.add(new Lobby(players));
        }

        @Override public void onSnapshot(long seq, String matchId, GameSnapshot snapshot) {
            events.add(new Snapshot(seq, matchId, snapshot));
        }

        @Override public void onStateUpdate(long seq, String command, String result, int actor,
                                            GameSnapshot snapshot) {
            events.add(new Update(seq, command, result, actor, snapshot));
        }

        @Override public void onGameOver(Integer winner, String matchId, GameSnapshot snapshot) {
            events.add(new GameOver(winner, matchId, snapshot));
        }

        @Override public void onError(String message) {
            events.add(new Error(message));
        }

        @Override public void onDisconnected(String reason) {
            events.add(new Disconnected(reason));
        }

        @SuppressWarnings("unchecked")
        <T> T next(Class<T> type) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0, "Timed out waiting for " + type.getSimpleName());
                Object event = events.poll(remaining, TimeUnit.NANOSECONDS);
                assertNotNull(event, "Timed out waiting for " + type.getSimpleName());
                if (type.isInstance(event)) return (T) event;
            }
        }
    }

    private MemoryTransport hostTransport;
    private MemoryTransport guestTransport;
    private NetClient hostClient;
    private NetClient guestClient;
    private Recorder hostEvents;
    private Recorder guestEvents;

    @BeforeEach void setUp() {
        server = new EmbeddedServer(hostUuid, "Host", deck("ZEUS"));
        hostTransport = new MemoryTransport(server);
        guestTransport = new MemoryTransport(server);
        hostEvents = new Recorder();
        guestEvents = new Recorder();
        hostClient = new NetClient(hostTransport, Runnable::run, hostEvents);
        guestClient = new NetClient(guestTransport, Runnable::run, guestEvents);
    }

    @AfterEach void tearDown() {
        server.close();
    }

    @Test void lobbyStartCommandAndRedaction() throws Exception {
        hostClient.hello(hostUuid, "Host", null);
        Recorder.Lobby lobby1 = hostEvents.next(Recorder.Lobby.class);
        assertEquals(1, lobby1.players().size());

        guestClient.hello(guestUuid, "Guest", dto(deck("POSEIDON")));
        assertEquals(2, hostEvents.next(Recorder.Lobby.class).players().size());
        assertEquals(2, guestEvents.next(Recorder.Lobby.class).players().size());

        hostClient.startMatch();
        Recorder.Snapshot hostSnapshot = hostEvents.next(Recorder.Snapshot.class);
        Recorder.Snapshot guestSnapshot = guestEvents.next(Recorder.Snapshot.class);
        assertEquals(1, hostSnapshot.snapshot().turnNumber());
        assertEquals(1, guestSnapshot.snapshot().turnNumber());

        // Per-recipient redaction end to end: neither side sees any deck-zone
        // card, and neither sees the opponent's hand cards.
        assertNoDeckCards(hostSnapshot.snapshot());
        assertNoDeckCards(guestSnapshot.snapshot());
        assertTrue(guestSnapshot.snapshot().cards().stream()
                .noneMatch(c -> c.owner() == 0 && c.zone() == Zone.HAND));
        assertTrue(hostSnapshot.snapshot().cards().stream()
                .noneMatch(c -> c.owner() == 1 && c.zone() == Zone.HAND));

        // The active player's client ends the turn; both see the same update.
        int active = hostSnapshot.snapshot().activePlayer();
        NetClient actor = active == 0 ? hostClient : guestClient;
        actor.sendCommand("end");
        Recorder.Update hostUpdate = hostEvents.next(Recorder.Update.class);
        Recorder.Update guestUpdate = guestEvents.next(Recorder.Update.class);
        assertEquals(hostUpdate.seq(), guestUpdate.seq());
        assertEquals("end", hostUpdate.command());
        assertTrue(hostUpdate.result().startsWith("OK"));
        assertEquals(active, hostUpdate.actor());

        // Read-only query commands are rejected by the allowlist.
        actor.sendCommand("board");
        Recorder.Error error = (active == 0 ? hostEvents : guestEvents).next(Recorder.Error.class);
        assertTrue(error.message().contains("Unsupported command"));
    }

    @Test void gameOverAndErrorDecoding() throws Exception {
        hostClient.hello(hostUuid, "Host", null);
        hostEvents.next(Recorder.Lobby.class);
        guestClient.hello(guestUuid, "Guest", dto(deck("POSEIDON")));
        guestEvents.next(Recorder.Lobby.class);

        // Fabricated server lines exercise NetClient decoding directly.
        hostTransport.deliver("{\"type\":\"game_over\",\"winner\":1,\"snapshot\":null}");
        Recorder.GameOver gameOver = hostEvents.next(Recorder.GameOver.class);
        assertEquals(1, gameOver.winner());

        hostTransport.deliver("not json at all");
        Recorder.Error malformed = hostEvents.next(Recorder.Error.class);
        assertTrue(malformed.message().contains("Malformed"));

        hostTransport.deliver("{\"type\":\"bogus\"}");
        Recorder.Error bogus = hostEvents.next(Recorder.Error.class);
        assertTrue(bogus.message().contains("Unexpected message"));
    }

    @Test void disconnectReachesClient() throws Exception {
        hostClient.hello(hostUuid, "Host", null);
        hostEvents.next(Recorder.Lobby.class);
        guestClient.hello(guestUuid, "Guest", dto(deck("POSEIDON")));
        guestEvents.next(Recorder.Lobby.class);
        // The host's server going away surfaces as a disconnect on the client.
        server.close();
        Recorder.Disconnected disconnected = guestEvents.next(Recorder.Disconnected.class);
        assertNotNull(disconnected);
    }

    private static void assertNoDeckCards(GameSnapshot snapshot) {
        assertTrue(snapshot.cards().stream().noneMatch(c -> c.zone() == Zone.DECK),
                "snapshot must not contain deck-zone cards");
        assertTrue(snapshot.deckCounts()[0] > 0 && snapshot.deckCounts()[1] > 0,
                "deck counts must still be present");
    }
}
