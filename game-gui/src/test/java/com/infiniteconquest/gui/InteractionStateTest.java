package com.infiniteconquest.gui;

import com.infiniteconquest.core.BoardPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractionStateTest {
    @Test
    void handAndBoardSelectionsAreMutuallyExclusive() {
        InteractionState state = new InteractionState();
        state.selectHand(2);
        assertEquals(InteractionState.Mode.HAND_SELECTED, state.mode());
        assertEquals(2, state.handIndex());
        assertNull(state.boardPosition());

        BoardPosition cell = new BoardPosition(1, 1);
        state.selectBoard(cell);
        assertEquals(InteractionState.Mode.BOARD_SELECTED, state.mode());
        assertNull(state.handIndex());
        assertEquals(cell, state.boardPosition());
    }

    @Test
    void selectingTheSameSourceAgainReturnsToIdle() {
        InteractionState state = new InteractionState();
        state.toggleHand(1);
        state.toggleHand(1);
        assertEquals(InteractionState.Mode.IDLE, state.mode());
        assertFalse(state.hasSelection());
    }

    @Test
    void dragRetainsItsSourceUntilTheDropIsResolved() {
        InteractionState state = new InteractionState();
        state.beginDrag(3, null);
        assertEquals(InteractionState.Mode.DRAGGING, state.mode());
        state.finishDrag();
        assertEquals(InteractionState.Mode.HAND_SELECTED, state.mode());
    }

    @Test
    void dragRequiresExactlyOneSource() {
        InteractionState state = new InteractionState();
        assertThrows(IllegalArgumentException.class, () -> state.beginDrag(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> state.beginDrag(0, new BoardPosition(0, 0)));
    }

    @Test
    void blockingModesRejectHumanSelection() {
        InteractionState state = new InteractionState();
        state.beginResolution();
        assertFalse(state.acceptsHumanInput());
        assertThrows(IllegalStateException.class, () -> state.selectHand(0));
        state.finishResolution();
        assertEquals(InteractionState.Mode.IDLE, state.mode());
    }

    @Test
    void reactionDuringPresentationReturnsToBotTurn() {
        InteractionState state = new InteractionState();
        state.beginBotTurn();
        state.lockPresentation();
        state.beginReaction();
        state.finishReaction();
        state.finishPresentation();
        assertEquals(InteractionState.Mode.BOT_TURN, state.mode());
    }

    @Test
    void finishingBotTurnDuringPresentationUnlocksToIdle() {
        InteractionState state = new InteractionState();
        state.beginBotTurn();
        state.lockPresentation();
        state.finishBotTurn();
        state.finishPresentation();
        assertEquals(InteractionState.Mode.IDLE, state.mode());
        assertTrue(state.acceptsHumanInput());
    }

    @Test
    void gameOverSurvivesAnActivePresentationLock() {
        InteractionState state = new InteractionState();
        state.beginResolution();
        state.lockPresentation();
        state.markGameOver();
        state.finishPresentation();
        assertEquals(InteractionState.Mode.GAME_OVER, state.mode());
        assertFalse(state.acceptsHumanInput());
    }
}
