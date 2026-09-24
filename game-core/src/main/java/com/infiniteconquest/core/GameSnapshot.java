package com.infiniteconquest.core;

import java.util.List;
import java.util.UUID;

/**
 * A redacted, serializable view of a {@link GameState} for exactly one viewing player.
 *
 * <p>This is the ONLY match state that ever crosses the network. It is produced
 * server-side (see {@code Redactor} in the net-server module) and rebuilt client-side
 * via {@link GameState#fromSnapshot}. Full {@code GameState} objects are never
 * serialized or transmitted.
 *
 * <p>Visibility contract:
 * <ul>
 *   <li>Battlefield and discard piles: full card detail for both players (public zones).</li>
 *   <li>Viewing player's own hand: full card detail.</li>
 *   <li>Both decks: counts only ({@link #deckCounts}); no card IDs and no deck
 *       order ever appear in {@link #cards}.</li>
 *   <li>Opponent's hand: count only ({@link #handCounts}); no card IDs.</li>
 *   <li>Events: a redacted recent tail; event types that reference hidden-zone card IDs
 *       (for example {@code CARD_DRAWN}) are dropped server-side before transmission.</li>
 * </ul>
 */
public record GameSnapshot(
        long seed,
        /** BoardGeometry name: "HEX" or "SQUARE". */
        String rulesId,
        int viewingPlayer,
        int activePlayer,
        int startingPlayer,
        int turnNumber,
        /** Personal turn counters, indexed by player. */
        int[] personalTurns,
        /** Phase.name(). */
        String phase,
        /** Null until the game ends. */
        Integer winner,
        /** Current GP, indexed by player. */
        int[] gp,
        /** Maximum GP, indexed by player. */
        int[] maxGp,
        /** Hand sizes, indexed by player. Opponent entries are counts only. */
        int[] handCounts,
        /** Deck sizes, indexed by player. Opponent entries are counts only. */
        int[] deckCounts,
        /** Lands played this turn, indexed by player (drives play legality hints). */
        int[] landsPlayed,
        /** Structures played this turn, indexed by player. */
        int[] structuresPlayed,
        /**
         * Every visible card: all battlefield and discard cards, plus the viewing
         * player's own hand. Battlefield cards are listed bottom-to-top
         * per stack so the board rebuilds exactly.
         */
        List<CardView> cards,
        /** Redacted recent events, oldest first. */
        List<GameEvent> events,
        /**
         * True while either player may still submit a mulligan decision.
         * Clients use this to tell a pre-mulligan snapshot from a live one.
         */
        boolean mulliganOpen
) {
    /** Full detail for one visible card. */
    public record CardView(
            UUID instanceId,
            String definitionId,
            int owner,
            Zone zone,
            /** Null unless {@code zone == BATTLEFIELD}. */
            BoardPosition position,
            int damage,
            boolean tapped,
            int attackBonus,
            int defenseBonus,
            boolean attackedThisTurn,
            boolean blinkUsedThisTurn,
            boolean abilityUsedThisTurn,
            int movementSpent
    ) {}
}
