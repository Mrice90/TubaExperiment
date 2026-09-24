package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link GameState#fromSnapshot} faithfully rebuilds every visible
 * aspect of a match from a {@link GameSnapshot}: turn/phase counters, GP, zones,
 * board stacks, per-card combat state, and the event tail.
 */
class GameSnapshotTest {
    private final Map<String, CardDefinition> definitions = new HashMap<>();

    private CardDefinition def(String id, CardType type) {
        return definitions.computeIfAbsent(id,
                key -> new CardDefinition(key, key, type, "TEST", 1, 2, 2, 0, 0));
    }

    @Test void roundTripPreservesVisibleState() {
        GameState original = new GameState(1234L);
        CardInstance land = new CardInstance(UUID.randomUUID(), def("land_a", CardType.LAND), 0, Zone.HAND);
        CardInstance knight = new CardInstance(UUID.randomUUID(), def("knight_a", CardType.CHARACTER), 0, Zone.HAND);
        CardInstance foe = new CardInstance(UUID.randomUUID(), def("foe_a", CardType.CHARACTER), 1, Zone.HAND);
        for (CardInstance card : List.of(land, knight, foe)) original.register(card);
        original.player(0).addToHand(land.instanceId());
        original.player(0).addToHand(knight.instanceId());
        original.player(1).addToHand(foe.instanceId());

        GameEngine engine = new GameEngine();
        assertTrue(engine.apply(original, new GameAction.PlayLand(0, land.instanceId(), new BoardPosition(1, 0))).accepted());
        knight.addDamage(1);
        knight.markAttacked();

        GameSnapshot snapshot = snapshotForTest(original, 0);
        GameState rebuilt = GameState.fromSnapshot(snapshot, definitions::get);

        assertEquals(original.seed(), rebuilt.seed());
        assertEquals(original.activePlayer(), rebuilt.activePlayer());
        assertEquals(original.turnNumber(), rebuilt.turnNumber());
        assertEquals(original.phase(), rebuilt.phase());
        assertEquals(original.player(0).currentGp(), rebuilt.player(0).currentGp());
        assertEquals(original.player(0).hand().size(), rebuilt.player(0).hand().size());
        assertEquals(original.player(1).hand().size(), rebuilt.player(1).hand().size());
        assertEquals(original.board().stackAt(new BoardPosition(1, 0)),
                rebuilt.board().stackAt(new BoardPosition(1, 0)));

        CardInstance rebuiltKnight = rebuilt.card(knight.instanceId()).orElseThrow();
        assertEquals(1, rebuiltKnight.damage());
        assertTrue(rebuiltKnight.attackedThisTurn());
        assertEquals(Zone.HAND, rebuiltKnight.zone());

        // Opponent hidden-zone cards are absent; only their count survives, backed by
        // inert deterministic placeholders (never the real UUIDs).
        assertTrue(rebuilt.card(foe.instanceId()).isEmpty());
        assertEquals(1, rebuilt.player(1).hand().size());
        UUID placeholder = rebuilt.player(1).hand().get(0);
        assertNotEquals(foe.instanceId(), placeholder);
        assertEquals("Hidden Card", rebuilt.card(placeholder).orElseThrow().definition().name());
    }

    @Test void unknownDefinitionFailsFast() {
        GameState original = new GameState(9L);
        GameSnapshot snapshot = snapshotForTest(original, 1);
        List<GameSnapshot.CardView> poisoned = new ArrayList<>(snapshot.cards());
        poisoned.add(new GameSnapshot.CardView(UUID.randomUUID(), "no_such_card", 0,
                Zone.HAND, null, 0, false, 0, 0, false, false, false, 0));
        GameSnapshot bad = new GameSnapshot(snapshot.seed(), snapshot.rulesId(), snapshot.viewingPlayer(),
                snapshot.activePlayer(), snapshot.startingPlayer(), snapshot.turnNumber(), snapshot.personalTurns(),
                snapshot.phase(), snapshot.winner(), snapshot.gp(), snapshot.maxGp(), snapshot.handCounts(),
                snapshot.deckCounts(), snapshot.landsPlayed(), snapshot.structuresPlayed(), poisoned, snapshot.events());
        assertThrows(IllegalArgumentException.class, () -> GameState.fromSnapshot(bad, definitions::get));
    }

    /**
     * Test-only stand-in for the net-server Redactor: keeps public zones plus the
     * viewer's own hand/deck, opponent hidden zones as counts only.
     */
    private GameSnapshot snapshotForTest(GameState state, int viewingPlayer) {
        List<GameSnapshot.CardView> cards = new ArrayList<>();
        for (BoardPosition position : state.board().positions()) {
            for (UUID id : state.board().stackAt(position)) {
                CardInstance card = state.card(id).orElseThrow();
                cards.add(view(card, position));
            }
        }
        for (int player = 0; player < 2; player++) {
            for (UUID id : state.player(player).discard())
                cards.add(view(state.card(id).orElseThrow(), null));
            if (player == viewingPlayer) {
                for (UUID id : state.player(player).hand())
                    cards.add(view(state.card(id).orElseThrow(), null));
                for (UUID id : state.player(player).deck())
                    cards.add(view(state.card(id).orElseThrow(), null));
            }
        }
        return new GameSnapshot(state.seed(), state.rules().geometry().name(), viewingPlayer,
                state.activePlayer(), state.startingPlayer(), state.turnNumber(),
                new int[]{state.personalTurnNumber(0), state.personalTurnNumber(1)},
                state.phase().name(), state.winner().isPresent() ? state.winner().getAsInt() : null,
                new int[]{state.player(0).currentGp(), state.player(1).currentGp()},
                new int[]{state.player(0).maximumGp(), state.player(1).maximumGp()},
                new int[]{state.player(0).hand().size(), state.player(1).hand().size()},
                new int[]{state.player(0).deck().size(), state.player(1).deck().size()},
                new int[]{0, 0}, new int[]{0, 0},
                cards, List.copyOf(state.events()));
    }

    private GameSnapshot.CardView view(CardInstance card, BoardPosition position) {
        return new GameSnapshot.CardView(card.instanceId(), card.definition().id(), card.owner(), card.zone(),
                position, card.damage(), card.tapped(), card.attackBonus(), card.defenseBonus(),
                card.attackedThisTurn(), card.blinkUsedThisTurn(), card.abilityUsedThisTurn(), 0);
    }
}
