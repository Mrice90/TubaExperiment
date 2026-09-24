package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SummoningAndStructureTest {
    private CardInstance add(GameState state, int owner, CardDefinition definition, Zone zone) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, zone);
        state.register(card);
        if (zone == Zone.HAND) state.player(owner).addToHand(card.instanceId());
        return card;
    }

    @Test void summonsCharacterDiagonallyAdjacentToFriendlyPermanent() {
        GameState state = new GameState(1L);
        state.player(0).restoreGp(1);
        CardDefinition landDef = new CardDefinition("land", "Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 5);
        CardInstance land = add(state, 0, landDef, Zone.BATTLEFIELD);
        state.board().push(new BoardPosition(0, 0), land.instanceId());
        CardDefinition unitDef = new CardDefinition("unit", "Unit", CardType.CHARACTER, "DEV", 1, 2, 2, 2, 1);
        CardInstance unit = add(state, 0, unitDef, Zone.HAND);

        ActionResult result = new GameEngine().apply(state,
                new GameAction.SummonCharacter(0, unit.instanceId(), new BoardPosition(1, 1)));

        assertTrue(result.accepted());
        assertEquals(Zone.BATTLEFIELD, unit.zone());
    }

    @Test void summonsCharacterOnTopOfFriendlyPermanent() {
        GameState state = new GameState(2L);
        state.player(0).restoreGp(1);
        CardDefinition landDef = new CardDefinition("land", "Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 5);
        CardInstance land = add(state, 0, landDef, Zone.BATTLEFIELD);
        BoardPosition position = new BoardPosition(0, 0);
        state.board().push(position, land.instanceId());
        CardInstance unit = add(state, 0,
                new CardDefinition("unit", "Unit", CardType.CHARACTER, "DEV", 1, 2, 2, 2, 1), Zone.HAND);

        assertTrue(new GameEngine().apply(state, new GameAction.SummonCharacter(0, unit.instanceId(), position)).accepted());
        assertEquals(unit.instanceId(), state.board().topAt(position).orElseThrow());
    }

    @Test void structureRequiresControlledLandAtTopOfStack() {
        GameState state = new GameState(3L);
        BoardPosition position = new BoardPosition(0, 0);
        CardInstance land = add(state, 0,
                new CardDefinition("land", "Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 5), Zone.BATTLEFIELD);
        state.board().push(position, land.instanceId());
        CardInstance structure = add(state, 0,
                new CardDefinition("tower", "Tower", CardType.STRUCTURE, "DEV", 1, 0, 0, 0, 0, 4), Zone.HAND);

        assertTrue(new GameEngine().apply(state, new GameAction.PlayStructure(0, structure.instanceId(), position)).accepted());
        assertEquals(structure.instanceId(), state.board().topAt(position).orElseThrow());
    }
}
