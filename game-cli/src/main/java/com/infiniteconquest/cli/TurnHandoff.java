package com.infiniteconquest.cli;

import com.infiniteconquest.core.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public final class TurnHandoff {
    static final String CLEAR_SCREEN = "\033[H\033[2J";

    public void awaitReady(GameState state, BufferedReader input, PrintStream output) throws IOException {
        clear(output);
        output.println("Pass the device to Player " + state.activePlayer() + ".");
        output.println("Player " + state.activePlayer() + ", press Enter when ready to reveal your hand.");
        if (input.readLine() == null) return;
        clear(output);
    }

    void clear(PrintStream output) {
        output.print(CLEAR_SCREEN);
        output.flush();
    }
}
