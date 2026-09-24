package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {
    @Test void activePlayerCanPlayTurnOneLandForFreeIntoOwnPlot() {
        GameState state = new GameState(849291L);
        CardDefinition definition = new CardDefinition("dev_land", "Development Land", CardType.LAND, "DEV", 1, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.HAND);
        state.register(card);
        state.player(0).addToHand(card.instanceId());

        ActionResult result = new GameEngine().apply(state, new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 0)));

        assertTrue(result.accepted());
        assertEquals(0, state.player(0).currentGp());
        assertEquals(Zone.BATTLEFIELD, card.zone());
    }

    @Test void rejectsLandPlacementOnOpponentsPlot() {
        GameState state = new GameState(3L);
        CardDefinition definition = new CardDefinition("dev_land", "Development Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.HAND);
        state.register(card);
        state.player(0).addToHand(card.instanceId());
        assertFalse(new GameEngine().apply(state,
                new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 3))).accepted());
    }

    @Test void developmentValueGatesLandByPersonalTurnWithoutSpendingGp() {
        GameState state = new GameState(5L);
        CardDefinition definition = new CardDefinition("turn_three_land", "Turn Three Land",
                CardType.LAND, "DEV", 3, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.HAND);
        state.register(card);
        state.player(0).addToHand(card.instanceId());
        GameEngine engine = new GameEngine();

        assertFalse(engine.apply(state,
                new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 0))).accepted());
        engine.apply(state, new GameAction.EndTurn(0));
        engine.apply(state, new GameAction.EndTurn(1));
        engine.apply(state, new GameAction.EndTurn(0));
        engine.apply(state, new GameAction.EndTurn(1));

        assertEquals(3, state.personalTurnNumber(0));
        assertTrue(engine.apply(state,
                new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 0))).accepted());
        assertEquals(0, state.player(0).currentGp());
    }

    @Test void endTurnChangesActivePlayerAndPreservesSecondPlayerOpeningGp() {
        GameState state = new GameState(7L);
        assertTrue(new GameEngine().apply(state, new GameAction.EndTurn(0)).accepted());
        assertEquals(1, state.activePlayer());
        assertEquals(2, state.turnNumber());
        assertEquals(1, state.player(1).currentGp());
    }

    @Test void rejectsOpponentAction() {
        GameState state = new GameState(1L);
        assertFalse(new GameEngine().apply(state, new GameAction.EndTurn(1)).accepted());
    }
}
