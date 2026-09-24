package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CombatTest {
    private CardInstance battlefield(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    @Test void attackEqualToDefenseKillsAndInRangeDefenderRetaliatesSimultaneously() {
        GameState equalState = new GameState(1L);
        CardInstance equalAttacker = battlefield(equalState, 0,
                new CardDefinition("a", "A", CardType.CHARACTER, "DEV", 0, 3, 1, 1, 1), new BoardPosition(0, 0));
        CardInstance equalDefender = battlefield(equalState, 1,
                new CardDefinition("d", "D", CardType.CHARACTER, "DEV", 0, 1, 3, 1, 1), new BoardPosition(0, 1));

        assertTrue(new GameEngine().apply(equalState,
                new GameAction.Attack(0, equalAttacker.instanceId(), equalDefender.instanceId())).accepted());
        assertEquals(Zone.DISCARD, equalDefender.zone());
        assertEquals(Zone.DISCARD, equalAttacker.zone());

        GameState higherState = new GameState(2L);
        CardInstance strong = battlefield(higherState, 0,
                new CardDefinition("strong", "Strong", CardType.CHARACTER, "DEV", 0, 4, 1, 1, 1), new BoardPosition(0, 0));
        CardInstance weak = battlefield(higherState, 1,
                new CardDefinition("weak", "Weak", CardType.CHARACTER, "DEV", 0, 1, 3, 1, 1), new BoardPosition(0, 1));
        assertTrue(new GameEngine().apply(higherState,
                new GameAction.Attack(0, strong.instanceId(), weak.instanceId())).accepted());
        assertEquals(Zone.DISCARD, weak.zone());
    }

    @Test void defenderCannotRetaliateWhenAttackerIsOutsideDefendersRange() {
        GameState state = new GameState(22L);
        CardInstance attacker = battlefield(state, 0,
                new CardDefinition("archer", "Archer", CardType.CHARACTER, "DEV", 0, 3, 1, 1, 2),
                new BoardPosition(0, 0));
        CardInstance defender = battlefield(state, 1,
                new CardDefinition("guard", "Guard", CardType.CHARACTER, "DEV", 0, 5, 3, 1, 1),
                new BoardPosition(0, 2));

        assertTrue(new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), defender.instanceId())).accepted());
        assertEquals(Zone.BATTLEFIELD, attacker.zone());
        assertEquals(Zone.DISCARD, defender.zone());
    }

    @Test void attacksAccumulateAgainstCharacterForOnlyTheCurrentTurn() {
        GameState state = new GameState(23L);
        CardInstance first = battlefield(state, 0,
                new CardDefinition("first", "First", CardType.CHARACTER, "DEV", 0, 2, 4, 1, 1), new BoardPosition(0, 0));
        CardInstance second = battlefield(state, 0,
                new CardDefinition("second", "Second", CardType.CHARACTER, "DEV", 0, 2, 4, 1, 1), new BoardPosition(1, 0));
        CardInstance defender = battlefield(state, 1,
                new CardDefinition("defender", "Defender", CardType.CHARACTER, "DEV", 0, 0, 4, 1, 1), new BoardPosition(0, 1));

        assertTrue(new GameEngine().apply(state, new GameAction.Attack(0, first.instanceId(), defender.instanceId())).accepted());
        assertEquals(2, defender.combatDamage());
        assertEquals(Zone.BATTLEFIELD, defender.zone());
        assertTrue(new GameEngine().apply(state, new GameAction.Attack(0, second.instanceId(), defender.instanceId())).accepted());
        assertEquals(Zone.DISCARD, defender.zone());

        GameState clearing = new GameState(24L);
        CardInstance chipper = battlefield(clearing, 0,
                new CardDefinition("chipper", "Chipper", CardType.CHARACTER, "DEV", 0, 2, 4, 1, 1), new BoardPosition(0, 0));
        CardInstance survivor = battlefield(clearing, 1,
                new CardDefinition("survivor", "Survivor", CardType.CHARACTER, "DEV", 0, 0, 4, 1, 1), new BoardPosition(0, 1));
        new GameEngine().apply(clearing, new GameAction.Attack(0, chipper.instanceId(), survivor.instanceId()));
        clearing.advanceTurn();
        assertEquals(0, survivor.combatDamage());
    }

    @Test void permanentAccumulatesDamageAndLastPermanentLossEndsGame() {
        GameState state = new GameState(3L);
        battlefield(state, 0, new CardDefinition("home", "Home", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 5),
                new BoardPosition(0, 0));
        CardInstance attacker = battlefield(state, 0,
                new CardDefinition("siege", "Siege", CardType.CHARACTER, "DEV", 0, 3, 1, 1, 2),
                new BoardPosition(1, 1));
        CardInstance target = battlefield(state, 1,
                new CardDefinition("enemy_land", "Enemy Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 3),
                new BoardPosition(1, 3));

        assertTrue(new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), target.instanceId())).accepted());
        assertEquals(Zone.DISCARD, target.zone());
        assertEquals(0, state.winner().orElseThrow());
        assertEquals(Phase.GAME_OVER, state.phase());
    }

    @Test void onlyTopCardMayAttackOrBeTargeted() {
        GameState state = new GameState(4L);
        BoardPosition origin = new BoardPosition(0, 0);
        CardInstance attacker = battlefield(state, 0,
                new CardDefinition("buried", "Buried", CardType.CHARACTER, "DEV", 0, 5, 1, 1, 2), origin);
        battlefield(state, 0, new CardDefinition("cover", "Cover", CardType.STRUCTURE, "DEV", 0, 0, 0, 0, 0, 5), origin);
        CardInstance target = battlefield(state, 1,
                new CardDefinition("target", "Target", CardType.CHARACTER, "DEV", 0, 1, 1, 1, 1), new BoardPosition(0, 1));

        assertFalse(new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), target.instanceId())).accepted());
    }
}
