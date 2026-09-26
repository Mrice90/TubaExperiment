package com.infiniteconquest.core;

import java.util.*;

public final class MovementRules {
    public Set<BoardPosition> legalDestinations(GameState state, CardInstance character) {
        if (character.definition().type() != CardType.CHARACTER || character.zone() != Zone.BATTLEFIELD) return Set.of();
        Optional<BoardPosition> originResult = state.board().positionOf(character.instanceId());
        if (originResult.isEmpty() || !state.board().topAt(originResult.get()).orElseThrow().equals(character.instanceId())) return Set.of();

        int allowance = character.movementRemaining();
        if (allowance == 0) return Set.of();
        BoardPosition origin = originResult.get();
        // Invading enemy territory is slow going: halve movement, minimum 1.
        if (origin.isOnEnemySide(character.owner())) allowance = Math.max(1, allowance / 2);
        Map<BoardPosition, Integer> distance = new HashMap<>();
        ArrayDeque<BoardPosition> queue = new ArrayDeque<>();
        distance.put(origin, 0);
        queue.add(origin);

        while (!queue.isEmpty()) {
            BoardPosition current = queue.removeFirst();
            int nextDistance = distance.get(current) + 1;
            if (nextDistance > allowance) continue;
            for (BoardPosition next : state.rules().geometry().neighbors(current)) {
                if (distance.containsKey(next)) continue;
                Passability pass = passability(state, character, next);
                if (pass == Passability.BLOCKED) continue;
                distance.put(next, nextDistance);
                if (pass == Passability.OPEN) queue.addLast(next);
            }
        }
        distance.remove(origin);
        return Collections.unmodifiableSet(distance.keySet());
    }

    public int shortestLegalDistance(GameState state, CardInstance character, BoardPosition destination) {
        List<BoardPosition> path = shortestLegalPath(state, character, destination);
        return path.isEmpty() ? -1 : path.size();
    }

    /** Returns each entered cell, excluding the origin and including the destination. */
    public List<BoardPosition> shortestLegalPath(GameState state, CardInstance character, BoardPosition destination) {
        BoardPosition origin = state.board().positionOf(character.instanceId()).orElseThrow();
        if (!legalDestinations(state, character).contains(destination)) return List.of();

        Map<BoardPosition, Integer> distance = new HashMap<>();
        Map<BoardPosition, BoardPosition> previous = new HashMap<>();
        ArrayDeque<BoardPosition> queue = new ArrayDeque<>();
        distance.put(origin, 0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BoardPosition current = queue.removeFirst();
            if (current.equals(destination)) {
                LinkedList<BoardPosition> path = new LinkedList<>();
                for (BoardPosition step = destination; !step.equals(origin); step = previous.get(step)) {
                    path.addFirst(step);
                }
                return List.copyOf(path);
            }
            for (BoardPosition next : state.rules().geometry().neighbors(current)) {
                if (distance.containsKey(next)) continue;
                // The destination was validated against legalDestinations, so it is
                // enterable by construction; every other step must be open ground.
                Passability pass = next.equals(destination) ? Passability.OPEN : passability(state, character, next);
                if (pass == Passability.BLOCKED) continue;
                distance.put(next, distance.get(current) + 1);
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        return List.of();
    }

    /**
     * How a moving character may treat a hex.
     * <ul>
     *   <li>OPEN — empty or open ground: the character may enter, pass through, and end its move here.</li>
     *   <li>ENTER_ONLY — a friendly stack: the character may end its move here but not pass through.</li>
     *   <li>BLOCKED — solid: an enemy structure, the enemy Capital, or an enemy Character.</li>
     * </ul>
     * Enemy land is open ground — a character marches straight through it — but
     * enemy structures are solid and cannot be entered or passed through.
     */
    private enum Passability { OPEN, ENTER_ONLY, BLOCKED }

    private Passability passability(GameState state, CardInstance character, BoardPosition position) {
        if (state.board().isEmpty(position)) return Passability.OPEN;
        List<CardInstance> stack = state.board().stackAt(position).stream()
                .map(id -> state.card(id).orElseThrow())
                .toList();
        if (stack.stream().allMatch(card -> card.definition().type() == CardType.LAND)) return Passability.OPEN;
        boolean friendly = stack.stream()
                .filter(card -> card.definition().type() != CardType.LAND)
                .allMatch(card -> card.owner() == character.owner());
        return friendly ? Passability.ENTER_ONLY : Passability.BLOCKED;
    }

}
