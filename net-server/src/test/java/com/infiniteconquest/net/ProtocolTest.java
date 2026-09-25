package com.infiniteconquest.net;

import com.infiniteconquest.core.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void envelopesRoundTrip() {
        DeckDto deck = new DeckDto("Deck", "ZEUS", null, "zeus_capital_cloud_throne", List.of("zeus_chain_lightning"));
        assertRoundTrip(new Protocol.Hello("uuid-1", "Mathew", deck), Protocol.Hello.class);
        assertRoundTrip(new Protocol.StartMatch(), Protocol.StartMatch.class);
        assertRoundTrip(new Protocol.PlayerCommand("end"), Protocol.PlayerCommand.class);
        assertRoundTrip(new Protocol.Lobby(
                List.of(new Protocol.LobbyPlayer("uuid-1", "Mathew")), "uuid-1"), Protocol.Lobby.class);
        assertRoundTrip(new Protocol.ErrorMessage("Not your turn"), Protocol.ErrorMessage.class);
        assertRoundTrip(new Protocol.MulliganPrompt(3L, "match-1", null), Protocol.MulliganPrompt.class);
        assertRoundTrip(new Protocol.MulliganDecision(List.of(UUID.randomUUID())), Protocol.MulliganDecision.class);
        assertRoundTrip(new Protocol.MulliganUpdate(true, false), Protocol.MulliganUpdate.class);
        assertRoundTrip(new Protocol.ReactionPrompt(4L, "match-1", 1,
                List.of("react 2 3 4", "react 5 3 4"), 60), Protocol.ReactionPrompt.class);
        assertRoundTrip(new Protocol.ReactionDecision("react 2 3 4"), Protocol.ReactionDecision.class);
        assertRoundTrip(new Protocol.ReactionDecision(null), Protocol.ReactionDecision.class);
        assertRoundTrip(new Protocol.ReactionTimeout(1), Protocol.ReactionTimeout.class);
        assertRoundTrip(new Protocol.ReactionWaiting(1, 60), Protocol.ReactionWaiting.class);
    }

    private <T> void assertRoundTrip(T message, Class<T> type) {
        String line = Protocol.encode(message);
        assertEquals(type.getSimpleName().equals("FullSnapshot") ? "snapshot" : lineType(message), Protocol.typeOf(line));
        T decoded = Protocol.decode(line, type);
        assertEquals(message, decoded);
    }

    private String lineType(Object message) {
        if (message instanceof Protocol.Hello h) return h.type();
        if (message instanceof Protocol.StartMatch s) return s.type();
        if (message instanceof Protocol.PlayerCommand c) return c.type();
        if (message instanceof Protocol.Lobby l) return l.type();
        if (message instanceof Protocol.ErrorMessage e) return e.type();
        if (message instanceof Protocol.MulliganPrompt m) return m.type();
        if (message instanceof Protocol.MulliganDecision m) return m.type();
        if (message instanceof Protocol.MulliganUpdate m) return m.type();
        if (message instanceof Protocol.ReactionPrompt r) return r.type();
        if (message instanceof Protocol.ReactionDecision r) return r.type();
        if (message instanceof Protocol.ReactionTimeout r) return r.type();
        if (message instanceof Protocol.ReactionWaiting r) return r.type();
        throw new IllegalArgumentException("unexpected");
    }

    @Test void snapshotEnvelopeCarriesPerViewerState() {
        GameSnapshot snapshot = new GameSnapshot(42L, "HEX", 1, 0, 0, 1,
                new int[]{1, 0}, "PLAY", null,
                new int[]{3, 3}, new int[]{3, 3}, new int[]{4, 4}, new int[]{36, 36},
                new int[]{0, 0}, new int[]{0, 0},
                List.of(), List.of(), false);
        String matchId = UUID.randomUUID().toString();
        Protocol.FullSnapshot out = new Protocol.FullSnapshot(7L, matchId, snapshot);
        Protocol.FullSnapshot back = Protocol.decode(Protocol.encode(out), Protocol.FullSnapshot.class);
        assertEquals(7L, back.seq());
        assertEquals(matchId, back.matchId(), "match id must survive the wire for rating reports");
        assertEquals(1, back.snapshot().viewingPlayer());
        assertEquals("snapshot", Protocol.typeOf(Protocol.encode(out)));

        Protocol.StateUpdate update = new Protocol.StateUpdate(8L, "end", "OK: Turn ended", 0, snapshot);
        Protocol.StateUpdate updateBack = Protocol.decode(Protocol.encode(update), Protocol.StateUpdate.class);
        assertEquals("state_update", updateBack.type());
        assertEquals("OK: Turn ended", updateBack.result());

        Protocol.GameOver gameOver = new Protocol.GameOver(0, matchId, snapshot);
        assertEquals("game_over", Protocol.typeOf(Protocol.encode(gameOver)));
        assertEquals(0, Protocol.decode(Protocol.encode(gameOver), Protocol.GameOver.class).winner());
        assertEquals(matchId, Protocol.decode(Protocol.encode(gameOver), Protocol.GameOver.class).matchId());
    }

    @Test void malformedInputFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> Protocol.typeOf("not json"));
        assertThrows(IllegalArgumentException.class, () -> Protocol.typeOf("{\"no\":\"type\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> Protocol.decode(Protocol.encode(new Protocol.PlayerCommand("end")), Protocol.Hello.class));
    }

    @Test void sanitizeName() {
        assertEquals("Player", Protocol.sanitizeName(null));
        assertEquals("Player", Protocol.sanitizeName("   "));
        assertEquals("Mathew", Protocol.sanitizeName("  Mathew  "));
        assertEquals("EvilName", Protocol.sanitizeName("Evil\u0007Name"));
        assertEquals(24, Protocol.sanitizeName("abcdefghijklmnopqrstuvwxyz123").length());
        assertEquals("Player One", Protocol.sanitizeName("Player One"));
    }

    @Test void deckDtoRoundTrip() {
        DeckDto deck = new DeckDto("My Deck", "ZEUS", "POSEIDON", "zeus_capital_cloud_throne",
                List.of("a", "b"));
        DeckDto back = Protocol.decode(Protocol.encode(new Protocol.Hello(UUID.randomUUID().toString(), "P", deck)),
                Protocol.Hello.class).deck();
        assertEquals(deck, back);
    }
}
