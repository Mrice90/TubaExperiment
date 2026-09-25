package com.infiniteconquest.core;

import java.util.*;

public final class BoardState {
    private final Map<BoardPosition, List<UUID>> cells = new LinkedHashMap<>();

    public BoardState() {
        for (int y = 0; y < BoardPosition.HEIGHT; y++) for (int x = 0; x < BoardPosition.WIDTH; x++)
            cells.put(new BoardPosition(x, y), new ArrayList<>());
    }

    /** Deep copy: positions are immutable records, stacks are duplicated. */
    BoardState(BoardState source) {
        for (Map.Entry<BoardPosition, List<UUID>> entry : source.cells.entrySet())
            cells.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    public Set<BoardPosition> positions() { return Collections.unmodifiableSet(cells.keySet()); }
    public List<UUID> stackAt(BoardPosition position) { return Collections.unmodifiableList(cells.get(Objects.requireNonNull(position))); }
    public Optional<UUID> topAt(BoardPosition position) {
        List<UUID> stack = cells.get(Objects.requireNonNull(position));
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.get(stack.size() - 1));
    }
    public Optional<BoardPosition> positionOf(UUID id) {
        return cells.entrySet().stream().filter(e -> e.getValue().contains(id)).map(Map.Entry::getKey).findFirst();
    }
    public boolean isEmpty(BoardPosition position) { return cells.get(Objects.requireNonNull(position)).isEmpty(); }
    public void push(BoardPosition position, UUID id) {
        if (positionOf(id).isPresent()) throw new IllegalStateException("Card is already on battlefield");
        cells.get(Objects.requireNonNull(position)).add(Objects.requireNonNull(id));
    }
    public void insertBelowTop(BoardPosition position, UUID id) {
        if (positionOf(id).isPresent()) throw new IllegalStateException("Card is already on battlefield");
        List<UUID> stack = cells.get(Objects.requireNonNull(position));
        if (stack.isEmpty()) throw new IllegalStateException("Cannot insert beneath an empty stack");
        stack.add(stack.size() - 1, Objects.requireNonNull(id));
    }
    public UUID pop(BoardPosition position) {
        List<UUID> stack = cells.get(Objects.requireNonNull(position));
        if (stack.isEmpty()) throw new IllegalStateException("Cannot pop empty cell");
        return stack.remove(stack.size() - 1);
    }
    public void remove(UUID id) {
        BoardPosition position = positionOf(id).orElseThrow(() -> new IllegalStateException("Card is not on battlefield"));
        if (!topAt(position).orElseThrow().equals(id)) throw new IllegalStateException("Only top card can be removed");
        pop(position);
    }
    public void moveTop(BoardPosition from, BoardPosition to, UUID expected) {
        if (!topAt(from).orElseThrow().equals(expected)) throw new IllegalStateException("Only top card can move");
        pop(from);
        push(to, expected);
    }
}
