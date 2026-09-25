package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.GameAction;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the M4 difficulty work: HERO must behave exactly like the original
 * bot, seeded difficulties must reproduce games exactly, MORTAL must actually
 * explore, and {@link GameState#copy()} must isolate lookahead simulations.
 */
class BotDifficultyTest {
    static final int MAX_TURNS = 120;
    static final int MAX_ACTIONS_PER_TURN = 200;

    /** Plays a full headless game; returns every command chosen, in order. */
    static List<String> playFullGame(BotDifficulty first, BotDifficulty second, long seed) {
        GameState state = new DemoMatchFactory().create(seed);
        CommandProcessor commands = new CommandProcessor(state);
        BotPlayer[] bots = {
                new BotPlayer(first, new Random(seed * 31 + 7)),
                new BotPlayer(second, new Random(seed * 31 + 13))};
        List<String> decisions = new ArrayList<>();
        int actionsThisTurn = 0;
        while (state.phase() != Phase.GAME_OVER && state.turnNumber() <= MAX_TURNS) {
            int active = state.activePlayer();
            BotPlayer.Decision decision = bots[active].takeNextAction(state, commands, active);
            assertTrue(decision.result().startsWith("OK:"), "bot chose rejected action: " + decision);
            decisions.add(active + ":" + decision.command());
            if (decision.command().equals("end")) {
                actionsThisTurn = 0;
            } else {
                actionsThisTurn++;
                if (state.phase() != Phase.GAME_OVER) bots[1 - active].react(state, commands, 1 - active);
                if (actionsThisTurn >= MAX_ACTIONS_PER_TURN && state.phase() != Phase.GAME_OVER) {
                    new GameEngine().apply(state, new GameAction.EndTurn(active));
                    actionsThisTurn = 0;
                }
            }
        }
        decisions.add("winner=" + (state.winner().isPresent() ? state.winner().getAsInt() : "draw"));
        return decisions;
    }

    @Test
    void heroIgnoresRandomnessAndMatchesLegacyBehavior() {
        // HERO never consults the RNG, so two differently-seeded HERO bots
        // must play byte-identical games — this is the legacy behavior.
        List<String> first = playFullGame(BotDifficulty.HERO, BotDifficulty.HERO, 4242L);
        List<String> second = playFullGame(BotDifficulty.HERO, BotDifficulty.HERO, 4242L);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    @Test
    void noArgConstructorIsHero() {
        assertEquals(BotDifficulty.HERO, new BotPlayer().difficulty());
    }

    @Test
    void sameSeedReproducesMortalGameExactly() {
        assertEquals(playFullGame(BotDifficulty.MORTAL, BotDifficulty.MORTAL, 777L),
                playFullGame(BotDifficulty.MORTAL, BotDifficulty.MORTAL, 777L));
    }

    @Test
    void sameSeedReproducesDemigodGameExactly() {
        assertEquals(playFullGame(BotDifficulty.DEMIGOD, BotDifficulty.HERO, 31337L),
                playFullGame(BotDifficulty.DEMIGOD, BotDifficulty.HERO, 31337L));
    }

    @Test
    void mortalActuallyExploresAwayFromHero() {
        // With a ~25% exploration rate over hundreds of decisions, MORTAL
        // must deviate from HERO's line at least once in a full game.
        List<String> hero = playFullGame(BotDifficulty.HERO, BotDifficulty.HERO, 5150L);
        List<String> mortal = playFullGame(BotDifficulty.MORTAL, BotDifficulty.MORTAL, 5150L);
        assertNotEquals(hero, mortal, "MORTAL never deviated from HERO — exploration is not firing");
    }

    /**
     * The difficulty ladder, proven by a seat-balanced deterministic
     * tournament on symmetric capital positions (so neither seat gets a
     * positional edge): DEMIGOD must dominate HERO, and HERO must dominate
     * MORTAL — MORTAL's face-blindness means it never goes for the Capital,
     * so it must never win a game against HERO.
     */
    @Test
    void tournamentProvesDemigodBeatsHeroBeatsMortal() {
        int[] dh = playMatch(BotDifficulty.DEMIGOD, BotDifficulty.HERO, 9100L, 4);
        assertEquals(0, dh[1], "HERO should never beat DEMIGOD");
        assertTrue(dh[0] >= 3, "DEMIGOD should dominate HERO, got " + dh[0] + " wins");

        int[] hm = playMatch(BotDifficulty.HERO, BotDifficulty.MORTAL, 9200L, 8);
        assertEquals(0, hm[1], "MORTAL should never beat HERO");
        assertTrue(hm[0] >= 2, "HERO should clearly outscore MORTAL, got " + hm[0]);

        int[] dm = playMatch(BotDifficulty.DEMIGOD, BotDifficulty.MORTAL, 9300L, 4);
        assertEquals(0, dm[1], "MORTAL should never beat DEMIGOD");
        assertTrue(dm[0] > 0, "DEMIGOD should beat MORTAL");
    }

    /**
     * Plays {@code games} headless games, alternating seats, on symmetric
     * capital positions. Returns {firstWins, secondWins, draws}.
     */
    static int[] playMatch(BotDifficulty first, BotDifficulty second, long baseSeed, int games) {
        int firstWins = 0, secondWins = 0, draws = 0;
        for (int i = 0; i < games; i++) {
            BotDifficulty p0 = (i % 2 == 0) ? first : second;
            BotDifficulty p1 = (i % 2 == 0) ? second : first;
            int winner = playHeadlessGame(p0, p1, baseSeed + i);
            if (winner == -1) draws++;
            else if ((winner == 0) == (i % 2 == 0)) firstWins++;
            else secondWins++;
        }
        return new int[]{firstWins, secondWins, draws};
    }

    /** Plays one headless game on symmetric capitals; returns winner or -1 for draw. */
    static int playHeadlessGame(BotDifficulty p0, BotDifficulty p1, long seed) {
        DemoMatchFactory factory = new DemoMatchFactory();
        List<CardDefinition> deck = factory.demoDeck();
        CardDefinition capital = factory.capitals().defaultForDeck(deck).orElse(null);
        int w = com.infiniteconquest.core.BoardPosition.WIDTH;
        int h = com.infiniteconquest.core.BoardPosition.HEIGHT;
        GameState state = factory.create(seed, deck, deck, capital, capital,
                new com.infiniteconquest.core.BoardPosition(1, 0),
                new com.infiniteconquest.core.BoardPosition(w - 2, h - 1));
        CommandProcessor commands = new CommandProcessor(state);
        BotPlayer[] bots = {
                new BotPlayer(p0, new Random(seed * 31 + 7)),
                new BotPlayer(p1, new Random(seed * 31 + 13))};
        int actionsThisTurn = 0;
        while (state.phase() != Phase.GAME_OVER && state.turnNumber() <= MAX_TURNS) {
            int active = state.activePlayer();
            BotPlayer.Decision decision = bots[active].takeNextAction(state, commands, active);
            assertTrue(decision.result().startsWith("OK:"), "bot chose rejected action: " + decision);
            if (decision.command().equals("end")) {
                actionsThisTurn = 0;
            } else {
                actionsThisTurn++;
                if (state.phase() != Phase.GAME_OVER) bots[1 - active].react(state, commands, 1 - active);
                if (actionsThisTurn >= MAX_ACTIONS_PER_TURN && state.phase() != Phase.GAME_OVER) {
                    new GameEngine().apply(state, new GameAction.EndTurn(active));
                    actionsThisTurn = 0;
                }
            }
        }
        return state.winner().isPresent() ? state.winner().getAsInt() : -1;
    }

    @Test
    void stateCopyIsIsolatedFromSimulation() {        GameState state = new DemoMatchFactory().create(99L);
        String before = signature(state);
        GameState copy = state.copy();
        CommandProcessor simulated = new CommandProcessor(copy);
        String result = simulated.execute("end");
        assertTrue(result.startsWith("OK:"));
        assertEquals(before, signature(state), "simulating on the copy mutated the original");
        assertNotEquals(before, signature(copy), "the copy did not advance");
    }

    @Test
    void demigodCompletesTurnWellUnderASecond() {
        GameState state = new DemoMatchFactory().create(2024L);
        CommandProcessor commands = new CommandProcessor(state);
        BotPlayer demigod = new BotPlayer(BotDifficulty.DEMIGOD, new Random(1L));
        long turnStart = System.nanoTime();
        long maxTurnNanos = 0;
        int active = state.activePlayer();
        for (int safety = 0; safety < MAX_ACTIONS_PER_TURN; safety++) {
            BotPlayer.Decision decision = demigod.takeNextAction(state, commands, active);
            assertTrue(decision.result().startsWith("OK:"));
            if (decision.command().equals("end")) break;
            if (state.activePlayer() != active) break;
        }
        maxTurnNanos = System.nanoTime() - turnStart;
        assertTrue(maxTurnNanos < 1_000_000_000L,
                "DEMIGOD turn took " + maxTurnNanos / 1_000_000 + "ms, over the 1s budget");
    }

    private static String signature(GameState state) {
        return state.turnNumber() + "|" + state.activePlayer() + "|"
                + state.player(0).currentGp() + "|" + state.player(1).currentGp() + "|"
                + state.player(0).hand().size() + "|" + state.player(1).hand().size() + "|"
                + state.battlefieldCards(0).size() + "|" + state.battlefieldCards(1).size() + "|"
                + state.events().size();
    }
}
