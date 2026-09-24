package com.infiniteconquest.gui.net;

/**
 * Byte transport for the network client: sends protocol lines and delivers
 * received lines to a listener. TCP is the production implementation;
 * tests use an in-memory loopback. Listener callbacks arrive on the
 * transport's own thread; {@link NetClient} forwards them to its executor.
 */
public interface NetTransport {
    interface Listener {
        void onLine(String line);
        void onClosed(Throwable cause);
    }

    void setListener(Listener listener);
    void send(String line);
    void close();

    /** Sends a pre-encoded protocol envelope; the transport applies line framing. */
    default void sendEnvelope(Object envelope) {
        send(com.infiniteconquest.net.Protocol.encode(envelope));
    }
}
