package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExhaustionVictoryTest {
    @Test
    void failedDrawDestroysLethallyDamagedLastPermanent() {
        GameState state = new GameState(88L);
        CardDefinition capitalDefinition = new CardDefinition("test_capital", "Test Capital",
                CardType.CAPITAL, "TEST", 0, 0, 0, 0, 0, 1);
        CardInstance capital = new CardInstance(UUID.randomUUID(), capitalDefinition, 0, Zone.BATTLEFIELD);
        state.register(capital);
        state.board().push(new BoardPosition(1, 0), capital.instanceId());

        new GameEngine().apply(state, new GameAction.EndTurn(0));
        new GameEngine().apply(state, new GameAction.EndTurn(1));

        assertEquals(Phase.GAME_OVER, state.phase());
        assertEquals(1, state.winner().orElseThrow());
        assertEquals(Zone.DISCARD, capital.zone());
    }

    @Test
    void lethalBuriedPermanentWaitsUntilItIsRevealed() {
        GameState state = new GameState(89L);
        BoardPosition position = new BoardPosition(1, 0);
        CardInstance land = add(state, new CardDefinition("land", "Land", CardType.LAND,
                "TEST", 0, 0, 0, 0, 0, 1), position);
        CardInstance structure = add(state, new CardDefinition("structure", "Structure", CardType.STRUCTURE,
                "TEST", 0, 0, 0, 0, 0, 1), position);

        new GameEngine().apply(state, new GameAction.EndTurn(0));
        new GameEngine().apply(state, new GameAction.EndTurn(1));

        assertEquals(Zone.DISCARD, structure.zone());
        assertEquals(Zone.DISCARD, land.zone());
        assertEquals(Phase.GAME_OVER, state.phase());
    }

    private CardInstance add(GameState state, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, 0, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }
}
