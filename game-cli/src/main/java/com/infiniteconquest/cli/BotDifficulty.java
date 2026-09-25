package com.infiniteconquest.cli;

/**
 * Bot skill levels, themed as the Greek heroic journey. Every level plays the
 * same game with the same legal actions; they differ only in how actions are
 * chosen.
 */
public enum BotDifficulty {
    /** Learning the ropes: clears the board like a cautious novice, rarely goes for the win. */
    MORTAL("Mortal", "Learning the ropes — cautious, often misses the winning move."),
    /** The classic challenge: the original heuristic tactician, unchanged. */
    HERO("Hero", "A competent tactician — the classic challenge."),
    /** A ruthless tactician: simulates each candidate move a turn ahead before choosing. */
    DEMIGOD("Demigod", "Ruthless tactician — thinks a move ahead.");

    private final String title;
    private final String description;

    BotDifficulty(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** Display name shown in the difficulty selector. */
    public String title() { return title; }

    /** One-line plain-language description shown under the selector. */
    public String description() { return description; }

    @Override public String toString() { return title; }
}
