package com.infiniteconquest.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket-to-TCP bridge for internet play.
 *
 * <p>cloudflared quick tunnels forward HTTP(S) only, so a guest's
 * {@code wss://} connection cannot reach the game's raw TCP server
 * directly. The host therefore runs this bridge on loopback: cloudflared
 * forwards to it, it performs the WebSocket handshake per connection, and
 * it pipes WebSocket text messages to/from the embedded game server as
 * protocol lines. Each WebSocket connection gets its own TCP connection to
 * the game server, so the game server needs no WebSocket awareness at all.
 *
 * <p>Only loopback clients are accepted (cloudflared connects from
 * 127.0.0.1); the bridge never listens on a public interface.
 */
public final class WsBridge implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final String gameHost;
    private final int gamePort;
    private final Thread acceptThread;
    private final AtomicBoolean closed = new AtomicBoolean();

    private WsBridge(ServerSocket serverSocket, String gameHost, int gamePort) {
        this.serverSocket = serverSocket;
        this.gameHost = gameHost;
        this.gamePort = gamePort;
        this.acceptThread = new Thread(this::acceptLoop, "ic-ws-bridge");
        this.acceptThread.setDaemon(true);
    }

    /**
     * Listens on 127.0.0.1 (port 0 picks an ephemeral port) and starts
     * bridging accepted WebSocket connections to the game server at
     * {@code gameHost:gamePort}.
     */
    public static WsBridge listen(int port, String gameHost, int gamePort) throws IOException {
        ServerSocket socket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
        WsBridge bridge = new WsBridge(socket, Objects.requireNonNull(gameHost), gamePort);
        bridge.acceptThread.start();
        return bridge;
    }

    /** The loopback port cloudflared should forward to. */
    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket ws = serverSocket.accept();
                if (!ws.getInetAddress().isLoopbackAddress()) {
                    try { ws.close(); } catch (IOException ignored) {}
                    continue;
                }
                Thread handler = new Thread(() -> handle(ws), "ic-ws-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (!closed.get()) break;
            }
        }
    }

    private void handle(Socket ws) {
        try (ws) {
            InputStream wsIn = ws.getInputStream();
            OutputStream wsOut = ws.getOutputStream();
            try {
                WsCodec.serverHandshake(wsIn, wsOut);
            } catch (IOException e) {
                return; // not a WebSocket client; drop
            }
            try (Socket game = new Socket(gameHost, gamePort)) {
                pump(wsIn, wsOut, game.getInputStream(), game.getOutputStream());
                try { game.close(); } catch (IOException ignored) {}
            } catch (IOException e) {
                try { WsCodec.writeClose(wsOut); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Pipes one WebSocket connection to one game-server connection:
     * text messages become protocol lines downstream, protocol lines become
     * text messages upstream. Returns when either side closes.
     */
    static void pump(InputStream wsIn, OutputStream wsOut, InputStream gameIn, OutputStream gameOut) {
        AtomicBoolean done = new AtomicBoolean();
        Thread upstream = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                ByteArrayCollector collector = new ByteArrayCollector();
                while (!done.get()) {
                    int n = gameIn.read(buffer);
                    if (n < 0) break;
                    int lineStart = 0;
                    for (int i = 0; i < n; i++) {
                        if (buffer[i] == '\n') {
                            byte[] line = collector.take(buffer, lineStart, i);
                            lineStart = i + 1;
                            synchronized (wsOut) {
                                WsCodec.writeTextFrame(wsOut, line);
                            }
                        }
                    }
                    if (lineStart < n) collector.keep(buffer, lineStart, n);
                }
            } catch (IOException ignored) {
            } finally {
                done.set(true);
            }
        }, "ic-ws-upstream");
        upstream.setDaemon(true);
        upstream.start();
        try {
            while (!done.get()) {
                // The bridge is the WebSocket server: client frames must be
                // masked (RFC 6455 section 5.1); unmasked frames are rejected.
                byte[] message = WsCodec.readTextMessage(wsIn, wsOut, true);
                if (message == null) break;
                String text = new String(message, StandardCharsets.UTF_8).stripTrailing();
                if (!text.isEmpty()) {
                    gameOut.write((text + "\n").getBytes(StandardCharsets.UTF_8));
                    gameOut.flush();
                }
            }
        } catch (IOException e) {
            // peer went away; fall through to close
        } finally {
            done.set(true);
            try { upstream.join(2000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Accumulates bytes across reads until a full line is available. */
    static final class ByteArrayCollector {
        private byte[] pending = new byte[0];

        synchronized byte[] take(byte[] buffer, int from, int to) {
            byte[] line = new byte[pending.length + (to - from)];
            System.arraycopy(pending, 0, line, 0, pending.length);
            System.arraycopy(buffer, from, line, pending.length, to - from);
            pending = new byte[0];
            return line;
        }

        synchronized void keep(byte[] buffer, int from, int to) {
            byte[] next = new byte[pending.length + (to - from)];
            System.arraycopy(pending, 0, next, 0, pending.length);
            System.arraycopy(buffer, from, next, pending.length, to - from);
            pending = next;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
