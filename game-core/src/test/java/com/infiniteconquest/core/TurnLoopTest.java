package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TurnLoopTest {
    private List<CardDefinition> validDeck(String prefix) {
        List<CardDefinition> cards = new ArrayList<>();
        for (int id = 0; id < 10; id++) {
            CardDefinition definition = new CardDefinition(prefix + id, prefix + id, CardType.LAND, "DEV", 0, 0, 0, 0, 0);
            for (int copy = 0; copy < 4; copy++) cards.add(definition);
        }
        return cards;
    }

    @Test void openingBonusFollowsCoinFlipOrder() {
        GameState state = new MatchFactory().create(9L, MatchRules.current(), validDeck("a"), validDeck("b"));
        int first = state.startingPlayer();
        int second = 1 - first;
        assertEquals(5, state.player(first).hand().size(), "Starting player opens with five and skips the first-turn draw");
        assertEquals(first == 0 ? 0 : 1, state.player(first).currentGp());
        assertEquals(6, state.player(second).hand().size(), "Second player opens with six before their first draw");
        assertEquals(second == 0 ? 0 : 1, state.player(second).currentGp());

        assertTrue(new GameEngine().apply(state, new GameAction.EndTurn(first)).accepted());
        assertEquals(second, state.activePlayer());
        assertEquals(7, state.player(second).hand().size());
    }

    @Test void eventSequenceIsOrderedAndRecordsPhaseChanges() {
        GameState state = new MatchFactory().create(11L, MatchRules.current(), validDeck("a"), validDeck("b"));
        new GameEngine().apply(state, new GameAction.EndTurn(state.activePlayer()));
        List<GameEvent> events = state.events();
        for (int i = 0; i < events.size(); i++) assertEquals(i, events.get(i).sequence());
        assertTrue(events.stream().anyMatch(e -> e.type() == GameEvent.Type.TURN_ENDED));
        assertTrue(events.stream().anyMatch(e -> e.type() == GameEvent.Type.CARD_DRAWN && e.playerId() == 1));
        assertTrue(events.stream().anyMatch(e -> e.type() == GameEvent.Type.CARDS_UNTAPPED));
    }

    @Test void gpPersistsAndDoesNotGrowWithoutDevelopment() {
        MatchRules rules = new MatchRules(0, 10, 12, 0);
        GameState state = new GameState(4L, rules, false);
        state.initializeMatch();
        GameEngine engine = new GameEngine();
        for (int i = 0; i < 10; i++) assertTrue(engine.apply(state, new GameAction.EndTurn(state.activePlayer())).accepted());
        assertEquals(10, state.player(0).currentGp());
        assertEquals(12, state.player(1).currentGp());
    }

    @Test void failedDrawDamagesEveryControlledPermanent() {
        MatchRules rules = new MatchRules(0, 10, 12, 1);
        GameState state = new GameState(4L, rules, false);
        CardDefinition land = new CardDefinition("land", "Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0);
        CardInstance permanent = new CardInstance(UUID.randomUUID(), land, 0, Zone.BATTLEFIELD);
        state.register(permanent);
        state.board().push(new BoardPosition(0, 0), permanent.instanceId());
        state.initializeMatch();
        state.advanceTurn();
        state.advanceTurn();
        assertEquals(1, permanent.damage());
        assertTrue(state.events().stream().anyMatch(e -> e.type() == GameEvent.Type.DRAW_FAILED));
        assertTrue(state.events().stream().anyMatch(e -> e.type() == GameEvent.Type.EXHAUSTION_DAMAGE));
    }
}
