package com.infiniteconquest.gui.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TCP {@link NetTransport}. Connects to a host/port (default 127.0.0.1 for
 * loopback play); the same framing works for later direct-IP play.
 */
public final class TcpNetTransport implements NetTransport {
    private final String host;
    private final int port;
    private final AtomicReference<Listener> listener = new AtomicReference<>();
    private volatile Socket socket;
    private volatile PrintWriter out;
    private volatile boolean closed;

    public TcpNetTransport(String host, int port) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    /** Connects (blocking) and starts the reader thread. */
    public void connect() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 10_000);
        socket = s;
        out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
        Thread reader = new Thread(this::readLoop, "ic-net-client");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                Listener l = listener.get();
                if (l != null) l.onLine(line);
            }
            closed(new IOException("Connection closed by host"));
        } catch (IOException e) {
            closed(e);
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
        PrintWriter writer = out;
        if (writer != null && !closed) {
            writer.print(line);
            writer.print('\n');
            writer.flush();
        }
    }

    @Override public void close() {
        closed(new IOException("Client closed"));
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
