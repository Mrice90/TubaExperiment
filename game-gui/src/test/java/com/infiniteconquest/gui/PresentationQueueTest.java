package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PresentationQueueTest {
    @Test
    void playsSnapshotsStrictlyInFifoOrder() {
        List<String> started = new ArrayList<>();
        List<Runnable> completions = new ArrayList<>();
        PresentationQueue queue = new PresentationQueue((snapshot, completion) -> {
            started.add(snapshot.command());
            completions.add(completion);
        });

        queue.enqueue(snapshot("first"));
        queue.enqueue(snapshot("second"));
        queue.enqueue(snapshot("third"));

        assertEquals(List.of("first"), started);
        assertEquals(2, queue.pendingCount());

        completions.get(0).run();
        assertEquals(List.of("first", "second"), started);
        assertEquals(1, queue.pendingCount());

        completions.get(1).run();
        assertEquals(List.of("first", "second", "third"), started);
        assertEquals(0, queue.pendingCount());
    }

    @Test
    void duplicateCompletionCannotSkipTheNextSnapshot() {
        List<Runnable> completions = new ArrayList<>();
        List<String> started = new ArrayList<>();
        PresentationQueue queue = new PresentationQueue((snapshot, completion) -> {
            started.add(snapshot.command());
            completions.add(completion);
        });

        queue.enqueue(snapshot("first"));
        queue.enqueue(snapshot("second"));
        Runnable firstDone = completions.get(0);
        firstDone.run();
        firstDone.run();

        assertEquals(List.of("first", "second"), started);
        assertTrue(queue.isPlaying());
        assertEquals("second", queue.active().command());
    }

    @Test
    void reentrantEnqueueWaitsForTheActivePresentation() {
        List<String> started = new ArrayList<>();
        PresentationQueue[] holder = new PresentationQueue[1];
        holder[0] = new PresentationQueue((snapshot, completion) -> {
            started.add(snapshot.command());
            if (snapshot.command().equals("first")) holder[0].enqueue(snapshot("nested"));
            completion.run();
        });

        holder[0].enqueue(snapshot("first"));

        assertEquals(List.of("first", "nested"), started);
        assertFalse(holder[0].isPlaying());
    }

    private PresentationSnapshot snapshot(String command) {
        PresentationSnapshot.Frame empty = new PresentationSnapshot.Frame(Map.of());
        return PresentationSnapshot.between(command, empty, empty);
    }
}
