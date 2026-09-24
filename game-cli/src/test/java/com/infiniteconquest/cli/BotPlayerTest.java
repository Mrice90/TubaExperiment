package com.infiniteconquest.cli;

import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotPlayerTest {
    @Test
    void botUsesOnlyLegalActionsAndEndsItsTurn() {
        GameState state = new DemoMatchFactory().create(101L);
        CommandProcessor commands = new CommandProcessor(state);
        BotPlayer bot = new BotPlayer();
        if (state.activePlayer() == 0) commands.execute("end");
        List<String> decisions = new ArrayList<>();

        for (int safety = 0; safety < 100 && state.activePlayer() == BotPlayer.BOT_ID
                && state.phase() != Phase.GAME_OVER; safety++) {
            BotPlayer.Decision decision = bot.takeNextAction(state, commands);
            decisions.add(decision.command());
            assertTrue(decision.result().startsWith("OK:"), decision.toString());
        }

        assertFalse(decisions.isEmpty());
        assertTrue(decisions.contains("end") || state.phase() == Phase.GAME_OVER);
        if (state.phase() != Phase.GAME_OVER) assertEquals(0, state.activePlayer());
    }

    @Test
    void sameSeedProducesDeterministicBotOpening() {
        assertEquals(openingDecisions(202L), openingDecisions(202L));
    }

    @Test
    void rendererClearlyLabelsHumanAndBot() {
        GameState state = new DemoMatchFactory().create(303L);
        String output = new BattlefieldRenderer().render(state);

        assertTrue(output.contains(state.activePlayer() == 0 ? "Player 1 (You)" : "Player 2 (Bot)"));
        assertTrue(output.contains("BOT K:Player 2"));
        String inactiveLabel = state.activePlayer() == 0 ? "Player 2 (Bot)" : "Player 1 (You)";
        assertFalse(output.contains(inactiveLabel + " hand:"));
    }

    private List<String> openingDecisions(long seed) {
        GameState state = new DemoMatchFactory().create(seed);
        CommandProcessor commands = new CommandProcessor(state);
        BotPlayer bot = new BotPlayer();
        if (state.activePlayer() == 0) commands.execute("end");
        List<String> result = new ArrayList<>();
        for (int safety = 0; safety < 30 && state.activePlayer() == BotPlayer.BOT_ID; safety++) {
            BotPlayer.Decision decision = bot.takeNextAction(state, commands);
            result.add(decision.command());
        }
        return result;
    }
}
