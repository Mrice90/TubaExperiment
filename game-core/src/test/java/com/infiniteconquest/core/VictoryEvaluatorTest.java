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

    @Test void playerWithNoLandsStructuresOrCapitalsLoses() {
        GameState state = new GameState(1L);
        add(state, 0, CardType.LAND, new BoardPosition(0, 0));
        add(state, 1, CardType.CHARACTER, new BoardPosition(0, 5));

        VictoryEvaluator evaluator = new VictoryEvaluator();
        assertFalse(evaluator.hasAnyPermanent(state, 1));
        assertEquals(0, evaluator.winnerAfterPermanentLoss(state, 1));
    }

    @Test void anyRemainingPermanentPreventsDefeat() {
        GameState state = new GameState(1L);
        add(state, 1, CardType.CAPITAL, new BoardPosition(0, 5));
        assertEquals(-1, new VictoryEvaluator().winnerAfterPermanentLoss(state, 1));
    }
}
