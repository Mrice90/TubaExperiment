package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceCompletionTest {
    @Test
    void handoffConcealsThePreviousHandUntilTheNextPlayerIsReady() throws Exception {
        GameState state = new DemoMatchFactory().create(21L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader input = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8))));

        new TurnHandoff().awaitReady(state, input, new PrintStream(bytes));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.startsWith(TurnHandoff.CLEAR_SCREEN));
        assertTrue(output.contains("Pass the device to Player " + state.activePlayer()));
        assertTrue(output.endsWith(TurnHandoff.CLEAR_SCREEN));
        assertFalse(output.contains("Hand:"));
    }

    @Test
    void inspectShowsFullHandCardAndBattlefieldStackDetails() {
        GameState state = new DemoMatchFactory().create(22L);
        CommandProcessor processor = new CommandProcessor(state);

        String activeLabel = state.activePlayer() == 0 ? "Player 1 (You)" : "Player 2 (Bot)";
        assertTrue(processor.execute("inspect 0").contains(" — " + activeLabel + " — "));
        BoardPosition capitalPosition = state.board().positions().stream()
                .filter(position -> state.board().stackAt(position).stream()
                        .map(id -> state.card(id).orElseThrow())
                        .anyMatch(card -> card.definition().type() == CardType.CAPITAL))
                .findFirst().orElseThrow();
        String capital = processor.execute("inspect " + capitalPosition.x() + " " + capitalPosition.y());
        assertTrue(capital.contains("stack (bottom to top)"));
        assertTrue(capital.contains("Capital"));
        assertTrue(capital.contains("damage 0/20"));
    }

    @Test
    void legalAttackHintsExcludeTargetsBehindBlockingStructures() {
        GameState state = new GameState(23L);
        CardInstance attacker = add(state, 0,
                new CardDefinition("archer", "Archer", CardType.CHARACTER, "DEV", 0, 4, 1, 1, 3),
                new BoardPosition(0, 0));
        add(state, 1, new CardDefinition("wall", "Wall", CardType.STRUCTURE, "DEV", 0, 0, 0, 0, 0, 5),
                new BoardPosition(0, 1));
        add(state, 1, new CardDefinition("target", "Target", CardType.CHARACTER, "DEV", 0, 1, 1, 1, 1),
                new BoardPosition(0, 2));

        assertEquals(java.util.Set.of(new BoardPosition(0, 1)),
                new GameEngine().legalAttackDestinations(state, attacker.instanceId()));
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(java.util.UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }
}
