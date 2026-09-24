package com.infiniteconquest.gui.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket {@link NetTransport} for tunnel guests.
 *
 * <p>The host's game is reachable over the internet only through a
 * cloudflared quick tunnel, which forwards HTTP(S) — so the guest speaks
 * WebSocket ({@code wss://}) to the tunnel URL. Each text message is one
 * protocol envelope; the host's {@code WsBridge} turns frames into lines
 * for the embedded game server. Uses the JDK's built-in HTTP client, so
 * no extra dependencies are needed (the shipped JRE must include
 * {@code java.net.http} and TLS providers — see dist-work notes).
 */
public final class WsNetTransport implements NetTransport {
    private final URI uri;
    private final AtomicReference<Listener> listener = new AtomicReference<>();
    private volatile WebSocket webSocket;
    private volatile boolean closed;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    /** @param wssUrl the tunnel URL, e.g. {@code wss://abc.trycloudflare.com} */
    public WsNetTransport(String wssUrl) {
        this.uri = URI.create(Objects.requireNonNull(wssUrl, "wssUrl"));
    }

    /** Opens the WebSocket (non-blocking); use {@link #awaitReady} to wait for it. */
    public void connect() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder pending = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket ws) {
                        webSocket = ws;
                        ready.complete(null);
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        pending.append(data);
                        if (last) {
                            String line = pending.toString();
                            pending.setLength(0);
                            Listener l = listener.get();
                            if (l != null && !line.isBlank()) l.onLine(line);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        closed(new java.io.IOException("Tunnel closed: " + reason));
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        if (!ready.isDone()) ready.completeExceptionally(error);
                        closed(error);
                    }
                })
                .whenComplete((ws, error) -> {
                    if (error != null && !ready.isDone()) ready.completeExceptionally(error);
                });
    }

    /** Waits for the WebSocket handshake to complete. */
    public void awaitReady(long timeout, TimeUnit unit)
            throws java.io.IOException, TimeoutException, InterruptedException {
        try {
            ready.get(timeout, unit);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            throw new java.io.IOException("Could not open tunnel connection: "
                    + (cause == null ? e : cause.getMessage()),
                    cause instanceof Exception ? (Exception) cause : e);
        }
    }

    private void closed(Throwable cause) {
        if (closed) return;
        closed = true;
        Listener l = listener.get();
        if (l != null) l.onClosed(cause);
    }

    @Override public void setListener(Listener listener) {
        this.listener.set(listener);
    }

    @Override public void send(String line) {
        WebSocket ws = webSocket;
        if (ws != null && !closed) {
            String text = line == null ? "" : line.stripTrailing();
            if (!text.isEmpty()) ws.sendText(text, true);
        }
    }

    @Override public void close() {
        closed(new java.io.IOException("Client closed"));
        WebSocket ws = webSocket;
        if (ws != null) ws.abort();
    }
}
