package com.infiniteconquest.net;

import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardInstance;
import com.infiniteconquest.core.GameEvent;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.core.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the per-viewer {@link GameSnapshot} that is the only match state ever
 * transmitted. The full {@link GameState} is never serialized.
 *
 * <p>Visibility contract (see {@code net-server/SECURITY.md}):
 * <ul>
 *   <li>Battlefield and both discard piles: full card detail for both viewers (public zones).</li>
 *   <li>Viewing player's own hand and deck: full card detail, deck order preserved.</li>
 *   <li>Opponent's hand and deck: counts only. No card IDs and no deck order appear
 *       anywhere in the snapshot, including event details.</li>
 *   <li>Events: recent tail with {@code CARD_DRAWN} and {@code MULLIGAN_COMPLETED}
 *       withheld, plus any event whose detail names a currently-hidden opponent card.</li>
 * </ul>
 */
public final class Redactor {
    /** Event types that reference hidden-zone card IDs: never transmitted. */
    private static final Set<GameEvent.Type> WITHHELD = EnumSet.of(
            GameEvent.Type.CARD_DRAWN, GameEvent.Type.MULLIGAN_COMPLETED);
    private static final int EVENT_TAIL = 80;

    private Redactor() {}

    public static GameSnapshot redact(GameState state, int viewingPlayer) {
        if (viewingPlayer < 0 || viewingPlayer > 1)
            throw new IllegalArgumentException("Viewing player must be 0 or 1");
        int opponent = 1 - viewingPlayer;

        List<GameSnapshot.CardView> cards = new ArrayList<>();
        List<BoardPosition> positions = new ArrayList<>(state.board().positions());
        positions.sort(Comparator.comparingInt(BoardPosition::y).thenComparingInt(BoardPosition::x));
        for (BoardPosition position : positions)
            for (UUID id : state.board().stackAt(position))
                cards.add(view(state.card(id).orElseThrow(), position));
        for (int player = 0; player < 2; player++)
            for (UUID id : state.player(player).discard())
                cards.add(view(state.card(id).orElseThrow(), null));
        for (UUID id : state.player(viewingPlayer).hand())
            cards.add(view(state.card(id).orElseThrow(), null));
        // Decks are NEVER transmitted: both decks are counts only
        // (deckCounts), with no card UUIDs and no order information.

        Set<String> hidden = new HashSet<>();
        for (UUID id : state.player(opponent).hand()) hidden.add(id.toString());
        for (UUID id : state.player(opponent).deck()) hidden.add(id.toString());
        List<GameEvent> all = state.events();
        List<GameEvent> events = new ArrayList<>();
        for (int i = Math.max(0, all.size() - EVENT_TAIL); i < all.size(); i++) {
            GameEvent event = all.get(i);
            if (WITHHELD.contains(event.type())) continue;
            boolean leaksHidden = hidden.stream().anyMatch(token -> event.detail().contains(token));
            if (!leaksHidden) events.add(event);
        }

        return new GameSnapshot(
                state.seed(),
                state.rules().geometry().name(),
                viewingPlayer,
                state.activePlayer(),
                state.startingPlayer(),
                state.turnNumber(),
                new int[]{state.personalTurnNumber(0), state.personalTurnNumber(1)},
                state.phase().name(),
                state.winner().isPresent() ? state.winner().getAsInt() : null,
                new int[]{state.player(0).currentGp(), state.player(1).currentGp()},
                new int[]{state.player(0).maximumGp(), state.player(1).maximumGp()},
                new int[]{state.player(0).hand().size(), state.player(1).hand().size()},
                new int[]{state.player(0).deck().size(), state.player(1).deck().size()},
                new int[]{state.landsPlayedThisTurn(0), state.landsPlayedThisTurn(1)},
                new int[]{state.structuresPlayedThisTurn(0), state.structuresPlayedThisTurn(1)},
                List.copyOf(cards),
                List.copyOf(events));
    }

    private static GameSnapshot.CardView view(CardInstance card, BoardPosition position) {
        return new GameSnapshot.CardView(
                card.instanceId(),
                card.definition().id(),
                card.owner(),
                card.zone(),
                position,
                card.damage(),
                card.tapped(),
                card.attackBonus(),
                card.defenseBonus(),
                card.attackedThisTurn(),
                card.blinkUsedThisTurn(),
                card.abilityUsedThisTurn(),
                card.movementSpent());
    }
}
