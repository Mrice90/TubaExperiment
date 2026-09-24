package com.infiniteconquest.net;

import com.infiniteconquest.cli.CommandProcessor;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.core.BoardGeometry;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameEvent;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Zone;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Privacy regression: a guest snapshot must never contain opponent hand card
 * IDs or deck order. Fails loudly if redaction ever leaks hidden-zone data.
 */
class RedactorTest {
    private final DemoMatchFactory factory = new DemoMatchFactory();

    private DeckBuild deck(String faction) {
        List<CardDefinition> cards = new FactionDecks(factory.pool()).starter(faction);
        CardDefinition capital = factory.capitals().forFaction(faction).get(0);
        return new DeckBuild(faction + " Test", faction, null, capital, cards);
    }

    private GameState livelyMatch() {
        GameState state = factory.create(20260924L, deck("ZEUS"), deck("POSEIDON"),
                new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        state.mulligan(0, List.of());
        state.mulligan(1, List.of());
        CommandProcessor commands = new CommandProcessor(state);
        // Play a few turns so the board, discards, and event log are non-trivial.
        for (int i = 0; i < 6 && state.winner().isEmpty(); i++) {
            tryPlaySomething(state, commands);
            commands.execute("end");
        }
        return state;
    }

    /** Plays the first playable hand card for the active player, or returns null. */
    private String tryPlaySomething(GameState state, CommandProcessor commands) {
        int player = state.activePlayer();
        List<UUID> hand = state.player(player).hand();
        for (int index = 0; index < hand.size(); index++) {
            for (int y = 0; y < 3; y++) {
                String result = commands.execute("play " + index + " 1 " + (player == 0 ? y : y + 3));
                if (result.startsWith("OK:")) return result;
            }
        }
        return null;
    }

    @Test void guestSnapshotLeaksNoOpponentHiddenCards() throws Exception {
        GameState state = livelyMatch();
        assertTrue(state.turnNumber() > 1, "test match should have advanced");

        for (int viewer = 0; viewer < 2; viewer++) {
            int opponent = 1 - viewer;
            GameSnapshot snapshot = Redactor.redact(state, viewer);

            Set<UUID> opponentHidden = new HashSet<>(state.player(opponent).hand());
            opponentHidden.addAll(state.player(opponent).deck());
            assertFalse(opponentHidden.isEmpty(), "opponent should still hold hidden cards");

            // 1. No opponent hidden-zone card appears in the card list.
            for (GameSnapshot.CardView card : snapshot.cards()) {
                assertFalse(opponentHidden.contains(card.instanceId()),
                        "opponent hidden card leaked into card list: " + card.instanceId());
                if (card.owner() == opponent)
                    assertTrue(card.zone() == Zone.BATTLEFIELD || card.zone() == Zone.DISCARD,
                            "opponent card in non-public zone: " + card.zone());
            }

            // 2. No opponent hidden-zone UUID appears ANYWHERE in the encoded snapshot
            //    (catches event-detail leaks).
            String encoded = Protocol.MAPPER.writeValueAsString(snapshot);
            for (UUID hidden : opponentHidden)
                assertFalse(encoded.contains(hidden.toString()),
                        "opponent hidden UUID leaked into snapshot JSON: " + hidden);

            // 3. Counts are exact; order is not reconstructible.
            assertEquals(state.player(opponent).hand().size(), snapshot.handCounts()[opponent]);
            assertEquals(state.player(opponent).deck().size(), snapshot.deckCounts()[opponent]);
            long opponentDeckCards = snapshot.cards().stream()
                    .filter(c -> c.owner() == opponent && c.zone() == Zone.DECK).count();
            assertEquals(0, opponentDeckCards, "opponent deck cards must not be listed");
        }
    }

    @Test void viewerKeepsOwnHandButNeverAnyDeck() throws Exception {
        GameState state = livelyMatch();
        checkViewerHandAndNoDeck(state, 0);
        checkViewerHandAndNoDeck(state, 1);
    }

    /**
     * The mulligan prompt carries a pre-decision snapshot to each player. Player
     * B's copy must contain none of player A's opening-hand IDs (it needs only
     * B's own hand to render the choice), and must be marked as a mulligan
     * snapshot so clients can tell it apart from the live match start.
     */
    @Test void mulliganSnapshotForPlayerBContainsNoPlayerAHandIds() throws Exception {
        GameState state = factory.create(20260924L, deck("ZEUS"), deck("POSEIDON"),
                new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        assertTrue(state.isMulliganWindowOpen(), "fresh match must be inside the mulligan window");

        GameSnapshot bView = Redactor.redact(state, 1);
        assertTrue(bView.mulliganOpen(), "mulligan prompt snapshot must be flagged");

        Set<UUID> aHand = new HashSet<>(state.player(0).hand());
        assertFalse(aHand.isEmpty(), "player A should hold an opening hand");
        for (GameSnapshot.CardView card : bView.cards())
            assertFalse(aHand.contains(card.instanceId()),
                    "player A hand id leaked into player B mulligan snapshot: " + card.instanceId());
        String encoded = Protocol.MAPPER.writeValueAsString(bView);
        for (UUID id : aHand)
            assertFalse(encoded.contains(id.toString()),
                    "player A hand UUID leaked into player B mulligan JSON: " + id);

        // Player B still sees their own full hand: the mulligan UI needs it.
        Set<UUID> bHand = new HashSet<>(state.player(1).hand());
        assertFalse(bHand.isEmpty(), "player B should hold an opening hand");
        for (UUID id : bHand)
            assertTrue(encoded.contains(id.toString()),
                    "player B should see their own hand id: " + id);
        assertEquals(bHand.size(), bView.handCounts()[1]);
        assertEquals(aHand.size(), bView.handCounts()[0], "player A hand visible as count only");
    }

    private void checkViewerHandAndNoDeck(GameState state, int viewer) throws Exception {
        GameSnapshot snapshot = Redactor.redact(state, viewer);
        List<UUID> handIds = snapshot.cards().stream()
                .filter(c -> c.owner() == viewer && c.zone() == Zone.HAND)
                .map(GameSnapshot.CardView::instanceId).collect(Collectors.toList());
        assertEquals(state.player(viewer).hand(), handIds, "viewer hand must round-trip in order");
        // Both decks are counts only: no deck-zone card for either player, and no
        // deck UUID from the authoritative state may appear anywhere in the JSON.
        assertEquals(0, snapshot.cards().stream()
                .filter(c -> c.zone() == Zone.DECK).count(), "no deck cards may be listed");
        String encoded = Protocol.MAPPER.writeValueAsString(snapshot);
        for (int player = 0; player < 2; player++)
            for (UUID deckId : state.player(player).deck())
                assertFalse(encoded.contains(deckId.toString()),
                        "deck UUID leaked into snapshot JSON: " + deckId);
        assertEquals(state.player(0).deck().size(), snapshot.deckCounts()[0]);
        assertEquals(state.player(1).deck().size(), snapshot.deckCounts()[1]);
    }

    @Test void withheldEventTypesNeverTransmitted() {
        GameState state = livelyMatch();
        assertTrue(state.events().stream().anyMatch(e -> e.type() == GameEvent.Type.CARD_DRAWN),
                "test match should contain draws");
        for (int viewer = 0; viewer < 2; viewer++) {
            GameSnapshot snapshot = Redactor.redact(state, viewer);
            assertTrue(snapshot.events().stream()
                    .noneMatch(e -> e.type() == GameEvent.Type.CARD_DRAWN
                            || e.type() == GameEvent.Type.MULLIGAN_COMPLETED));
        }
    }

    @Test void publicZonesAreFullyVisible() {
        GameState state = livelyMatch();
        GameSnapshot snapshot = Redactor.redact(state, 0);
        long boardCards = snapshot.cards().stream()
                .filter(c -> c.zone() == Zone.BATTLEFIELD).count();
        long actualBoard = state.board().positions().stream()
                .mapToLong(p -> state.board().stackAt(p).size()).sum();
        assertEquals(actualBoard, boardCards, "battlefield must be fully visible");
        assertTrue(boardCards > 0, "test match should have board cards");
    }
}
