package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MovementRulesTest {
    private CardInstance character(GameState state, int movement, BoardPosition position) {
        CardDefinition definition = new CardDefinition("runner", "Runner", CardType.CHARACTER, "DEV", 0, 1, 1, movement, 1);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    @Test void diagonalMovementCostsOneAndCanBeSplitAcrossActions() {
        GameState state = new GameState(1L);
        CardInstance runner = character(state, 3, new BoardPosition(0, 0));
        GameEngine engine = new GameEngine();

        assertTrue(engine.legalMovementDestinations(state, runner.instanceId()).contains(new BoardPosition(1, 1)));
        assertTrue(engine.apply(state, new GameAction.MoveCharacter(0, runner.instanceId(), new BoardPosition(1, 1))).accepted());
        assertEquals(1, runner.movementSpent());

        assertTrue(engine.apply(state, new GameAction.MoveCharacter(0, runner.instanceId(), new BoardPosition(3, 3))).accepted());
        assertEquals(3, runner.movementSpent());
        assertTrue(engine.legalMovementDestinations(state, runner.instanceId()).isEmpty());
    }

    @Test void enemyOccupiedCellsBlockOrdinaryMovementPaths() {
        GameState state = new GameState(2L);
        CardInstance runner = character(state, 1, new BoardPosition(0, 0));
        CardDefinition landDef = new CardDefinition("block", "Block", CardType.LAND, "DEV", 0, 0, 0, 0, 0);
        CardInstance blocker = new CardInstance(UUID.randomUUID(), landDef, 1, Zone.BATTLEFIELD);
        state.register(blocker);
        state.board().push(new BoardPosition(1, 1), blocker.instanceId());

        assertFalse(new GameEngine().legalMovementDestinations(state, runner.instanceId()).contains(new BoardPosition(1, 1)));
    }

    @Test void characterCanMoveOntoAnotherFriendlyCharacter() {
        GameState state = new GameState(3L);
        CardInstance runner = character(state, 1, new BoardPosition(0, 0));
        CardInstance ally = character(state, 1, new BoardPosition(1, 1));
        GameEngine engine = new GameEngine();

        assertTrue(engine.legalMovementDestinations(state, runner.instanceId()).contains(new BoardPosition(1, 1)));
        assertTrue(engine.apply(state,
                new GameAction.MoveCharacter(0, runner.instanceId(), new BoardPosition(1, 1))).accepted());
        assertEquals(List.of(ally.instanceId(), runner.instanceId()), state.board().stackAt(new BoardPosition(1, 1)));
    }

    @Test void movementThroughEnemyRangeTriggersOneFreeAttackAndStopsWhenLethal() {
        GameState state = new GameState(5L);
        CardInstance runner = character(state, 3, new BoardPosition(0, 0));
        CardDefinition sentryDefinition = new CardDefinition("sentry", "Sentry", CardType.CHARACTER,
                "DEV", 0, 1, 1, 1, 1);
        CardInstance sentry = new CardInstance(UUID.randomUUID(), sentryDefinition, 1, Zone.BATTLEFIELD);
        state.register(sentry);
        state.board().push(new BoardPosition(1, 1), sentry.instanceId());
        GameEngine engine = new GameEngine();

        var threats = engine.opportunityThreats(state, runner.instanceId(), new BoardPosition(2, 0));
        assertEquals(1, threats.size());
        assertTrue(threats.get(0).lethal());

        ActionResult result = engine.apply(state,
                new GameAction.MoveCharacter(0, runner.instanceId(), new BoardPosition(2, 0)));
        assertTrue(result.accepted());
        assertEquals(Zone.DISCARD, runner.zone());
        assertEquals(1, state.events().stream()
                .filter(event -> event.type() == GameEvent.Type.OPPORTUNITY_ATTACK).count());
        assertFalse(sentry.attackedThisTurn(), "free attack must not consume the normal attack");
    }
}
