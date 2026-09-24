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
                boolean stackableDestination = canJoinFriendlyStack(state, character, next);
                if (!state.board().isEmpty(next) && !stackableDestination) continue;
                distance.put(next, nextDistance);
                if (state.board().isEmpty(next)) queue.addLast(next);
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
                if ((!state.board().isEmpty(next) && !next.equals(destination)) || distance.containsKey(next)) continue;
                distance.put(next, distance.get(current) + 1);
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private boolean canJoinFriendlyStack(GameState state, CardInstance character, BoardPosition position) {
        if (state.board().isEmpty(position)) return false;
        return state.board().stackAt(position).stream()
                .map(id -> state.card(id).orElseThrow())
                .allMatch(card -> card.owner() == character.owner());
    }

}
