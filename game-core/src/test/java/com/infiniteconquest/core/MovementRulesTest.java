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

    private CardInstance permanent(GameState state, CardType type, int owner, BoardPosition position) {
        CardDefinition definition = new CardDefinition("block", "Block", type, "DEV", 0, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    @Test void enemyStructureBlocksMovement() {
        GameState state = new GameState(2L);
        CardInstance runner = character(state, 1, new BoardPosition(0, 0));
        permanent(state, CardType.STRUCTURE, 1, new BoardPosition(1, 0));

        assertFalse(new GameEngine().legalMovementDestinations(state, runner.instanceId()).contains(new BoardPosition(1, 0)));
    }

    @Test void enemyLandIsOpenGround() {
        GameState state = new GameState(2L);
        CardInstance runner = character(state, 2, new BoardPosition(0, 0));
        permanent(state, CardType.LAND, 1, new BoardPosition(1, 0));
        GameEngine engine = new GameEngine();

        var destinations = engine.legalMovementDestinations(state, runner.instanceId());
        assertTrue(destinations.contains(new BoardPosition(1, 0)), "a character can enter enemy land");
        assertTrue(destinations.contains(new BoardPosition(2, 0)), "a character can march straight through enemy land");
        assertTrue(engine.apply(state,
                new GameAction.MoveCharacter(0, runner.instanceId(), new BoardPosition(2, 0))).accepted());
        assertEquals(new BoardPosition(2, 0), state.board().positionOf(runner.instanceId()).orElseThrow());
    }

    @Test void enemyCharacterAndMixedStacksStillBlock() {
        GameState state = new GameState(2L);
        CardInstance runner = character(state, 1, new BoardPosition(0, 0));
        permanent(state, CardType.CHARACTER, 1, new BoardPosition(1, 0));
        permanent(state, CardType.LAND, 1, new BoardPosition(0, 1));
        permanent(state, CardType.STRUCTURE, 1, new BoardPosition(0, 1));

        var destinations = new GameEngine().legalMovementDestinations(state, runner.instanceId());
        assertFalse(destinations.contains(new BoardPosition(1, 0)), "enemy Character blocks");
        assertFalse(destinations.contains(new BoardPosition(0, 1)), "enemy land under an enemy structure still blocks");
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

    @Test void movementHalvedInEnemyTerritory() {
        GameState state = new GameState(11L);
        // Player 0 invader on player 1's home rows: movement 3 becomes 1.
        CardInstance invader = character(state, 3, new BoardPosition(1, 4));
        var destinations = new GameEngine().legalMovementDestinations(state, invader.instanceId());
        assertFalse(destinations.isEmpty());
        for (BoardPosition destination : destinations) {
            assertEquals(1, state.rules().geometry().distance(new BoardPosition(1, 4), destination));
        }
    }

    @Test void slowUnitStillMovesOneInEnemyTerritory() {
        GameState state = new GameState(12L);
        CardInstance invader = character(state, 1, new BoardPosition(1, 5));
        var destinations = new GameEngine().legalMovementDestinations(state, invader.instanceId());
        assertFalse(destinations.isEmpty(), "minimum 1 movement in enemy territory");
        for (BoardPosition destination : destinations) {
            assertEquals(1, state.rules().geometry().distance(new BoardPosition(1, 5), destination));
        }
    }

    @Test void fullMovementOnOwnSide() {
        GameState state = new GameState(13L);
        // Home rows are not enemy territory: full speed.
        CardInstance scout = character(state, 3, new BoardPosition(1, 1));
        var destinations = new GameEngine().legalMovementDestinations(state, scout.instanceId());
        assertTrue(destinations.stream()
                .anyMatch(d -> state.rules().geometry().distance(new BoardPosition(1, 1), d) == 3));
    }
}
