package com.infiniteconquest.gui;

import com.infiniteconquest.core.BoardPosition;

import java.util.Objects;

/** Owns transient user interaction independently from authoritative game state. */
final class InteractionState {
    enum Mode {
        IDLE, HAND_SELECTED, BOARD_SELECTED, DRAGGING,
        RESOLVING, BOT_TURN, REACTION, PRESENTATION_LOCKED, GAME_OVER
    }

    private Mode mode = Mode.IDLE;
    private Mode resumeMode;
    private Mode reactionReturnMode;
    private Integer handIndex;
    private BoardPosition boardPosition;

    Mode mode() { return mode; }
    Integer handIndex() { return handIndex; }
    BoardPosition boardPosition() { return boardPosition; }
    boolean hasSelection() { return handIndex != null || boardPosition != null; }

    boolean acceptsHumanInput() {
        return mode == Mode.IDLE || mode == Mode.HAND_SELECTED
                || mode == Mode.BOARD_SELECTED || mode == Mode.DRAGGING;
    }

    void toggleHand(int index) {
        requireHumanInput();
        if (mode != Mode.DRAGGING && Objects.equals(handIndex, index)) {
            clearSelection();
            return;
        }
        selectHand(index);
    }

    void selectHand(int index) {
        requireHumanInput();
        handIndex = index;
        boardPosition = null;
        mode = Mode.HAND_SELECTED;
    }

    void toggleBoard(BoardPosition position) {
        requireHumanInput();
        if (mode != Mode.DRAGGING && Objects.equals(boardPosition, position)) {
            clearSelection();
            return;
        }
        selectBoard(position);
    }

    void selectBoard(BoardPosition position) {
        requireHumanInput();
        handIndex = null;
        boardPosition = Objects.requireNonNull(position);
        mode = Mode.BOARD_SELECTED;
    }

    void beginDrag(Integer hand, BoardPosition board) {
        requireHumanInput();
        if ((hand == null) == (board == null)) {
            throw new IllegalArgumentException("A drag needs exactly one source");
        }
        handIndex = hand;
        boardPosition = board;
        mode = Mode.DRAGGING;
    }

    void finishDrag() {
        requireHumanInput();
        if (handIndex != null) mode = Mode.HAND_SELECTED;
        else if (boardPosition != null) mode = Mode.BOARD_SELECTED;
        else mode = Mode.IDLE;
    }

    void beginResolution() { transitionTo(Mode.RESOLVING); }
    void finishResolution() { finishBlockingMode(Mode.RESOLVING, Mode.IDLE); }
    void beginBotTurn() { transitionTo(Mode.BOT_TURN); }
    void finishBotTurn() { finishBlockingMode(Mode.BOT_TURN, Mode.IDLE); }

    void beginReaction() {
        reactionReturnMode = underlyingMode() == Mode.BOT_TURN ? Mode.BOT_TURN : Mode.IDLE;
        transitionTo(Mode.REACTION);
    }

    void finishReaction() {
        finishBlockingMode(Mode.REACTION,
                reactionReturnMode == null ? Mode.IDLE : reactionReturnMode);
        reactionReturnMode = null;
    }

    void markGameOver() { transitionTo(Mode.GAME_OVER); }

    void lockPresentation() {
        clearSelectionFields();
        if (mode == Mode.PRESENTATION_LOCKED) return;
        resumeMode = mode;
        mode = Mode.PRESENTATION_LOCKED;
    }

    void finishPresentation() {
        if (mode != Mode.PRESENTATION_LOCKED) return;
        mode = resumeMode == null ? Mode.IDLE : resumeMode;
        resumeMode = null;
    }

    void clearSelection() {
        requireHumanInput();
        clearSelectionFields();
        mode = Mode.IDLE;
    }

    void reset() {
        clearSelectionFields();
        resumeMode = null;
        reactionReturnMode = null;
        mode = Mode.IDLE;
    }

    private Mode underlyingMode() {
        return mode == Mode.PRESENTATION_LOCKED && resumeMode != null ? resumeMode : mode;
    }

    private void transitionTo(Mode target) {
        clearSelectionFields();
        if (mode == Mode.PRESENTATION_LOCKED) resumeMode = target;
        else mode = target;
    }

    private void finishBlockingMode(Mode blocking, Mode destination) {
        if (mode == Mode.PRESENTATION_LOCKED) {
            if (resumeMode == blocking) resumeMode = destination;
        } else if (mode == blocking) {
            mode = destination;
        }
    }

    private void requireHumanInput() {
        if (!acceptsHumanInput()) {
            throw new IllegalStateException("Interaction is locked during " + mode);
        }
    }

    private void clearSelectionFields() {
        handIndex = null;
        boardPosition = null;
    }
}
