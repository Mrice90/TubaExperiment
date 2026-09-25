package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VictoryEvaluatorTest {
    private CardInstance add(GameState state, int owner, CardType type, BoardPosition position) {
        CardDefinition definition = new CardDefinition(type + "_" + owner, type.name(), type, "DEV", 0, 0, 0, 0, 0);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    @Test void capitalDestroyedEndsGameDespiteRemainingLandsAndStructures() {
        GameState state = new GameState(1L);
        add(state, 0, CardType.LAND, new BoardPosition(0, 0));
        add(state, 0, CardType.STRUCTURE, new BoardPosition(1, 0));
        add(state, 1, CardType.CHARACTER, new BoardPosition(0, 5));

        VictoryEvaluator evaluator = new VictoryEvaluator();
        assertFalse(evaluator.hasCapital(state, 0));
        assertEquals(1, evaluator.winnerAfterCapitalLoss(state, 0));
    }

    @Test void landsAndStructuresLostButCapitalAliveMeansGameContinues() {
        GameState state = new GameState(1L);
        add(state, 1, CardType.CAPITAL, new BoardPosition(0, 5));
        add(state, 1, CardType.CHARACTER, new BoardPosition(1, 5));
        assertTrue(new VictoryEvaluator().hasCapital(state, 1));
        assertEquals(-1, new VictoryEvaluator().winnerAfterCapitalLoss(state, 1));
    }

    @Test void capitalDestroyedThroughGameEngineEndsGame() {
        GameState state = new GameState(2L);
        CardInstance capital = add(state, 0, CardType.CAPITAL, new BoardPosition(1, 0));
        add(state, 0, CardType.LAND, new BoardPosition(2, 0));
        add(state, 0, CardType.STRUCTURE, new BoardPosition(3, 0));

        state.destroy(capital);

        assertEquals(Phase.GAME_OVER, state.phase());
        assertEquals(1, state.winner().orElseThrow());
    }

    @Test void destroyingLandsAndStructuresWhileCapitalLivesContinuesGame() {
        GameState state = new GameState(3L);
        add(state, 0, CardType.CAPITAL, new BoardPosition(1, 0));
        CardInstance land = add(state, 0, CardType.LAND, new BoardPosition(2, 0));
        CardInstance structure = add(state, 0, CardType.STRUCTURE, new BoardPosition(3, 0));

        state.destroy(land);
        state.destroy(structure);

        assertEquals(Phase.PLAY, state.phase());
        assertTrue(state.winner().isEmpty());
    }
}
