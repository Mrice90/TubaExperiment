package com.infiniteconquest.core;

public record BoardPosition(int x, int y) {
    public static final int WIDTH = 4;
    public static final int HEIGHT = 6;
    public static final int PLOT_HEIGHT = 3;

    public BoardPosition {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            throw new IllegalArgumentException("Position outside 4x6 battlefield: " + x + "," + y);
        }
    }

    /** Diagonal and orthogonal steps each cost one space. */
    public int distanceTo(BoardPosition other) {
        return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
    }

    public boolean adjacentTo(BoardPosition other) {
        return distanceTo(other) == 1;
    }

    public boolean isOnPlayerSide(int playerId) {
        if (playerId == 0) return y < PLOT_HEIGHT;
        if (playerId == 1) return y >= PLOT_HEIGHT;
        throw new IllegalArgumentException("Player ID must be 0 or 1");
    }

    /** True when the position sits on the opponent's home plot. */
    public boolean isOnEnemySide(int playerId) {
        if (playerId == 0) return y >= PLOT_HEIGHT;
        if (playerId == 1) return y < PLOT_HEIGHT;
        throw new IllegalArgumentException("Player ID must be 0 or 1");
    }
}
