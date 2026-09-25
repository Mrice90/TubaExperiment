package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {
    @Test void activePlayerCanPlayTurnOneLandForFreeNextToCapital() {
        GameState state = new GameState(849291L);
        placeCapital(state, 0, new BoardPosition(1, 0));
        CardDefinition definition = new CardDefinition("dev_land", "Development Land", CardType.LAND, "DEV", 1, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.HAND);
        state.register(card);
        state.player(0).addToHand(card.instanceId());

        ActionResult result = new GameEngine().apply(state, new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 0)));

        assertTrue(result.accepted());
        assertEquals(0, state.player(0).currentGp());
        assertEquals(Zone.BATTLEFIELD, card.zone());
    }

    @Test void rejectsLandPlacementFarFromControlledTerritory() {
        GameState state = new GameState(3L);
        placeCapital(state, 0, new BoardPosition(1, 0));
        CardDefinition definition = new CardDefinition("dev_land", "Development Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.HAND);
        state.register(card);
        state.player(0).addToHand(card.instanceId());
        assertFalse(new GameEngine().apply(state,
                new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 3))).accepted());
    }

    @Test void landPlacementGrowsOutwardFromCapitalAndControlledLand() {
        GameState state = new GameState(21L);
        placeCapital(state, 0, new BoardPosition(1, 0));
        placeCapital(state, 1, new BoardPosition(2, 5));
        // adjacent to your capital: legal; far away: illegal
        assertTrue(GameEngine.legalLandDestination(state, 0, new BoardPosition(0, 0)));
        assertFalse(GameEngine.legalLandDestination(state, 0, new BoardPosition(0, 3)));
        // another player's territory is not your anchor
        assertFalse(GameEngine.legalLandDestination(state, 1, new BoardPosition(0, 0)));
        assertFalse(GameEngine.legalLandDestination(state, 0, new BoardPosition(2, 4)));
        // occupied hexes are never legal, even next to an anchor
        assertFalse(GameEngine.legalLandDestination(state, 0, new BoardPosition(1, 0)));
    }

    @Test void playedLandBecomesAnAnchorForTheNextLand() {
        GameState state = new GameState(22L);
        GameEngine engine = new GameEngine();
        placeCapital(state, 0, new BoardPosition(1, 0));
        CardInstance first = handLand(state, 0, "chain_one");
        assertTrue(engine.apply(state,
                new GameAction.PlayLand(0, first.instanceId(), new BoardPosition(0, 0))).accepted());
        // second land chains off the first, further from the capital
        engine.apply(state, new GameAction.EndTurn(0));
        engine.apply(state, new GameAction.EndTurn(1));
        CardInstance second = handLand(state, 0, "chain_two");
        assertTrue(engine.apply(state,
                new GameAction.PlayLand(0, second.instanceId(), new BoardPosition(0, 1))).accepted());
        // ...but not out in the wilderness
        engine.apply(state, new GameAction.EndTurn(0));
        engine.apply(state, new GameAction.EndTurn(1));
        CardInstance third = handLand(state, 0, "chain_three");
        assertFalse(engine.apply(state,
                new GameAction.PlayLand(0, third.instanceId(), new BoardPosition(3, 5))).accepted());
    }

    private static void placeCapital(GameState state, int owner, BoardPosition position) {
        CardDefinition capitalDef = new CardDefinition("cap_" + owner, "Capital", CardType.CAPITAL, "DEV", 0, 0, 0, 0, 0, 20);
        CardInstance capital = new CardInstance(UUID.randomUUID(), capitalDef, owner, Zone.BATTLEFIELD);
        state.register(capital);
        state.board().push(position, capital.instanceId());
    }

    private static CardInstance handLand(GameState state, int owner, String id) {
        CardDefinition definition = new CardDefinition(id, "Chain Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.HAND);
        state.register(card);
        state.player(owner).addToHand(card.instanceId());
        return card;
    }

    @Test void developmentValueGatesLandByPersonalTurnWithoutSpendingGp() {
        GameState state = new GameState(5L);
        placeCapital(state, 0, new BoardPosition(1, 0));
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
        int gpBefore = state.player(0).currentGp();
        assertTrue(engine.apply(state,
                new GameAction.PlayLand(0, card.instanceId(), new BoardPosition(0, 0))).accepted());
        assertEquals(gpBefore, state.player(0).currentGp());
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
