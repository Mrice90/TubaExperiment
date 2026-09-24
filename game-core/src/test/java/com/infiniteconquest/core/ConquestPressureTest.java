package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConquestPressureTest {
    private CardInstance permanent(GameState state, int owner, String id, int hitPoints, BoardPosition position) {
        CardDefinition definition = new CardDefinition(id, id, CardType.CAPITAL, "TEST", 0,
                0, 0, 0, 0, hitPoints);
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    @Test
    void landsAndStructuresGenerateOneGpOnOwnersTurn() {
        MatchRules rules = new MatchRules(0, 10, 12, 0);
        GameState state = new GameState(1L, rules, false);
        CardDefinition landDefinition = new CardDefinition("land", "Land", CardType.LAND, "TEST", 1,
                0, 0, 0, 0, 10);
        CardDefinition structureDefinition = new CardDefinition("structure", "Structure", CardType.STRUCTURE, "TEST", 1,
                0, 0, 0, 0, 10);
        CardInstance land = new CardInstance(UUID.randomUUID(), landDefinition, 0, Zone.BATTLEFIELD);
        CardInstance structure = new CardInstance(UUID.randomUUID(), structureDefinition, 0, Zone.BATTLEFIELD);
        state.register(land); state.register(structure);
        state.board().push(new BoardPosition(0, 0), land.instanceId());
        state.board().push(new BoardPosition(1, 0), structure.instanceId());
        state.initializeMatch();
        assertEquals(12, state.player(0).currentGp());
        assertTrue(state.events().stream().anyMatch(event -> event.type() == GameEvent.Type.GP_GENERATED));
    }

    @Test
    void matchHasNoLateGameDeadlineOrPressureDamage() {
        MatchRules rules = new MatchRules(0, 10, 12, 0);
        GameState state = new GameState(2L, rules, false);
        permanent(state, 0, "first", 100, new BoardPosition(0, 0));
        permanent(state, 0, "first_extra", 100, new BoardPosition(1, 0));
        permanent(state, 1, "second", 100, new BoardPosition(0, 5));
        state.initializeMatch();

        while (state.turnNumber() < 30) state.advanceTurn();
        assertEquals(Phase.PLAY, state.phase());
        assertTrue(state.winner().isEmpty());
        assertEquals(0, state.battlefieldCards(0).get(0).damage());
    }

    @Test
    void controlledCapitalGeneratesOneGpOnOwnersTurn() {
        MatchRules rules = new MatchRules(0, 0, 1, 0);
        GameState state = new GameState(7L, rules, false);
        permanent(state, 0, "capital_income", 20, new BoardPosition(0, 0));
        state.initializeMatch();
        assertEquals(1, state.player(0).currentGp());
        assertEquals(1, state.gpIncomePerTurn(0));
    }
}
