package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BoardStateTest {
    @Test void boardSupportsTwoFourByThreePlotsAndOrderedStacks() {
        BoardState board = new BoardState();
        BoardPosition position = new BoardPosition(3, 5);
        UUID bottom = UUID.randomUUID();
        UUID top = UUID.randomUUID();
        board.push(position, bottom);
        board.push(position, top);
        assertEquals(java.util.List.of(bottom, top), board.stackAt(position));
        assertEquals(top, board.topAt(position).orElseThrow());
        assertEquals(top, board.pop(position));
        assertEquals(bottom, board.topAt(position).orElseThrow());
        assertTrue(new BoardPosition(0, 0).isOnPlayerSide(0));
        assertTrue(new BoardPosition(0, 5).isOnPlayerSide(1));
    }

    @Test void rejectsCoordinatesOutsideFourBySix() {
        assertThrows(IllegalArgumentException.class, () -> new BoardPosition(4, 0));
        assertThrows(IllegalArgumentException.class, () -> new BoardPosition(0, 6));
    }
}
