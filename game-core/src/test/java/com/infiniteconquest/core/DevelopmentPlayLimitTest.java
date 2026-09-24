package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentPlayLimitTest {
    @Test void oneLandAndOneStructureMayBePlayedIndependentlyEachTurn() {
        GameState state = new GameState(301L);
        assertTrue(state.canPlayDevelopment(0, CardType.LAND));
        assertTrue(state.canPlayDevelopment(0, CardType.STRUCTURE));

        state.recordCardPlayed(card(0, CardType.LAND));
        assertFalse(state.canPlayDevelopment(0, CardType.LAND));
        assertTrue(state.canPlayDevelopment(0, CardType.STRUCTURE));

        state.recordCardPlayed(card(0, CardType.STRUCTURE));
        assertFalse(state.canPlayDevelopment(0, CardType.LAND));
        assertFalse(state.canPlayDevelopment(0, CardType.STRUCTURE));
        assertTrue(state.canPlayDevelopment(1, CardType.LAND), "The opponent has independent limits");

        state.advanceTurn();
        state.advanceTurn();
        assertTrue(state.canPlayDevelopment(0, CardType.LAND));
        assertTrue(state.canPlayDevelopment(0, CardType.STRUCTURE));
    }

    private CardInstance card(int owner, CardType type) {
        CardDefinition definition = new CardDefinition(type.name().toLowerCase() + UUID.randomUUID(),
                type.name(), type, "TEST", 1, 0, 0, 0, 0, 8);
        return new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
    }
}
