package com.infiniteconquest.gui.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link LobbyService} against a stub Worker over local HTTP.
 * The stub mimics just enough of the Worker contract (lobbies, report)
 * to verify request shapes and response parsing. Self-skips where the
 * sandbox blocks JVM sockets, like the other TCP tests.
 */
class LobbyServiceTest {
    private HttpServer server;
    private LobbyService service;
    private final ConcurrentHashMap<String, String> lobbies = new ConcurrentHashMap<>();

    @BeforeEach
    void startStub() {
        if (!loopbackWorks()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "JVM loopback TCP is blocked here; skipping LobbyService HTTP test");
        }
        HttpServer created;
        try {
            created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "JVM sockets blocked here; skipping LobbyService HTTP test");
            return;
        }
        created.createContext("/", this::handle);
        created.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "stub-worker");
            t.setDaemon(true);
            return t;
        }));
        created.start();
        server = created;
        service = new LobbyService("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopStub() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String response;
        int status = 200;
        if (method.equals("POST") && path.equals("/lobbies")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String code = body.contains("\"code\"") ? extract(body, "code") : "abc123";
            lobbies.put(code, body);
            response = "{\"code\":\"" + code + "\"}";
        } else if (method.equals("GET") && path.equals("/lobbies")) {
            StringBuilder sb = new StringBuilder("[");
            lobbies.forEach((code, body) -> sb.append("{\"code\":\"").append(code)
                    .append("\",\"hostName\":\"").append(extract(body, "hostName"))
                    .append("\",\"hostRating\":1000,\"wssUrl\":\"").append(extract(body, "wssUrl"))
                    .append("\"},"));
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
            response = sb.append("]").toString();
        } else if (method.equals("POST") && path.equals("/report")) {
            response = "{\"applied\":true,\"rating\":1016,\"delta\":16}";
        } else if (method.equals("GET") && path.startsWith("/rating/")) {
            response = "{\"rating\":1000,\"wins\":0,\"losses\":0}";
        } else if (method.equals("GET") && path.startsWith("/lobbies/")) {
            String code = path.substring("/lobbies/".length());
            String body = lobbies.get(code);
            if (body == null) {
                status = 404;
                response = "{\"error\":\"not found\"}";
            } else {
                response = "{\"code\":\"" + code + "\",\"hostName\":\"" + extract(body, "hostName")
                        + "\",\"hostRating\":1000,\"wssUrl\":\"" + extract(body, "wssUrl") + "\"}";
            }
        } else {
            status = 404;
            response = "{\"error\":\"not found\"}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String extract(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    /**
     * Proves real JVM loopback TCP works: accepts a connection on a probe
     * server socket and exchanges a byte. A bare connect is not enough —
     * this sandbox's filter accepts the TCP handshake and then answers with
     * its own block message instead of routing to the peer.
     */
    private static boolean loopbackWorks() {
        try (java.net.ServerSocket probe =
                     new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
             java.net.Socket client = new java.net.Socket()) {
            probe.setSoTimeout(2000);
            client.connect(new InetSocketAddress("127.0.0.1", probe.getLocalPort()), 2000);
            try (java.net.Socket accepted = probe.accept()) {
                client.setSoTimeout(2000);
                accepted.getOutputStream().write(0x42);
                accepted.getOutputStream().flush();
                return client.getInputStream().read() == 0x42;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    void registerListAndResolveLobby() throws IOException {
        String code = service.registerLobby(null, "uuid-1", "Mathew", 1000,
                "wss://abc.trycloudflare.com");
        assertEquals("abc123", code);
        List<LobbyService.LobbyEntry> entries = service.listLobbies();
        assertEquals(1, entries.size());
        assertEquals("Mathew", entries.get(0).hostName());
        assertEquals("wss://abc.trycloudflare.com", entries.get(0).wssUrl());
        LobbyService.LobbyEntry resolved = service.getLobby("abc123");
        assertNotNull(resolved);
        assertEquals("Mathew", resolved.hostName());
        assertNull(service.getLobby("nope"), "unknown code resolves to null, not an error");
    }

    @Test
    void reportReturnsRatingChange() throws IOException {
        LobbyService.ReportResult result =
                service.report("match-1", "uuid-1", "uuid-1", "uuid-2");
        assertTrue(result.applied());
        assertEquals(1016, result.rating());
        assertEquals(16, result.delta());
    }

    @Test
    void ratingDefaultsForNewPlayers() throws IOException {
        LobbyService.Rating rating = service.ratingOf("uuid-new");
        assertEquals(1000, rating.rating());
        assertEquals(0, rating.wins());
    }
}
