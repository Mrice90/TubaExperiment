package com.infiniteconquest.core;

import java.util.*;

public final class CapitalDeployment {
    private final CardInstance[] capitals = new CardInstance[2];
    private final BoardPosition[] positions = new BoardPosition[2];
    private boolean revealed;

    public void commit(int playerId, CardInstance capital, BoardPosition position) {
        if (revealed) throw new IllegalStateException("Capitals already revealed");
        if (playerId < 0 || playerId > 1 || capital.owner() != playerId) throw new IllegalArgumentException("Wrong owner");
        if (capital.definition().type() != CardType.CAPITAL) throw new IllegalArgumentException("Card must be a Capital");
        if (!position.isOnPlayerSide(playerId)) throw new IllegalArgumentException("Capital must be on its owner's plot");
        capitals[playerId] = capital;
        positions[playerId] = position;
    }

    public boolean ready() { return capitals[0] != null && capitals[1] != null; }

    public Map<Integer, BoardPosition> reveal(BoardState board) {
        if (!ready()) throw new IllegalStateException("Both players must commit first");
        if (revealed) throw new IllegalStateException("Capitals already revealed");
        for (int player = 0; player < 2; player++) {
            if (!board.isEmpty(positions[player])) throw new IllegalStateException("Capital position is occupied");
        }
        for (int player = 0; player < 2; player++) {
            capitals[player].moveTo(Zone.BATTLEFIELD);
            board.push(positions[player], capitals[player].instanceId());
        }
        revealed = true;
        return Map.of(0, positions[0], 1, positions[1]);
    }
}
