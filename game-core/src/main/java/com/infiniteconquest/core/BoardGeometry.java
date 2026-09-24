package com.infiniteconquest.core;

import java.util.*;

/** Rules geometry, independent of rendering. HEX uses odd-row offset coordinates. */
public enum BoardGeometry {
    SQUARE, HEX;

    public int distance(BoardPosition a, BoardPosition b) {
        if (this == SQUARE) return a.distanceTo(b);
        int aq = a.x() - (a.y() - (a.y() & 1)) / 2;
        int bq = b.x() - (b.y() - (b.y() & 1)) / 2;
        return Math.max(Math.abs(aq - bq), Math.max(Math.abs(a.y() - b.y()),
                Math.abs(aq + a.y() - bq - b.y())));
    }

    public boolean adjacent(BoardPosition a, BoardPosition b) { return distance(a, b) == 1; }

    public List<BoardPosition> neighbors(BoardPosition origin) {
        List<BoardPosition> result = new ArrayList<>();
        for (int y = 0; y < BoardPosition.HEIGHT; y++) for (int x = 0; x < BoardPosition.WIDTH; x++) {
            BoardPosition p = new BoardPosition(x, y);
            if (adjacent(origin, p)) result.add(p);
        }
        return List.copyOf(result);
    }

    /** Two symmetric cube-coordinate traces resolve exact shared-edge ambiguity. */
    public List<BoardPosition> hexTrace(BoardPosition a, BoardPosition b, double nudge) {
        // Canonical direction makes rounding identical when sight is queried in reverse.
        if (a.y() * 4 + a.x() > b.y() * 4 + b.x()) return hexTrace(b, a, nudge);
        int steps = distance(a, b);
        List<BoardPosition> result = new ArrayList<>();
        double aq = a.x() - (a.y() - (a.y() & 1)) / 2.0;
        double bq = b.x() - (b.y() - (b.y() & 1)) / 2.0;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double q = aq + (bq - aq) * t + nudge;
            double r = a.y() + (b.y() - a.y()) * t + nudge;
            double s = -q - r;
            int rq = (int) Math.round(q), rr = (int) Math.round(r), rs = (int) Math.round(s);
            double dq = Math.abs(rq - q), dr = Math.abs(rr - r), ds = Math.abs(rs - s);
            if (dq > dr && dq > ds) rq = -rr - rs;
            else if (dr > ds) rr = -rq - rs;
            int x = rq + (rr - (rr & 1)) / 2;
            if (x >= 0 && x < BoardPosition.WIDTH && rr >= 0 && rr < BoardPosition.HEIGHT)
                result.add(new BoardPosition(x, rr));
        }
        return List.copyOf(result);
    }
}
