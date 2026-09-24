package com.infiniteconquest.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteconquest.core.GameSnapshot;

import java.util.List;

/**
 * Line-delimited JSON wire protocol for loopback online play.
 *
 * <p>Every message is one JSON object per line with a {@code "type"} tag.
 * Envelope types: {@code hello}, {@code start}, {@code command}, {@code lobby},
 * {@code state_update}, {@code snapshot}, {@code game_over}, {@code error}.
 * There is no chat in alpha, and no chat envelope exists.
 *
 * <p>The wire carries only: player UUIDs, display names, deck lists (to the host
 * only), game commands, and redacted state. See {@code net-server/SECURITY.md}.
 */
public final class Protocol {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Protocol() {}

    /** Client -> server: introduce, carrying the guest's deck list for the host. */
    public record Hello(String type, String uuid, String name, DeckDto deck) {
        public Hello(String uuid, String name, DeckDto deck) { this("hello", uuid, name, deck); }
        public Hello { requireType(type, "hello"); }
    }

    /** Client -> server: host requests the match to begin. */
    public record StartMatch(String type) {
        public StartMatch() { this("start"); }
        public StartMatch { requireType(type, "start"); }
    }

    /** Client -> server: one authoritative game command, e.g. {@code "play 0 1 2"}. */
    public record PlayerCommand(String type, String text) {
        public PlayerCommand(String text) { this("command", text); }
        public PlayerCommand { requireType(type, "command"); }
    }

    /** Server -> all: lobby roster. {@code players} is in seat order (host first). */
    public record Lobby(String type, List<LobbyPlayer> players, String hostUuid) {
        public Lobby(List<LobbyPlayer> players, String hostUuid) { this("lobby", players, hostUuid); }
        public Lobby { requireType(type, "lobby"); }
    }

    public record LobbyPlayer(String uuid, String name) {}

    /** Server -> all: authoritative result of one command, with a per-viewer snapshot. */
    public record StateUpdate(String type, long seq, String command, String result, int actor,
                              GameSnapshot snapshot) {
        public StateUpdate(long seq, String command, String result, int actor, GameSnapshot snapshot) {
            this("state_update", seq, command, result, actor, snapshot);
        }
        public StateUpdate { requireType(type, "state_update"); }
    }

    /** Server -> all: full per-viewer state outside of a command (match start, resync). */
    public record FullSnapshot(String type, long seq, GameSnapshot snapshot) {
        public FullSnapshot(long seq, GameSnapshot snapshot) { this("snapshot", seq, snapshot); }
        public FullSnapshot { requireType(type, "snapshot"); }
    }

    /** Server -> all: terminal match result, with a final per-viewer snapshot. */
    public record GameOver(String type, Integer winner, GameSnapshot snapshot) {
        public GameOver(Integer winner, GameSnapshot snapshot) { this("game_over", winner, snapshot); }
        public GameOver { requireType(type, "game_over"); }
    }

    /** Server -> one client: request rejected or session notice. */
    public record ErrorMessage(String type, String message) {
        public ErrorMessage(String message) { this("error", message); }
        public ErrorMessage { requireType(type, "error"); }
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual))
            throw new IllegalArgumentException("Expected type \"" + expected + "\" but got \"" + actual + "\"");
    }

    /** Serializes one envelope, terminated for the line-delimited transport. */
    public static String encode(Object message) {
        try {
            return MAPPER.writeValueAsString(message) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode protocol message", e);
        }
    }

    /** Returns the {@code "type"} tag of one wire line, or throws on malformed JSON. */
    public static String typeOf(String line) {
        try {
            JsonNode node = MAPPER.readTree(line);
            JsonNode type = node.get("type");
            if (type == null || !type.isTextual()) throw new IllegalArgumentException("Missing type tag");
            return type.asText();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed protocol message", e);
        }
    }

    public static <T> T decode(String line, Class<T> type) {
        try {
            return MAPPER.readValue(line, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed protocol message", e);
        }
    }

    /**
     * Sanitizes a display name: strips control characters, trims, caps at 24
     * characters, and falls back to {@code "Player"}. Display names are public
     * (lobby, future leaderboards); UUIDs are never exposed in their place.
     */
    public static String sanitizeName(String raw) {
        if (raw == null) return "Player";
        String cleaned = raw.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) return "Player";
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }
}
