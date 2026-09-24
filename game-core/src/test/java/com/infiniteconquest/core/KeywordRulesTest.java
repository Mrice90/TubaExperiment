package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KeywordRulesTest {
    private CardDefinition character(String id, int attack, int defense, int range, Keyword... keywords) {
        return new CardDefinition(id, id, CardType.CHARACTER, "DEV", 0,
                attack, defense, 2, range, 0, Set.of(keywords));
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, Zone zone,
                             BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, zone);
        state.register(card);
        if (zone == Zone.HAND) state.player(owner).addToHand(card.instanceId());
        if (position != null) state.board().push(position, card.instanceId());
        return card;
    }

    @Test
    void moleBurrowsBeneathControlledLandAndIsRevealedWhenLandLeaves() {
        GameState state = new GameState(10L);
        BoardPosition position = new BoardPosition(1, 1);
        CardInstance land = add(state, 0,
                new CardDefinition("land", "Land", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 4),
                Zone.BATTLEFIELD, position);
        CardInstance mole = add(state, 0, character("mole", 2, 2, 1, Keyword.MOLE),
                Zone.HAND, null);

        ActionResult result = new GameEngine().apply(state,
                new GameAction.BurrowCharacter(0, mole.instanceId(), position));

        assertTrue(result.accepted());
        assertEquals(java.util.List.of(mole.instanceId(), land.instanceId()), state.board().stackAt(position));
        assertEquals(land.instanceId(), state.board().topAt(position).orElseThrow());

        state.destroy(land);
        assertEquals(mole.instanceId(), state.board().topAt(position).orElseThrow());
    }

    @Test
    void nonMoleCannotBurrowAndMoleCannotUseEnemyLand() {
        GameState state = new GameState(11L);
        BoardPosition friendly = new BoardPosition(0, 0);
        BoardPosition enemy = new BoardPosition(0, 3);
        add(state, 0, new CardDefinition("friendly", "Friendly", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 4),
                Zone.BATTLEFIELD, friendly);
        add(state, 1, new CardDefinition("enemy", "Enemy", CardType.LAND, "DEV", 0, 0, 0, 0, 0, 4),
                Zone.BATTLEFIELD, enemy);
        CardInstance ordinary = add(state, 0, character("ordinary", 1, 1, 1), Zone.HAND, null);
        CardInstance mole = add(state, 0, character("mole", 1, 1, 1, Keyword.MOLE), Zone.HAND, null);

        assertFalse(new GameEngine().apply(state,
                new GameAction.BurrowCharacter(0, ordinary.instanceId(), friendly)).accepted());
        assertFalse(new GameEngine().apply(state,
                new GameAction.BurrowCharacter(0, mole.instanceId(), enemy)).accepted());
    }

    @Test
    void blinkTeleportsToAnyEmptySquareOncePerTurnWithoutSpendingMovement() {
        GameState state = new GameState(12L);
        CardInstance blinker = add(state, 0, character("blinker", 2, 2, 1, Keyword.BLINK),
                Zone.BATTLEFIELD, new BoardPosition(0, 0));
        GameEngine engine = new GameEngine();

        assertTrue(engine.apply(state,
                new GameAction.BlinkCharacter(0, blinker.instanceId(), new BoardPosition(3, 5))).accepted());
        assertEquals(new BoardPosition(3, 5), state.board().positionOf(blinker.instanceId()).orElseThrow());
        assertEquals(0, blinker.movementSpent());
        assertFalse(engine.apply(state,
                new GameAction.BlinkCharacter(0, blinker.instanceId(), new BoardPosition(0, 0))).accepted());

        engine.apply(state, new GameAction.EndTurn(0));
        engine.apply(state, new GameAction.EndTurn(1));
        assertTrue(engine.apply(state,
                new GameAction.BlinkCharacter(0, blinker.instanceId(), new BoardPosition(0, 0))).accepted());
    }

    @Test
    void structuresCapitalsAndVanguardBlockLineOfSight() {
        assertBlocks(new CardDefinition("wall", "Wall", CardType.STRUCTURE, "DEV", 0, 0, 0, 0, 0, 5));
        assertBlocks(new CardDefinition("capital", "Capital", CardType.CAPITAL, "DEV", 0, 0, 0, 0, 0, 20));
        assertBlocks(character("guard", 1, 5, 1, Keyword.VANGUARD));
    }

    @Test
    void ordinaryCharacterDoesNotBlockLineOfSight() {
        GameState state = rangedLane(character("middle", 1, 1, 1));
        CardInstance attacker = state.board().topAt(new BoardPosition(0, 0)).flatMap(state::card).orElseThrow();
        CardInstance target = state.board().topAt(new BoardPosition(0, 2)).flatMap(state::card).orElseThrow();

        assertTrue(new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), target.instanceId())).accepted());
        assertEquals(Zone.DISCARD, target.zone());
    }

    @Test
    void diagonalVanguardBlocksDiagonalAim() {
        GameState state = new GameState(14L);
        CardInstance attacker = add(state, 0, character("archer", 4, 1, 3), Zone.BATTLEFIELD,
                new BoardPosition(0, 0));
        add(state, 1, character("vanguard", 1, 4, 1, Keyword.VANGUARD), Zone.BATTLEFIELD,
                new BoardPosition(1, 1));
        CardInstance target = add(state, 1, character("target", 1, 1, 1), Zone.BATTLEFIELD,
                new BoardPosition(2, 2));

        assertFalse(new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), target.instanceId())).accepted());
    }

    @Test
    void fastStrikePreventsRetaliationOnlyWhenItBreaksDefense() {
        GameState state = new GameState(15L);
        CardInstance striker = add(state, 0, character("striker", 4, 2, 1, Keyword.FAST_STRIKE),
                Zone.BATTLEFIELD, new BoardPosition(0, 0));
        CardInstance defender = add(state, 1, character("defender", 5, 3, 1),
                Zone.BATTLEFIELD, new BoardPosition(0, 1));

        assertTrue(new GameEngine().apply(state,
                new GameAction.Attack(0, striker.instanceId(), defender.instanceId())).accepted());
        assertEquals(Zone.BATTLEFIELD, striker.zone());
        assertEquals(Zone.DISCARD, defender.zone());

        GameState tied = new GameState(16L);
        CardInstance tiedStriker = add(tied, 0, character("tied_striker", 3, 2, 1, Keyword.FAST_STRIKE),
                Zone.BATTLEFIELD, new BoardPosition(0, 0));
        CardInstance tiedDefender = add(tied, 1, character("tied_defender", 3, 3, 1),
                Zone.BATTLEFIELD, new BoardPosition(0, 1));
        assertTrue(new GameEngine().apply(tied,
                new GameAction.Attack(0, tiedStriker.instanceId(), tiedDefender.instanceId())).accepted());
        assertEquals(Zone.DISCARD, tiedStriker.zone());
        assertEquals(Zone.DISCARD, tiedDefender.zone());
    }

    @Test
    void siegeDealsDoubleDamageToPermanents() {
        GameState state = new GameState(17L);
        CardInstance siege = add(state, 0, character("siege", 3, 2, 2, Keyword.SIEGE),
                Zone.BATTLEFIELD, new BoardPosition(0, 0));
        CardInstance land = add(state, 1,
                new CardDefinition("fort", "Fort", CardType.LAND, "DEV", 1, 0, 0, 0, 0, 8),
                Zone.BATTLEFIELD, new BoardPosition(0, 2));
        assertTrue(new GameEngine().apply(state,
                new GameAction.Attack(0, siege.instanceId(), land.instanceId())).accepted());
        assertEquals(6, land.damage());
    }

    @Test
    void sharpShotGainsAttackAndRangeOnlyAboveStructureOrCapital() {
        GameState state = new GameState(18L);
        BoardPosition tower = new BoardPosition(0, 0);
        add(state, 0, new CardDefinition("tower", "Tower", CardType.STRUCTURE, "DEV", 1, 0, 0, 0, 0, 8),
                Zone.BATTLEFIELD, tower);
        CardInstance marksman = add(state, 0, character("marksman", 2, 2, 1, Keyword.SHARP_SHOT),
                Zone.BATTLEFIELD, tower);
        CardInstance target = add(state, 1, character("target", 1, 3, 1),
                Zone.BATTLEFIELD, new BoardPosition(0, 2));
        GameEngine engine = new GameEngine();

        assertEquals(3, engine.effectiveAttack(state, marksman));
        assertEquals(2, engine.effectiveRange(state, marksman));
        assertTrue(engine.apply(state, new GameAction.Attack(0, marksman.instanceId(), target.instanceId())).accepted());
        assertEquals(Zone.DISCARD, target.zone());
    }

    @Test
    void characterCanMoveOntoFriendlyPermanentStack() {
        GameState state = new GameState(19L);
        CardInstance runner = add(state, 0, character("runner", 2, 2, 1), Zone.BATTLEFIELD,
                new BoardPosition(0, 0));
        BoardPosition capital = new BoardPosition(1, 0);
        CardInstance base = add(state, 0,
                new CardDefinition("capital", "Capital", CardType.CAPITAL, "DEV", 0, 0, 0, 0, 0, 20),
                Zone.BATTLEFIELD, capital);

        assertTrue(new GameEngine().apply(state,
                new GameAction.MoveCharacter(0, runner.instanceId(), capital)).accepted());
        assertEquals(java.util.List.of(base.instanceId(), runner.instanceId()), state.board().stackAt(capital));
        assertEquals(runner.instanceId(), state.board().topAt(capital).orElseThrow());
    }

    private void assertBlocks(CardDefinition blocker) {
        GameState state = rangedLane(blocker);
        CardInstance attacker = state.board().topAt(new BoardPosition(0, 0)).flatMap(state::card).orElseThrow();
        CardInstance target = state.board().topAt(new BoardPosition(0, 2)).flatMap(state::card).orElseThrow();

        ActionResult result = new GameEngine().apply(state,
                new GameAction.Attack(0, attacker.instanceId(), target.instanceId()));
        assertFalse(result.accepted());
        assertEquals(Zone.BATTLEFIELD, target.zone());
        assertFalse(attacker.attackedThisTurn());
    }

    private GameState rangedLane(CardDefinition middle) {
        GameState state = new GameState(13L);
        add(state, 0, character("archer", 4, 1, 3), Zone.BATTLEFIELD, new BoardPosition(0, 0));
        add(state, 1, middle, Zone.BATTLEFIELD, new BoardPosition(0, 1));
        add(state, 1, character("target", 1, 1, 1), Zone.BATTLEFIELD, new BoardPosition(0, 2));
        return state;
    }
}
