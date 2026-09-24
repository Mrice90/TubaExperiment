package com.infiniteconquest.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the embedded server over real loopback TCP: lobby, start,
 * per-turn authorization, the command allowlist, and legal/illegal commands.
 * Self-skips where loopback TCP is intercepted (some sandboxes); the same
 * session logic is verified transport-free in {@link LoopbackSessionTest}.
 */
class EmbeddedServerTest {
    private EmbeddedServer server;
    private int port;
    private final String hostUuid = UUID.randomUUID().toString();
    private final String guestUuid = UUID.randomUUID().toString();
    private final DemoMatchFactory factory = new DemoMatchFactory();

    private DeckBuild deck(String faction) {
        List<CardDefinition> cards = new FactionDecks(factory.pool()).starter(faction);
        return new DeckBuild(faction + " Test", faction, null,
                factory.capitals().forFaction(faction).get(0), cards);
    }

    private DeckDto dto(DeckBuild build) {
        return new DeckDto(build.name(), build.primaryFaction(), build.allyFaction(),
                build.capital().id(), build.cards().stream().map(CardDefinition::id).toList());
    }

    @BeforeEach void startServer() throws Exception {
        server = new EmbeddedServer(hostUuid, "Host", deck("ZEUS"));
        server.start();
        port = server.port();
        assumeTrue(TcpProbe.available(port), "Loopback TCP is intercepted in this environment");
    }

    @AfterEach void stopServer() {
        if (server != null) server.close();
    }

    @Test void lobbyStartAndCommandAuthorization() throws Exception {
        try (TestClient host = new TestClient("127.0.0.1", port);
             TestClient guest = new TestClient("127.0.0.1", port)) {

            host.send(new Protocol.Hello(hostUuid, "Host", null));
            JsonNode lobby = host.nextJson("lobby", 3000);
            assertEquals(1, lobby.get("players").size());
            assertEquals("Host", lobby.get("players").get(0).get("name").asText());

            guest.send(new Protocol.Hello(guestUuid, "Guest", dto(deck("POSEIDON"))));
            JsonNode lobby2 = host.nextJson("lobby", 3000);
            assertEquals(2, lobby2.get("players").size());
            // Host holds seat 0, guest seat 1.
            assertEquals(hostUuid, lobby2.get("players").get(0).get("uuid").asText());
            assertEquals(guestUuid, lobby2.get("players").get(1).get("uuid").asText());
            guest.nextOfType("lobby", 3000);

            // Only the host can start.
            guest.send(new Protocol.StartMatch());
            assertTrue(guest.nextOfType("error", 3000).contains("Only the host"));

            host.send(new Protocol.StartMatch());
            // Real networked mulligans: both players are prompted, decide, and
            // only then does the match start.
            host.nextOfType("mulligan_prompt", 5000);
            guest.nextOfType("mulligan_prompt", 5000);
            host.send(new Protocol.MulliganDecision(List.of()));
            JsonNode update1 = host.nextJson("mulligan_update", 3000);
            assertTrue(update1.get("decided0").asBoolean());
            assertFalse(update1.get("decided1").asBoolean());
            guest.send(new Protocol.MulliganDecision(List.of()));
            JsonNode update2 = host.nextJson("mulligan_update", 3000);
            assertTrue(update2.get("decided0").asBoolean());
            assertTrue(update2.get("decided1").asBoolean());
            JsonNode hostSnap = host.nextJson("snapshot", 5000);
            JsonNode guestSnap = guest.nextJson("snapshot", 5000);
            assertEquals(0, hostSnap.get("snapshot").get("viewingPlayer").asInt());
            assertEquals(1, guestSnap.get("snapshot").get("viewingPlayer").asInt());
            int active = hostSnap.get("snapshot").get("activePlayer").asInt();
            TestClient activeClient = active == 0 ? host : guest;
            TestClient idleClient = active == 0 ? guest : host;

            // Idle player may not act.
            idleClient.send(new Protocol.PlayerCommand("end"));
            assertTrue(idleClient.nextOfType("error", 3000).contains("Not your turn"));

            // Read-only queries are rejected: they can leak hidden zones.
            activeClient.send(new Protocol.PlayerCommand("board"));
            assertTrue(activeClient.nextOfType("error", 3000).contains("Unsupported command"));

            // Illegal game command: server rejects, state still broadcasts.
            activeClient.send(new Protocol.PlayerCommand("play 99 0 0"));
            JsonNode rejected = activeClient.nextJson("state_update", 3000);
            String result = rejected.get("result").asText();
            assertTrue(result.startsWith("Invalid command") || result.startsWith("REJECTED"),
                    "unexpected result: " + result);
            assertEquals("play 99 0 0", rejected.get("command").asText());
            assertEquals(active, rejected.get("actor").asInt());
            // The idle client saw the same update.
            assertEquals(rejected.get("seq").asLong(),
                    idleClient.nextJson("state_update", 3000).get("seq").asLong());

            // Legal command advances the game.
            int turnBefore = rejected.get("snapshot").get("turnNumber").asInt();
            activeClient.send(new Protocol.PlayerCommand("end"));
            JsonNode advanced = activeClient.nextJson("state_update", 3000);
            assertTrue(advanced.get("result").asText().startsWith("OK"));
            assertEquals(turnBefore + 1, advanced.get("snapshot").get("turnNumber").asInt());
            assertEquals(1 - active, advanced.get("snapshot").get("activePlayer").asInt());
        }
    }

    @Test void badDeckIsRejectedAtHello() throws Exception {
        try (TestClient guest = new TestClient("127.0.0.1", port)) {
            DeckDto bad = new DeckDto("Bad", "ZEUS", null, "no_such_capital", List.of("no_such_card"));
            guest.send(new Protocol.Hello(guestUuid, "Guest", bad));
            assertTrue(guest.nextOfType("error", 3000).contains("Deck rejected"));
        }
    }

    @Test void duplicateSeatIsRejected() throws Exception {
        try (TestClient host = new TestClient("127.0.0.1", port);
             TestClient intruder = new TestClient("127.0.0.1", port)) {
            host.send(new Protocol.Hello(hostUuid, "Host", null));
            host.nextOfType("lobby", 3000);
            intruder.send(new Protocol.Hello(hostUuid, "Impostor", null));
            assertTrue(intruder.nextOfType("error", 3000).contains("Already joined"));
        }
    }

    @Test void versionMismatchIsRejectedWithClearError() throws Exception {
        try (TestClient guest = new TestClient("127.0.0.1", port)) {
            guest.send(new Protocol.Hello("hello", guestUuid, "Guest",
                    dto(deck("POSEIDON")), "ic-net-0"));
            assertTrue(guest.nextOfType("error", 3000).contains("Version mismatch"));
        }
    }

    @Test void legacyHelloWithoutVersionIsRejectedWithClearError() throws Exception {
        try (TestClient guest = new TestClient("127.0.0.1", port)) {
            // Hand-written JSON predating the dataVersion field: decodes to
            // "unknown" and must be rejected, not silently accepted.
            guest.sendRaw("{\"type\":\"hello\",\"uuid\":\"" + guestUuid
                    + "\",\"name\":\"Guest\",\"deck\":null,\"dataVersion\":null}");
            assertTrue(guest.nextOfType("error", 3000).contains("Version mismatch"));
        }
    }
}
