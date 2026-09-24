package com.infiniteconquest.gui.net;

import com.infiniteconquest.net.EmbeddedServer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory {@link NetTransport} bridged to an {@link EmbeddedServer} peer.
 * No TCP: the server's game thread delivers lines straight to the transport
 * listener, and client sends go to {@link EmbeddedServer#receive}.
 */
final class MemoryTransport implements NetTransport {
    private final EmbeddedServer server;
    private final EmbeddedServer.Peer peer;
    private final AtomicReference<Listener> listener = new AtomicReference<>();
    private volatile boolean closed;

    MemoryTransport(EmbeddedServer server) {
        this.server = server;
        this.peer = new EmbeddedServer.Peer() {
            @Override public void send(String line) {
                Listener l = listener.get();
                if (l != null && !closed) l.onLine(line);
            }

            @Override public void close() {
                if (!closed) {
                    closed = true;
                    Listener l = listener.get();
                    if (l != null) l.onClosed(null);
                }
            }
        };
        server.attachPeer(peer);
    }

    /** Test-only: inject a raw server line as if it arrived over the wire. */
    void deliver(String line) {
        Listener l = listener.get();
        if (l != null) l.onLine(line);
    }

    @Override public void setListener(Listener listener) {
        this.listener.set(listener);
    }

    @Override public void send(String line) {
        if (!closed) server.receive(peer, line);
    }

    @Override public void close() {
        peer.close();
    }
}
