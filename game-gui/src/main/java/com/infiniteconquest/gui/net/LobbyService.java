package com.infiniteconquest.gui.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infiniteconquest.net.Protocol;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HTTP client for the lobby/rating Worker (see {@code lobby-worker/}).
 *
 * <p>The Worker is rendezvous-only: lobby listings, quick-match pairing,
 * and the Elo ledger. It never sees game traffic, hands, or deck lists —
 * only player UUIDs, display names, tunnel URLs, and match results.
 * Every call here is a plain stateless HTTP request; the game keeps no
 * session with the Worker.
 */
public final class LobbyService {
    private static final ObjectMapper MAPPER = Protocol.MAPPER;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public record LobbyEntry(String code, String hostName, int hostRating, String wssUrl) {}
    public record QueuePoll(String status, String wssUrl, String code,
                            String opponentUuid, String opponentName, int opponentRating) {}
    public record ReportResult(boolean applied, int rating, int delta, String reason) {}
    public record LeaderEntry(String name, int rating, int wins, int losses) {}
    public record Rating(int rating, int wins, int losses) {}

    private final String baseUrl;
    private final HttpClient http;

    /**
     * Proxy selector that always connects directly to loopback addresses:
     * lobby calls to a local stub (tests) must never go through a system
     * proxy, while public Worker hosts keep the default behavior.
     */
    private static final java.net.ProxySelector LOOPBACK_DIRECT = new java.net.ProxySelector() {
        @Override public java.util.List<java.net.Proxy> select(URI uri) {
            String host = uri.getHost();
            if (host != null && (host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1"))) {
                return java.util.List.of(java.net.Proxy.NO_PROXY);
            }
            return java.net.ProxySelector.getDefault().select(uri);
        }

        @Override public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException e) {
            java.net.ProxySelector.getDefault().connectFailed(uri, address, e);
        }
    };

    /** @param baseUrl the Worker origin, e.g. {@code https://ic-lobby.example.workers.dev} */
    public LobbyService(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .proxy(LOOPBACK_DIRECT)
                .build();
    }

    // --- Lobbies ---

    /** Registers (or heartbeats) a public lobby; returns the lobby code. */
    public String registerLobby(String code, String hostUuid, String hostName,
                                int hostRating, String wssUrl) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        if (code != null) body.put("code", code);
        body.put("hostUuid", hostUuid);
        body.put("hostName", hostName);
        body.put("hostRating", hostRating);
        body.put("wssUrl", wssUrl);
        body.put("dataVersion", Protocol.DATA_VERSION);
        JsonNode response = post("/lobbies", body);
        return response.path("code").asText();
    }

    /** Lists open public lobbies. */
    public List<LobbyEntry> listLobbies() throws IOException {
        JsonNode response = get("/lobbies");
        List<LobbyEntry> entries = new ArrayList<>();
        for (JsonNode node : response) {
            entries.add(new LobbyEntry(
                    node.path("code").asText(),
                    node.path("hostName").asText("?"),
                    node.path("hostRating").asInt(1000),
                    node.path("wssUrl").asText("")));
        }
        return entries;
    }

    /** Resolves a lobby code to its entry, or {@code null} when unknown/expired. */
    public LobbyEntry getLobby(String code) throws IOException {
        try {
            JsonNode node = get("/lobbies/" + encode(code));
            return new LobbyEntry(node.path("code").asText(), node.path("hostName").asText("?"),
                    node.path("hostRating").asInt(1000), node.path("wssUrl").asText(""));
        } catch (NotFoundException e) {
            return null;
        }
    }

    /** Closes a lobby; only the registering host UUID may delete it. */
    public void deleteLobby(String code, String hostUuid) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("hostUuid", hostUuid);
        request("DELETE", "/lobbies/" + encode(code), MAPPER.writeValueAsString(body));
    }

    // --- Quick match ---

    /** Joins the quick-match queue (re-POST to stay queued; entries expire). */
    public void enqueue(String uuid, String name, int rating) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("uuid", uuid);
        body.put("name", name);
        body.put("rating", rating);
        post("/queue", body);
    }

    /** Polls for a pairing: waiting, host (you publish the tunnel), or ready (join theirs). */
    public QueuePoll pollQueue(String uuid) throws IOException {
        JsonNode node = get("/queue/poll?uuid=" + encode(uuid));
        return new QueuePoll(
                node.path("status").asText("waiting"),
                node.path("wssUrl").asText(null),
                node.path("code").asText(null),
                node.path("opponentUuid").asText(null),
                node.path("opponentName").asText(null),
                node.path("opponentRating").asInt(1000));
    }

    /** Publishes the tunnel for a guest found via quick match. */
    public void publishPairing(String hostUuid, String forUuid, String wssUrl, String code,
                               String hostName, int hostRating) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("hostUuid", hostUuid);
        body.put("forUuid", forUuid);
        body.put("wssUrl", wssUrl);
        body.put("code", code);
        body.put("hostName", hostName);
        body.put("hostRating", hostRating);
        post("/pair", body);
    }

    /** Leaves the quick-match queue. */
    public void leaveQueue(String uuid) throws IOException {
        request("DELETE", "/queue?uuid=" + encode(uuid), null);
    }

    // --- Ratings ---

    /**
     * Reports this client's view of a match result. Both clients report
     * independently; the Worker applies the Elo change only when both
     * reports agree on the winner.
     */
    public ReportResult report(String matchId, String reporterUuid,
                               String winnerUuid, String loserUuid) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("matchId", matchId);
        body.put("reporterUuid", reporterUuid);
        body.put("winnerUuid", winnerUuid);
        body.put("loserUuid", loserUuid);
        body.put("dataVersion", Protocol.DATA_VERSION);
        JsonNode node = post("/report", body);
        return new ReportResult(
                node.path("applied").asBoolean(false),
                node.path("rating").asInt(1000),
                node.path("delta").asInt(0),
                node.path("reason").isMissingNode() ? null : node.path("reason").asText(null));
    }

    /** Top-N leaderboard; entries carry names and ratings only, never UUIDs. */
    public List<LeaderEntry> leaderboard(int limit) throws IOException {
        JsonNode response = get("/leaderboard?limit=" + limit);
        List<LeaderEntry> entries = new ArrayList<>();
        for (JsonNode node : response) {
            entries.add(new LeaderEntry(node.path("name").asText("?"),
                    node.path("rating").asInt(1000),
                    node.path("wins").asInt(0), node.path("losses").asInt(0)));
        }
        return entries;
    }

    /** This player's rating record (defaults to 1000/0/0 for new players). */
    public Rating ratingOf(String uuid) throws IOException {
        JsonNode node = get("/rating/" + encode(uuid));
        return new Rating(node.path("rating").asInt(1000),
                node.path("wins").asInt(0), node.path("losses").asInt(0));
    }

    // --- HTTP plumbing ---

    private JsonNode get(String path) throws IOException {
        return request("GET", path, null);
    }

    private JsonNode post(String path, JsonNode body) throws IOException {
        try {
            return request("POST", path, MAPPER.writeValueAsString(body));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("Could not encode request", e);
        }
    }

    private JsonNode request(String method, String path, String body) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "InfiniteConquest/" + Protocol.DATA_VERSION);
        if ("POST".equals(method)) builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        else if ("DELETE".equals(method))
            builder.method("DELETE", body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        int status = response.statusCode();
        if (status == 404) throw new NotFoundException(path);
        if (status < 200 || status >= 300)
            throw new IOException("Lobby service error " + status + " for " + path);
        try {
            String text = response.body();
            return text == null || text.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException("Bad response from lobby service", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Thrown for HTTP 404 so callers can distinguish "unknown/expired" from errors. */
    public static final class NotFoundException extends IOException {
        NotFoundException(String path) { super("Not found: " + path); }
    }
}
