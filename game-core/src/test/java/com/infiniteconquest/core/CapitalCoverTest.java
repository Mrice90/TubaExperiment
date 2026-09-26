package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enemy structures work as cover for the Capital against aimed
 * capital-striking abilities (Storm Relay Pylon and its kin): the activation
 * is rejected while a structure stands on the sight line, unless the source
 * aims from higher ground.
 */
class CapitalCoverTest {
    private final GameEngine engine = new GameEngine();

    private GameState state() {
        return new GameState(42L, MatchRules.hex(), true);
    }

    private CardDefinition pingerDef() {
        return new CardDefinition("pinger", "Pinger", CardType.STRUCTURE, "TEST", 1,
                0, 0, 0, 0, 8, Set.of(), List.of(), 1, DevelopmentPassive.NONE,
                List.of(new CardAbility(AbilityTrigger.ACTIVATED,
                        AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 1, 0)));
    }

    private CardDefinition plainDef(String id, CardType type) {
        return new CardDefinition(id, id, type, "TEST", 1,
                0, 0, 0, 0, type == CardType.CAPITAL ? 20 : 8, Set.of(), List.of(),
                type == CardType.STRUCTURE ? 1 : 0, DevelopmentPassive.NONE, List.of());
    }

    private CardDefinition highGroundLandDef() {
        return new CardDefinition("high", "High Land", CardType.LAND, "TEST", 1,
                0, 0, 0, 0, 8, Set.of(Keyword.HIGH_GROUND), List.of(), 1,
                DevelopmentPassive.NONE, List.of(),
                Map.of(Keyword.HIGH_GROUND, new KeywordValue(0, 2)), Set.of(), 0);
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    /** Pinger at (0,2), enemy Capital at (3,2): the trace crosses (1,2) and (2,2). */
    private GameState pingSetup() {
        GameState state = state();
        add(state, 0, plainDef("home_capital", CardType.CAPITAL), new BoardPosition(0, 5));
        add(state, 1, plainDef("enemy_capital", CardType.CAPITAL), new BoardPosition(3, 2));
        return state;
    }

    @Test void capitalStrikerFiresWhenTheSightLineIsClear() {
        GameState state = pingSetup();
        CardInstance pinger = add(state, 0, pingerDef(), new BoardPosition(0, 2));

        ActionResult result = engine.apply(state, new GameAction.ActivateAbility(0, pinger.instanceId()));

        assertTrue(result.accepted(), result.message());
        CardInstance capital = state.battlefieldCards(1).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL).findFirst().orElseThrow();
        assertEquals(1, capital.damage());
    }

    @Test void enemyStructureOnTheSightLineBlocksTheActivation() {
        GameState state = pingSetup();
        CardInstance pinger = add(state, 0, pingerDef(), new BoardPosition(0, 2));
        add(state, 1, plainDef("screen", CardType.STRUCTURE), new BoardPosition(1, 2));

        ActionResult result = engine.apply(state, new GameAction.ActivateAbility(0, pinger.instanceId()));

        assertFalse(result.accepted());
        assertTrue(result.message().contains("cover"), result.message());
        CardInstance capital = state.battlefieldCards(1).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL).findFirst().orElseThrow();
        assertEquals(0, capital.damage(), "a blocked activation deals no damage");
    }

    @Test void strikerOnHighGroundShootsOverTheScreen() {
        GameState state = pingSetup();
        BoardPosition perch = new BoardPosition(0, 2);
        add(state, 0, highGroundLandDef(), perch);
        CardInstance pinger = add(state, 0, pingerDef(), perch);
        add(state, 1, plainDef("screen", CardType.STRUCTURE), new BoardPosition(1, 2));

        ActionResult result = engine.apply(state, new GameAction.ActivateAbility(0, pinger.instanceId()));

        assertTrue(result.accepted(), result.message());
    }

    @Test void destroyedTriggerStillIgnoresCover() {
        // Only aimed (activated) strikes need a sight line; deathrattles are not aimed.
        GameState state = pingSetup();
        CardInstance spire = add(state, 0, new CardDefinition("spire", "Spire", CardType.STRUCTURE, "TEST", 1,
                0, 0, 0, 0, 8, Set.of(), List.of(), 1, DevelopmentPassive.NONE,
                List.of(new CardAbility(AbilityTrigger.DESTROYED,
                        AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 0))), new BoardPosition(0, 2));
        add(state, 1, plainDef("screen", CardType.STRUCTURE), new BoardPosition(1, 2));

        state.destroy(spire);

        CardInstance capital = state.battlefieldCards(1).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL).findFirst().orElseThrow();
        assertEquals(2, capital.damage());
    }
}
