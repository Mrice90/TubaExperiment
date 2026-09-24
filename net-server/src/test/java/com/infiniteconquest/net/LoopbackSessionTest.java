package com.infiniteconquest.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.infiniteconquest.cli.ActionHints;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.core.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full loopback session: a host and a guest client play several turns against
 * the embedded server through in-memory peers, including an attack and end
 * turns. The "players" are simple hint-driven drivers that rebuild the
 * authoritative state from each redacted snapshot, exactly like the GUI does.
 * The TCP transport itself is covered by {@link EmbeddedServerTest} where
 * loopback TCP is available.
 */
class LoopbackSessionTest {
    private EmbeddedServer server;
    private final String hostUuid = UUID.randomUUID().toString();
    private final String guestUuid = UUID.randomUUID().toString();
    private final DemoMatchFactory factory = new DemoMatchFactory();
    private Function<String, CardDefinition> definitions;

    private DeckBuild deck(String faction) {
        List<CardDefinition> cards = new FactionDecks(factory.pool()).starter(faction);
        return new DeckBuild(faction + " Test", faction, null,
                factory.capitals().forFaction(faction).get(0), cards);
    }

    private DeckDto dto(DeckBuild build) {
        return new DeckDto(build.name(), build.primaryFaction(), build.allyFaction(),
                build.capital().id(), build.cards().stream().map(CardDefinition::id).toList());
    }

    @BeforeEach void startServer() {
        server = new EmbeddedServer(hostUuid, "Host", deck("ZEUS"));
        definitions = id -> {
            try { return factory.pool().require(id); }
            catch (RuntimeException e) {
                try { return factory.capitals().require(id); }
                catch (RuntimeException e2) { return null; }
            }
        };
    }

    @AfterEach void stopServer() {
        if (server != null) server.close();
    }

    /** In-memory driver standing in for a network client. */
    private final class Driver {
        final TestPeer peer = new TestPeer();

        Driver() {
            server.attachPeer(peer);
        }

        void send(Object envelope) {
            server.receive(peer, Protocol.encode(envelope));
        }

        JsonNode nextOfType(String type, long timeoutMs) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new AssertionError("Timed out waiting for " + type);
                String line = peer.next(remaining, TimeUnit.NANOSECONDS);
                if (line == null) throw new AssertionError("Timed out waiting for " + type);
                JsonNode node = Protocol.MAPPER.readTree(line);
                if (type.equals(node.path("type").asText())) return node;
            }
        }
    }

    @Test void scriptedSessionWithAttackAndEndTurns() throws Exception {
        Driver host = new Driver();
        Driver guest = new Driver();

        host.send(new Protocol.Hello(hostUuid, "Host", null));
        host.nextOfType("lobby", 3000);
        guest.send(new Protocol.Hello(guestUuid, "Guest", dto(deck("POSEIDON"))));
        host.nextOfType("lobby", 3000);
        guest.nextOfType("lobby", 3000);

        host.send(new Protocol.StartMatch());
        GameSnapshot hostView = snapshotOf(host.nextOfType("snapshot", 5000));
        GameSnapshot guestView = snapshotOf(guest.nextOfType("snapshot", 5000));
        assertEquals(1, hostView.turnNumber());

        ActionHints hints = new ActionHints();
        GameEngine engine = new GameEngine();
        int attacksAccepted = 0;
        int endsAccepted = 0;
        int turnsAdvanced = 0;
        boolean gameOver = false;

        for (int step = 0; step < 60 && !gameOver; step++) {
            int active = hostView.activePlayer();
            GameSnapshot view = active == 0 ? hostView : guestView;
            Driver actor = active == 0 ? host : guest;
            GameState state = GameState.fromSnapshot(view, definitions);
            String command = chooseCommand(hints.forActivePlayer(state, engine), active);
            actor.send(new Protocol.PlayerCommand(command));

            JsonNode updateHost = host.nextOfType("state_update", 5000);
            JsonNode updateGuest = guest.nextOfType("state_update", 5000);
            assertEquals(updateHost.get("seq").asLong(), updateGuest.get("seq").asLong(),
                    "both clients must see the same update");
            String result = updateHost.get("result").asText();
            assertEquals(command, updateHost.get("command").asText());
            assertEquals(active, updateHost.get("actor").asInt());

            if (command.startsWith("attack") && result.startsWith("OK")) attacksAccepted++;
            if (command.equals("end") && result.startsWith("OK")) {
                endsAccepted++;
                turnsAdvanced++;
            }
            hostView = snapshotOf(updateHost);
            guestView = snapshotOf(updateGuest);

            // Game over can arrive right after a killing blow.
            try {
                host.nextOfType("game_over", 50);
                guest.nextOfType("game_over", 3000);
                gameOver = true;
            } catch (AssertionError ignored) {
            }
            if (turnsAdvanced >= 6 && attacksAccepted >= 1) break;
        }

        assertTrue(turnsAdvanced >= 6, "expected several turns, got " + turnsAdvanced);
        assertTrue(attacksAccepted >= 1, "expected at least one accepted attack");
        assertTrue(endsAccepted >= 1, "expected end turns");
        assertEquals(hostView.turnNumber(), guestView.turnNumber(), "clients must agree on turn");
        assertEquals(hostView.activePlayer(), guestView.activePlayer(), "clients must agree on active player");
    }

    /** Prefers attacks, then advancing moves, then plays, then anything but end. */
    private String chooseCommand(List<String> hints, int active) {
        List<String> attacks = hints.stream().filter(h -> h.startsWith("attack")).toList();
        if (!attacks.isEmpty()) return attacks.get(0);
        List<String> moves = hints.stream().filter(h -> h.startsWith("move")).toList();
        if (!moves.isEmpty()) {
            // March toward the enemy capital: highest y for player 0, lowest for player 1.
            Comparator<String> byY = Comparator.comparingInt(h -> Integer.parseInt(h.split(" ")[4]));
            if (active == 0) byY = byY.reversed();
            return moves.stream().min(byY).orElse(moves.get(0));
        }
        List<String> plays = hints.stream().filter(h -> h.startsWith("play")).toList();
        if (!plays.isEmpty()) {
            Comparator<String> byY = Comparator.comparingInt(h -> Integer.parseInt(h.split(" ")[3]));
            if (active == 0) byY = byY.reversed();
            return plays.stream().min(byY).orElse(plays.get(0));
        }
        return hints.stream().filter(h -> !h.equals("end")).findFirst().orElse("end");
    }

    private GameSnapshot snapshotOf(JsonNode envelope) throws Exception {
        return Protocol.MAPPER.treeToValue(envelope.get("snapshot"), GameSnapshot.class);
    }
}
