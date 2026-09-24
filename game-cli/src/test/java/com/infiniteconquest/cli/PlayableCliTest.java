package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayableCliTest {
    @Test
    void demoCreatesValidatedSixtyCardDecksAndDeployedCapitals() {
        DemoMatchFactory factory = new DemoMatchFactory();
        assertEquals(60, factory.demoDeck().size());

        GameState state = factory.create(42L);
        long playerZeroCapitals = state.board().positions().stream()
                .flatMap(position -> state.board().stackAt(position).stream())
                .map(id -> state.card(id).orElseThrow())
                .filter(card -> card.owner() == 0 && card.definition().type() == CardType.CAPITAL)
                .count();
        long playerOneCapitals = state.board().positions().stream()
                .flatMap(position -> state.board().stackAt(position).stream())
                .map(id -> state.card(id).orElseThrow())
                .filter(card -> card.owner() == 1 && card.definition().type() == CardType.CAPITAL)
                .count();

        assertEquals(1, playerZeroCapitals);
        assertEquals(1, playerOneCapitals);
        assertEquals(5, state.player(state.startingPlayer()).hand().size());
        assertEquals(6, state.player(1 - state.startingPlayer()).hand().size());
    }

    @Test
    void rendererShowsFullBoardHandGpAndHiddenOpponentCount() {
        GameState state = new DemoMatchFactory().create(7L);
        String rendered = new BattlefieldRenderer().render(state);

        assertTrue(rendered.contains("Turn 1 | Player " + (state.activePlayer() + 1)));
        assertTrue(rendered.contains("GP " + state.player(state.activePlayer()).currentGp()));
        assertTrue(rendered.contains("x0"));
        assertTrue(rendered.contains("y5"));
        assertTrue(rendered.contains(state.activePlayer() == 0
                ? "Player 1 (You) hand:" : "Player 2 (Bot) hand:"));
        assertTrue(rendered.contains("Opponent: 6 cards in hand"));
        assertFalse(rendered.contains("Player 1 Capital —"));
    }

    @Test
    void processorSupportsHelpActionsTurnPassingAndSafeErrors() {
        GameState state = new DemoMatchFactory().create(9L);
        CommandProcessor processor = new CommandProcessor(state);

        assertTrue(processor.execute("help").contains("play <hand#>"));
        assertTrue(processor.execute("actions").contains("end"));
        assertEquals("Invalid command: Expected a number", processor.execute("move x 0 1 1"));
        int first = state.activePlayer();
        assertTrue(processor.execute("end").startsWith("OK:"));
        assertEquals(1 - first, state.activePlayer());
        assertEquals("Match closed.", processor.execute("quit"));
        assertTrue(processor.quitRequested());
    }
}
