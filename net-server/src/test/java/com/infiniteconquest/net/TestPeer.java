package com.infiniteconquest.net;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link EmbeddedServer.Peer} for tests: no sockets, so session logic
 * can be verified where loopback TCP is unavailable. {@link #send} (called by the
 * server's game thread) enqueues lines; {@link #drain()} dequeues them. The test
 * delivers client lines via {@link EmbeddedServer#receive}.
 */
public final class TestPeer implements EmbeddedServer.Peer {
    private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    @Override
    public void send(String line) {
        if (!closed) inbox.add(line);
    }

    @Override
    public void close() {
        closed = true;
    }

    /** Blocks up to the timeout for the next line the server sent this peer. */
    public String next(long timeout, TimeUnit unit) throws InterruptedException {
        return inbox.poll(timeout, unit);
    }

    /** Discards any queued lines. */
    public void drain() {
        inbox.clear();
    }

    public boolean isClosed() { return closed; }
}
