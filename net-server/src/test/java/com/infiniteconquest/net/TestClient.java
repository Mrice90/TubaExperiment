package com.infiniteconquest.net;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Minimal raw-socket client for server tests. Incoming envelopes are queued as
 * raw JSON lines; tests parse what they need via {@link Protocol}.
 */
final class TestClient implements Closeable {
    private final Socket socket;
    private final PrintWriter out;
    private final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    private final Thread reader;

    TestClient(String host, int port) throws Exception {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(
                new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) incoming.add(line);
            } catch (Exception ignored) {
            }
        }, "test-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    void send(Object message) {
        out.print(Protocol.encode(message));
        out.print('\n');
        out.flush();
    }

    /** Sends a hand-written JSON line, bypassing the encoder. */
    void sendRaw(String jsonLine) {
        out.print(jsonLine);
        out.print('\n');
        out.flush();
    }

    String next(long timeoutMs) throws InterruptedException {
        String line = incoming.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (line == null) throw new AssertionError("Timed out waiting for server message");
        return line;
    }

    String nextOfType(String type, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) throw new AssertionError("Timed out waiting for " + type);
            String line = next(remaining);
            if (Protocol.typeOf(line).equals(type)) return line;
        }
    }

    JsonNode nextJson(String type, long timeoutMs) throws InterruptedException {
        try {
            return Protocol.MAPPER.readTree(nextOfType(type, timeoutMs));
        } catch (Exception e) {
            throw new AssertionError("Cannot parse " + type, e);
        }
    }

    @Override
    public void close() {
        try { socket.close(); } catch (Exception ignored) {}
    }
}
