package com.infiniteconquest.gui;

/** A completed visual action gets a short readable beat before the bot may mutate state again. */
final class BotPresentationPacer {
    static final long SETTLE_NANOS = 180_000_000L;
    private long nextAction;
    boolean ready(long now, boolean presenting) {
        if (presenting) {
            nextAction = now + SETTLE_NANOS;
            return false;
        }
        return now >= nextAction;
    }
    void acted(long now) { nextAction = now + SETTLE_NANOS; }
}
