package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;

import java.util.Optional;

public final class LineOfSightRules {
    /**
     * Activated abilities that strike the enemy Capital are aimed shots: they
     * need a clear sight line from the source to that Capital. An enemy
     * structure between the two works as cover and blocks the ability, unless
     * the source is aiming from a higher height — the height-aware trace lets
     * a higher eye level shoot over lower cover. Vacuously true when no enemy
     * Capital stands on the battlefield.
     */
    public boolean hasLineToEnemyCapital(GameState state, CardInstance source, BoardPosition from) {
        return state.battlefieldCards(1 - source.owner()).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .map(card -> state.board().positionOf(card.instanceId()))
                .flatMap(Optional::stream)
                .anyMatch(capital -> hasLineOfSight(state, from, capital));
    }

    public boolean hasLineOfSight(GameState state, BoardPosition from, BoardPosition to) {
        if (state.rules().geometry() == BoardGeometry.HEX) {
            return java.util.stream.DoubleStream.of(0.000001, -0.000001).anyMatch(nudge ->
                    BoardGeometry.HEX.hexTrace(from, to, nudge).stream().noneMatch(p -> blocksHeightSight(state, from, to, p)));
        }
        int x = from.x();
        int y = from.y();
        int dx = Math.abs(to.x() - x);
        int dy = Math.abs(to.y() - y);
        int stepX = Integer.compare(to.x(), x);
        int stepY = Integer.compare(to.y(), y);
        int error = dx - dy;

        while (x != to.x() || y != to.y()) {
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubledError < dx) {
                error += dx;
                y += stepY;
            }
            BoardPosition position = new BoardPosition(x, y);
            if (!position.equals(to) && blocksSight(state, position)) return false;
        }
        return true;
    }

    private boolean blocksHeightSight(GameState state, BoardPosition from, BoardPosition to, BoardPosition middle) {
        int obstacle=TerrainRules.obstacleHeight(state,middle);
        if(obstacle==0)return false;
        int distance=state.rules().geometry().distance(from,to);
        double fraction=(double)state.rules().geometry().distance(from,middle)/Math.max(1,distance);
        double ray=TerrainRules.eyeLevel(state,from)*(1-fraction)+TerrainRules.eyeLevel(state,to)*fraction;
        return obstacle+1e-9>=ray;
    }

    private boolean blocksSight(GameState state, BoardPosition position) {
        return state.board().topAt(position)
                .flatMap(state::card)
                .map(card -> card.definition().type() == CardType.STRUCTURE
                        || card.definition().type() == CardType.CAPITAL
                        || card.definition().hasKeyword(Keyword.VANGUARD))
                .orElse(false);
    }
}
