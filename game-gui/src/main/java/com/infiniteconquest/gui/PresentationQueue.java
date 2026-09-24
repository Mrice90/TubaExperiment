package com.infiniteconquest.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serializes complete presentation snapshots behind explicit completion callbacks. */
final class PresentationQueue {
    interface Player {
        void play(PresentationSnapshot snapshot, Runnable completion);
    }

    private final Player player;
    private final Deque<PresentationSnapshot> pending = new ArrayDeque<>();
    private PresentationSnapshot active;

    PresentationQueue(Player player) {
        this.player = Objects.requireNonNull(player);
    }

    void enqueue(PresentationSnapshot snapshot) {
        pending.addLast(Objects.requireNonNull(snapshot));
        playNextIfIdle();
    }

    boolean isPlaying() { return active != null; }
    int pendingCount() { return pending.size(); }
    PresentationSnapshot active() { return active; }

    private void playNextIfIdle() {
        if (active != null || pending.isEmpty()) return;
        active = pending.removeFirst();
        AtomicBoolean completed = new AtomicBoolean();
        PresentationSnapshot started = active;
        player.play(started, () -> {
            if (!completed.compareAndSet(false, true)) return;
            if (active != started) throw new IllegalStateException("Presentation completed out of order");
            active = null;
            playNextIfIdle();
        });
    }
}
