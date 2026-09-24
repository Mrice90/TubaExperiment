package com.infiniteconquest.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Probes whether real loopback TCP works in this environment. Some sandboxes
 * intercept JVM socket connections; tests that require TCP self-skip there via
 * {@link org.junit.jupiter.api.Assumptions}.
 */
public final class TcpProbe {
    private TcpProbe() {}

    /** Returns true if a loopback TCP round-trip to {@code port} returns a JSON line. */
    public static boolean available(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3_000);
            socket.setSoTimeout(3_000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            socket.getOutputStream().write("{\"type\":\"ping\"}\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String line = in.readLine();
            // A real server replies with a JSON error envelope; an intercepted
            // connection returns a policy message or nothing at all.
            return line != null && line.trim().startsWith("{") && line.contains("\"type\"");
        } catch (Exception e) {
            return false;
        }
    }
}
