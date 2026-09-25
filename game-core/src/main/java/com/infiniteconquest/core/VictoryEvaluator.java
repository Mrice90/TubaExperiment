package com.infiniteconquest.core;

public final class VictoryEvaluator {
    /** True if the player still has a Capital on the battlefield. */
    public boolean hasCapital(GameState state, int playerId) {
        return state.board().positions().stream()
                .flatMap(position -> state.board().stackAt(position).stream())
                .map(id -> state.card(id).orElseThrow())
                .anyMatch(card -> card.owner() == playerId && card.definition().type() == CardType.CAPITAL);
    }

    /**
     * Evaluate immediately after the named player loses a permanent.
     * Destroying the enemy Capital is the win condition: the game ends when
     * the affected player has no Capital left, no matter how many lands and
     * structures they still hold.
     */
    public int winnerAfterCapitalLoss(GameState state, int affectedPlayerId) {
        if (hasCapital(state, affectedPlayerId)) return -1;
        return 1 - affectedPlayerId;
    }
}
