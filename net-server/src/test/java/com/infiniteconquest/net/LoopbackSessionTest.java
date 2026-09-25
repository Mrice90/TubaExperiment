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
import com.infiniteconquest.core.Zone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        // Both players answer the real mulligan prompt before the match starts.
        host.nextOfType("mulligan_prompt", 5000);
        guest.nextOfType("mulligan_prompt", 5000);
        host.send(new Protocol.MulliganDecision(List.of()));
        guest.send(new Protocol.MulliganDecision(List.of()));
        host.nextOfType("mulligan_update", 3000);
        host.nextOfType("mulligan_update", 3000);
        guest.nextOfType("mulligan_update", 3000);
        guest.nextOfType("mulligan_update", 3000);
        GameSnapshot hostView = snapshotOf(host.nextOfType("snapshot", 5000));
        GameSnapshot guestView = snapshotOf(guest.nextOfType("snapshot", 5000));
        assertEquals(1, hostView.turnNumber());
        assertFalse(hostView.mulliganOpen(), "match snapshot must not be flagged as mulligan");

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

    /**
     * Scripted two-client mulligan: the host keeps, the guest discards two
     * cards. Both clients see progress updates, then the match starts legally:
     * the keeper's hand is untouched, the redrawer's discards are replaced,
     * and both views agree the game is live.
     */
    @Test void scriptedTwoClientMulliganOneKeepsOneRedraws() throws Exception {
        Driver host = new Driver();
        Driver guest = new Driver();

        host.send(new Protocol.Hello(hostUuid, "Host", null));
        host.nextOfType("lobby", 3000);
        guest.send(new Protocol.Hello(guestUuid, "Guest", dto(deck("POSEIDON"))));
        host.nextOfType("lobby", 3000);
        guest.nextOfType("lobby", 3000);

        host.send(new Protocol.StartMatch());
        GameSnapshot hostPre = snapshotOf(host.nextOfType("mulligan_prompt", 5000));
        GameSnapshot guestPre = snapshotOf(guest.nextOfType("mulligan_prompt", 5000));
        assertTrue(hostPre.mulliganOpen() && guestPre.mulliganOpen());

        // Player B's prompt must not leak player A's hand IDs (privacy check
        // through the real wire path, mirroring RedactorTest).
        Set<UUID> aHand = new HashSet<>(handIds(hostPre, 0));
        for (GameSnapshot.CardView card : guestPre.cards())
            assertFalse(aHand.contains(card.instanceId()), "player A hand id leaked to player B");

        List<UUID> guestDiscard = handIds(guestPre, 1).subList(0, 2);
        host.send(new Protocol.MulliganDecision(List.of()));
        JsonNode first = host.nextOfType("mulligan_update", 3000);
        assertTrue(first.get("decided0").asBoolean());
        assertFalse(first.get("decided1").asBoolean());
        guest.send(new Protocol.MulliganDecision(guestDiscard));
        JsonNode second = host.nextOfType("mulligan_update", 3000);
        assertTrue(second.get("decided0").asBoolean());
        assertTrue(second.get("decided1").asBoolean());
        guest.nextOfType("mulligan_update", 3000);
        guest.nextOfType("mulligan_update", 3000);

        GameSnapshot hostView = snapshotOf(host.nextOfType("snapshot", 5000));
        GameSnapshot guestView = snapshotOf(guest.nextOfType("snapshot", 5000));

        // The game starts legally for both clients.
        assertFalse(hostView.mulliganOpen());
        assertEquals(1, hostView.turnNumber());
        assertEquals(hostView.activePlayer(), guestView.activePlayer());
        assertEquals(hostView.turnNumber(), guestView.turnNumber());

        // The keeper's hand is untouched.
        assertEquals(new HashSet<>(handIds(hostPre, 0)), new HashSet<>(handIds(hostView, 0)));

        // The redrawer's two discards left the hand, were replaced, and sit in discard.
        assertEquals(handIds(guestPre, 1).size(), handIds(guestView, 1).size());
        Set<UUID> guestHand = new HashSet<>(handIds(guestView, 1));
        for (UUID id : guestDiscard) assertFalse(guestHand.contains(id), "discarded card still in hand: " + id);
        List<UUID> guestDiscards = discardIds(guestView, 1);
        assertEquals(2, guestDiscards.size());
        assertTrue(new HashSet<>(guestDiscards).containsAll(guestDiscard));
    }

    /**
     * Reaction windows over the wire: after the host acts, the server prompts
     * only the guest (the inactive player); while the window is open the host
     * is blocked; the guest's exact offered choice is executed and honored.
     */
    @Test void scriptedReactionPromptReachesCorrectPlayerAndChoiceHonored() throws Exception {
        server.close();
        DeckBuild hostDeck = riggedDeck("Host Aggro", "ZEUS", null, List.of(
                "zeus_cloudline_courier", "zeus_arc_relay_scout",
                "zeus_thunderhead_skirmisher", "zeus_keyword_sparkstep_runner",
                "zeus_iris_signal_runner", "zeus_aegis_airguard",
                "zeus_cyclone_marksman", "zeus_keyword_stormgate_sentinel",
                "zeus_fast_tempest_duelist", "zeus_boltwing_cavalier").stream()
                .flatMap(id -> java.util.stream.Stream.of(id, id, id, id)).toList());
        DeckBuild guestDeck = riggedDeck("Guest Spells", "POSEIDON", "ZEUS", List.of(
                "zeus_chain_lightning", "poseidon_crushing_depths", "poseidon_erode_foundation",
                "zeus_skybreaker_bolt", "poseidon_undertow_recall",
                "zeus_windstep_protocol", "zeus_stormcharge", "zeus_aegis_of_the_sky",
                "poseidon_restorative_tide", "poseidon_tidal_armor").stream()
                .flatMap(id -> java.util.stream.Stream.of(id, id, id, id)).toList());
        server = new EmbeddedServer(hostUuid, "Host", hostDeck);

        Driver host = new Driver();
        Driver guest = new Driver();
        host.send(new Protocol.Hello(hostUuid, "Host", null));
        host.nextOfType("lobby", 3000);
        guest.send(new Protocol.Hello(guestUuid, "Guest", dto(guestDeck)));
        host.nextOfType("lobby", 3000);
        guest.nextOfType("lobby", 3000);
        host.send(new Protocol.StartMatch());

        // Mulligan: host digs for 1-cost characters, guest digs for enemy-target
        // spells, so a reaction window opens early and deterministically.
        GameSnapshot hostPre = snapshotOf(host.nextOfType("mulligan_prompt", 5000));
        GameSnapshot guestPre = snapshotOf(guest.nextOfType("mulligan_prompt", 5000));
        host.send(new Protocol.MulliganDecision(discardMatching(hostPre, 0, d -> d.cost() != 1)));
        guest.send(new Protocol.MulliganDecision(discardMatching(guestPre, 1, d -> !ENEMY_SPELLS.contains(d.id()))));
        for (int i = 0; i < 2; i++) {
            host.nextOfType("mulligan_update", 3000);
            guest.nextOfType("mulligan_update", 3000);
        }
        GameSnapshot hostView = snapshotOf(host.nextOfType("snapshot", 5000));
        GameSnapshot guestView = snapshotOf(guest.nextOfType("snapshot", 5000));

        ActionHints hints = new ActionHints();
        GameEngine engine = new GameEngine();
        JsonNode prompt = null;
        JsonNode waiting = null;
        for (int step = 0; step < 60 && prompt == null; step++) {
            int active = hostView.activePlayer();
            Driver actor = active == 0 ? host : guest;
            Driver other = active == 0 ? guest : host;
            String command = active == 0
                    ? chooseCommand(hints.forActivePlayer(GameState.fromSnapshot(hostView, definitions), engine), active)
                    : "end";
            actor.send(new Protocol.PlayerCommand(command));
            GameSnapshot after = snapshotOf(actor.nextOfType("state_update", 5000));
            if (active == 0) hostView = after; else guestView = after;
            GameSnapshot otherAfter = snapshotOf(other.nextOfType("state_update", 5000));
            if (active == 0) guestView = otherAfter; else hostView = otherAfter;
            // A reaction prompt, if opened, arrives right after the other's update.
            String maybe = other.peer.next(800, TimeUnit.MILLISECONDS);
            if (maybe != null) {
                JsonNode node = Protocol.MAPPER.readTree(maybe);
                assertEquals("reaction_prompt", node.get("type").asText(),
                        "unexpected envelope after state update: " + node.get("type").asText());
                prompt = node;
                assertEquals(1 - active, node.get("reactingPlayer").asInt());
                // The non-reacting player gets the lightweight waiting notice
                // (no card data) instead of the prompt; capture it before the
                // game-over probe below discards queued envelopes.
                String notice = actor.peer.next(800, TimeUnit.MILLISECONDS);
                assertNotNull(notice, "active player must get a reaction_waiting notice");
                waiting = Protocol.MAPPER.readTree(notice);
                assertEquals("reaction_waiting", waiting.get("type").asText());
            }
            try {
                host.nextOfType("game_over", 50);
                fail("game ended before any reaction window opened");
            } catch (AssertionError ignored) {
            }
        }
        assertNotNull(prompt, "expected a reaction window to open within 60 steps");
        assertNotNull(waiting, "expected a reaction_waiting notice alongside the prompt");

        // The waiting notice names the reacting player, carries the timeout,
        // and carries no card data.
        int reactor = prompt.get("reactingPlayer").asInt();
        assertEquals(reactor, waiting.get("reactingPlayer").asInt(),
                "waiting notice must name the reacting player");
        assertEquals(60, waiting.get("secondsLeft").asInt(),
                "waiting notice must carry the reaction timeout");
        assertTrue(waiting.path("commands").isMissingNode(),
                "reaction_waiting must not carry card data: " + waiting);

        // The prompt reached the inactive player (guest) only.
        assertNull(host.peer.next(300, TimeUnit.MILLISECONDS),
                "reaction prompt must not be sent to the active player");
        assertTrue(prompt.get("commands").size() > 0, "prompt must offer at least one choice");
        String chosen = prompt.get("commands").get(0).asText();
        assertTrue(chosen.startsWith("react 1 "), "offered commands must address the reactor: " + chosen);

        // While the window is open the active player is blocked.
        host.send(new Protocol.PlayerCommand("end"));
        JsonNode blocked = host.nextOfType("error", 3000);
        assertTrue(blocked.get("message").asText().toLowerCase(java.util.Locale.ROOT).contains("reaction"),
                "active player should be told to wait for the reaction: " + blocked);

        // The guest's choice is honored exactly: the offered command executes.
        int guestGpBefore = guestView.gp()[1];
        int spellCost = definitions.apply(handCardByIndex(guestView, 1,
                Integer.parseInt(chosen.split("\\s+")[2])).definitionId()).cost();
        guest.send(new Protocol.ReactionDecision(chosen));
        JsonNode guestUpdate = guest.nextOfType("state_update", 5000);
        JsonNode hostUpdate = host.nextOfType("state_update", 5000);
        assertEquals(guestUpdate.get("seq").asLong(), hostUpdate.get("seq").asLong());
        assertEquals(chosen, guestUpdate.get("command").asText());
        assertTrue(guestUpdate.get("result").asText().startsWith("OK"),
                "reaction choice was not honored: " + guestUpdate.get("result").asText());
        GameSnapshot afterReaction = snapshotOf(guestUpdate);
        assertEquals(guestGpBefore - spellCost, afterReaction.gp()[1],
                "reactor must pay the chosen spell's cost");
        assertEquals(hostView.activePlayer(), afterReaction.activePlayer(),
                "a reaction must not pass the turn");

        // Play resumes: the active player can act again.
        host.send(new Protocol.PlayerCommand("end"));
        JsonNode resumed = host.nextOfType("state_update", 5000);
        assertTrue(resumed.get("result").asText().startsWith("OK"));
        assertEquals(1 - hostView.activePlayer(), resumed.get("snapshot").get("activePlayer").asInt());
        guest.nextOfType("state_update", 5000);
    }

    /** Enemy-targeted spells the guest's rigged deck can react with. */
    private static final Set<String> ENEMY_SPELLS = Set.of(
            "zeus_chain_lightning", "poseidon_crushing_depths", "poseidon_erode_foundation",
            "zeus_skybreaker_bolt", "poseidon_undertow_recall");

    private DeckBuild riggedDeck(String name, String primary, String ally, List<String> cardIds) {
        List<CardDefinition> cards = cardIds.stream().map(id -> {
            CardDefinition def;
            try {
                def = factory.pool().require(id);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("unknown card: " + id, e);
            }
            return def;
        }).toList();
        return new DeckBuild(name, primary, ally,
                factory.capitals().forFaction(primary).get(0), cards);
    }

    private List<UUID> handIds(GameSnapshot view, int owner) {
        return view.cards().stream()
                .filter(c -> c.zone() == Zone.HAND && c.owner() == owner)
                .map(GameSnapshot.CardView::instanceId).toList();
    }

    private List<UUID> discardIds(GameSnapshot view, int owner) {
        return view.cards().stream()
                .filter(c -> c.zone() == Zone.DISCARD && c.owner() == owner)
                .map(GameSnapshot.CardView::instanceId).toList();
    }

    /** Up to 3 hand IDs matching the discard predicate, in hand order. */
    private List<UUID> discardMatching(GameSnapshot view, int owner,
                                       java.util.function.Predicate<CardDefinition> discardIf) {
        List<UUID> ids = new ArrayList<>();
        for (GameSnapshot.CardView card : view.cards()) {
            if (card.zone() == Zone.HAND && card.owner() == owner
                    && discardIf.test(definitions.apply(card.definitionId()))) {
                ids.add(card.instanceId());
                if (ids.size() == 3) break;
            }
        }
        return ids;
    }

    private GameSnapshot.CardView handCardByIndex(GameSnapshot view, int owner, int index) {
        List<GameSnapshot.CardView> hand = view.cards().stream()
                .filter(c -> c.zone() == Zone.HAND && c.owner() == owner).toList();
        return hand.get(index);
    }
}
