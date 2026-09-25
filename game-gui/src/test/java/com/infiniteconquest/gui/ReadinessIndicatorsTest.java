package com.infiniteconquest.gui;

import com.infiniteconquest.cli.ActionHints;
import com.infiniteconquest.cli.CommandProcessor;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.PrototypeCardPool;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardInstance;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The glanceable readiness markers: a Character that can still attack shows
 * up in attackReady, a top-of-stack card with a legal activated ability shows
 * up in abilityReady — and both vanish the moment the action is spent.
 */
class ReadinessIndicatorsTest {
    private static final BoardPosition ATTACKER_AT = new BoardPosition(1, 2);
    private static final BoardPosition DEFENDER_AT = new BoardPosition(1, 4);
    private static final BoardPosition SPIRE_AT = new BoardPosition(2, 2);

    private GameState battleState() {
        DemoMatchFactory factory = new DemoMatchFactory();
        PrototypeCardPool pool = factory.pool();
        List<CardDefinition> deck = factory.demoDeck();
        CardDefinition capital = factory.capitals().defaultForDeck(deck).orElse(null);
        GameState state = factory.create(42L, deck, deck, capital, capital,
                new BoardPosition(1, 0),
                new BoardPosition(BoardPosition.WIDTH - 2, BoardPosition.HEIGHT - 1));
        int active = state.activePlayer();
        place(state, pool.require("neo_proto_zephyr_scout"), ATTACKER_AT, active);
        place(state, pool.require("neo_proto_talus_defender"), DEFENDER_AT, 1 - active);
        place(state, pool.require("zeus_ability_oracle_spire"), SPIRE_AT, active);
        state.player(active).restoreGp(10);
        return state;
    }

    private static void place(GameState state, CardDefinition definition,
                              BoardPosition position, int owner) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
    }

    private static ReadinessIndicators.Readiness readiness(GameState state) {
        List<String> legal = new ActionHints().forActivePlayer(state, new GameEngine());
        return ReadinessIndicators.compute(legal);
    }

    @Test
    void attackMarkerAppearsWhenCharacterCanAttack() {
        GameState state = battleState();
        ReadinessIndicators.Readiness readiness = readiness(state);
        assertTrue(readiness.attackReady().contains(ATTACKER_AT),
                "a fresh Character in range of an enemy should be marked ready to attack");
        assertFalse(readiness.attackReady().contains(DEFENDER_AT),
                "the defender belongs to the waiting player and must not be marked");
    }

    @Test
    void attackMarkerDisappearsAfterTheAttackIsSpent() {
        GameState state = battleState();
        CommandProcessor commands = new CommandProcessor(state);
        String attack = "attack " + ATTACKER_AT.x() + " " + ATTACKER_AT.y()
                + " " + DEFENDER_AT.x() + " " + DEFENDER_AT.y();
        String result = commands.execute(attack);
        assertTrue(result.startsWith("OK:"), "the fixture attack should be legal, was: " + result);
        ReadinessIndicators.Readiness after = readiness(state);
        assertFalse(after.attackReady().contains(ATTACKER_AT),
                "after attacking, the Character must lose its ready marker");
    }

    @Test
    void abilityMarkerAppearsWhenActivationIsLegal() {
        GameState state = battleState();
        ReadinessIndicators.Readiness readiness = readiness(state);
        assertTrue(readiness.abilityReady().contains(SPIRE_AT),
                "a top-of-stack Structure with an affordable unused ability should be marked");
    }

    @Test
    void abilityMarkerDisappearsAfterTheAbilityIsUsed() {
        GameState state = battleState();
        CommandProcessor commands = new CommandProcessor(state);
        String activate = "activate " + SPIRE_AT.x() + " " + SPIRE_AT.y();
        assertTrue(commands.execute(activate).startsWith("OK:"),
                "the fixture activation should be legal");
        ReadinessIndicators.Readiness after = readiness(state);
        assertFalse(after.abilityReady().contains(SPIRE_AT),
                "after firing, the ability marker must clear for the rest of the turn");
    }

    @Test
    void exhaustedCardsShowNoMarkers() {
        GameState state = battleState();
        CommandProcessor commands = new CommandProcessor(state);
        commands.execute("attack " + ATTACKER_AT.x() + " " + ATTACKER_AT.y()
                + " " + DEFENDER_AT.x() + " " + DEFENDER_AT.y());
        commands.execute("activate " + SPIRE_AT.x() + " " + SPIRE_AT.y());
        ReadinessIndicators.Readiness after = readiness(state);
        assertFalse(after.attackReady().contains(ATTACKER_AT));
        assertFalse(after.abilityReady().contains(SPIRE_AT));
    }
}
