package com.infiniteconquest.cli;

import com.infiniteconquest.core.AbilityEffectType;
import com.infiniteconquest.core.AbilityTrigger;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardAbility;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardInstance;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DevelopmentPassive;
import com.infiniteconquest.core.GameAction;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bots must consider activated abilities with judgment, not a flat score:
 * HERO fires a lethal pinger to win, values chip damage and card draw
 * sensibly, and never wastes a heal on a full-health target; MORTAL keeps
 * its novice aversion to non-lethal capital strikes (including pingers) but
 * still takes the winning hit; DEMIGOD's lookahead finds the lethal ping.
 */
class BotActivatedAbilityTest {

    private static CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    private static CardDefinition capital(String id, int hitPoints) {
        return new CardDefinition(id, "Test " + id, CardType.CAPITAL, "T", 0, 0, 0, 0, 0, hitPoints);
    }

    private static CardDefinition abilityCard(String id, AbilityEffectType effect, int amount, int gpCost) {
        return new CardDefinition(id, "Test " + id, CardType.STRUCTURE, "T", 0, 0, 0, 0, 0, 5,
                Set.of(), List.of(), 0, DevelopmentPassive.NONE,
                List.of(new CardAbility(AbilityTrigger.ACTIVATED, effect, amount, gpCost)));
    }

    /** Fresh game with the bot (player 1) to move. */
    private static GameState botTurn(long seed) {
        GameState state = new GameState(seed);
        if (state.activePlayer() == 0) new GameEngine().apply(state, new GameAction.EndTurn(0));
        assertEquals(1, state.activePlayer());
        return state;
    }

    private static void giveGp(GameState state, int player, int gp) {
        state.player(player).spendGp(state.player(player).currentGp());
        state.player(player).restoreGp(gp);
    }

    private static void damageEnemyCapital(GameState state, int damage) {
        state.battlefieldCards(0).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .forEach(card -> card.addDamage(damage));
    }

    @Test
    void heroFiresLethalPingerToWinTheGame() {
        GameState state = botTurn(101L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4));
        damageEnemyCapital(state, 18); // 2 HP left
        add(state, 1, abilityCard("pinger", AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 3),
                new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"), decision.command());
    }

    @Test
    void heroValuesChipPingerAbovePassing() {
        GameState state = botTurn(102L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4));
        add(state, 1, abilityCard("pinger", AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 3),
                new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"), decision.command());
    }

    @Test
    void heroFiresCardDrawEngine() {
        GameState state = botTurn(103L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4));
        add(state, 1, abilityCard("lattice", AbilityEffectType.DRAW_CARD, 2, 3), new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"), decision.command());
    }

    @Test
    void heroDoesNotWasteHealOnFullHealthTargets() {
        GameState state = botTurn(104L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4)); // full HP: nothing to heal
        add(state, 1, abilityCard("tidewell", AbilityEffectType.HEAL_CAPITAL, 2, 2), new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertEquals("end", decision.command());
    }

    @Test
    void heroHealsDamagedCapital() {
        GameState state = botTurn(108L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        CardInstance ownCapital = add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4));
        ownCapital.addDamage(6); // 6 HP missing: the heal has real work to do
        add(state, 1, abilityCard("tidewell", AbilityEffectType.HEAL_CAPITAL, 2, 2), new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"), decision.command());
    }

    @Test
    void mortalSkipsChipPingerToClearTheBoard() {
        GameState state = botTurn(105L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(2, 4));
        add(state, 1, abilityCard("pinger", AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 3),
                new BoardPosition(1, 3));
        add(state, 1, new CardDefinition("b_ch", "Bruiser", CardType.CHARACTER, "T", 0, 3, 3, 1, 1, 3),
                new BoardPosition(2, 3));
        add(state, 0, new CardDefinition("e_ch", "Target", CardType.CHARACTER, "T", 0, 1, 1, 1, 1, 2),
                new BoardPosition(2, 2));
        giveGp(state, 1, 10);

        // Any seed works: the filtered pinger never appears, so MORTAL's
        // exploration cannot pick it either.
        BotPlayer mortal = new BotPlayer(BotDifficulty.MORTAL, new Random(99L));
        BotPlayer.Decision decision = mortal.takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertFalse(decision.command().startsWith("activate"),
                "novice should clear the board, not ping: " + decision.command());
    }

    @Test
    void mortalStillTakesLethalPinger() {
        GameState state = botTurn(106L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(2, 4));
        damageEnemyCapital(state, 18); // 2 HP left: the ping is lethal
        add(state, 1, abilityCard("pinger", AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 3),
                new BoardPosition(1, 3));
        add(state, 1, new CardDefinition("b_ch", "Bruiser", CardType.CHARACTER, "T", 0, 3, 3, 1, 1, 3),
                new BoardPosition(2, 3));
        add(state, 0, new CardDefinition("e_ch", "Target", CardType.CHARACTER, "T", 0, 1, 1, 1, 1, 2),
                new BoardPosition(2, 2));
        giveGp(state, 1, 10);

        // Hunt a seed whose first draw does not trigger exploration, so the
        // assertion is on the novice's ranking, not its noise.
        long seed = 0;
        while (new Random(seed).nextDouble() < BotPlayer.MORTAL_MISTAKE_RATE) seed++;
        BotPlayer mortal = new BotPlayer(BotDifficulty.MORTAL, new Random(seed));
        BotPlayer.Decision decision = mortal.takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"),
                "even a novice takes the winning hit: " + decision.command());
    }

    @Test
    void demigodFindsLethalPingerThroughLookahead() {
        GameState state = botTurn(107L);
        add(state, 0, capital("e_cap", 20), new BoardPosition(1, 1));
        add(state, 1, capital("b_cap", 20), new BoardPosition(1, 4));
        damageEnemyCapital(state, 18); // 2 HP left
        add(state, 1, abilityCard("pinger", AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 3),
                new BoardPosition(1, 3));
        giveGp(state, 1, 10);

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.DEMIGOD).takeNextAction(state, new CommandProcessor(state), 1);

        assertTrue(decision.result().startsWith("OK:"), decision.toString());
        assertTrue(decision.command().startsWith("activate"), decision.command());
    }
}
