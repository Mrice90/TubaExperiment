package com.infiniteconquest.core;

public final class VictoryEvaluator {
    public boolean hasAnyPermanent(GameState state, int playerId) {
        return state.board().positions().stream()
                .flatMap(position -> state.board().stackAt(position).stream())
                .map(id -> state.card(id).orElseThrow())
                .anyMatch(card -> card.owner() == playerId && isPermanent(card.definition().type()));
    }

    /** Evaluate immediately after the named player loses a permanent. */
    public int winnerAfterPermanentLoss(GameState state, int affectedPlayerId) {
        if (hasAnyPermanent(state, affectedPlayerId)) return -1;
        return 1 - affectedPlayerId;
    }

    private boolean isPermanent(CardType type) {
        return type == CardType.LAND || type == CardType.STRUCTURE || type == CardType.CAPITAL;
    }
}
