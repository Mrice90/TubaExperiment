package com.infiniteconquest.core;

public record MatchRules(int initialHandSize, int startingGp, int secondPlayerStartingGp,
                         int cardsDrawnAtTurnStart, BoardGeometry geometry) {
    public MatchRules(int hand, int gp, int secondGp, int draw) {
        this(hand, gp, secondGp, draw, BoardGeometry.SQUARE);
    }
    public MatchRules {
        java.util.Objects.requireNonNull(geometry);
        if (initialHandSize < 0) throw new IllegalArgumentException("Initial hand size cannot be negative");
        if (startingGp < 0 || secondPlayerStartingGp < 0 || cardsDrawnAtTurnStart < 0) {
            throw new IllegalArgumentException("Rule values cannot be negative");
        }
    }
    public static MatchRules current() { return new MatchRules(5, 0, 1, 1); }
    public static MatchRules hex() { return new MatchRules(5, 0, 1, 1, BoardGeometry.HEX); }
    public int initialHandSizeFor(boolean startsSecond) {
        return initialHandSize + (startsSecond ? 1 : 0);
    }
}
