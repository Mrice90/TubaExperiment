package com.infiniteconquest.core;

import java.util.*;

public final class PlayerState {
    private final int id;
    private final List<UUID> deck = new ArrayList<>();
    private final List<UUID> hand = new ArrayList<>();
    private final List<UUID> discard = new ArrayList<>();
    private int currentGp;
    private int maximumGp;

    public PlayerState(int id) {
        if (id < 0 || id > 1) throw new IllegalArgumentException("Player ID must be 0 or 1");
        this.id = id;
    }

    public int id() { return id; }
    public List<UUID> deck() { return Collections.unmodifiableList(deck); }
    public List<UUID> hand() { return Collections.unmodifiableList(hand); }
    public List<UUID> discard() { return Collections.unmodifiableList(discard); }
    public int currentGp() { return currentGp; }
    public int maximumGp() { return maximumGp; }

    void loadDeck(List<UUID> cardIds) {
        if (!deck.isEmpty() || !hand.isEmpty()) throw new IllegalStateException("Deck already loaded");
        deck.addAll(List.copyOf(cardIds));
    }
    Optional<UUID> drawOne() {
        if (deck.isEmpty()) return Optional.empty();
        UUID card = deck.remove(0);
        hand.add(card);
        return Optional.of(card);
    }
    Optional<UUID> drawFirst(java.util.function.Predicate<UUID> predicate) {
        for (int index = 0; index < deck.size(); index++) {
            UUID card = deck.get(index);
            if (predicate.test(card)) {
                deck.remove(index);
                hand.add(card);
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }
    public void addToHand(UUID id) { hand.add(Objects.requireNonNull(id)); }
    public boolean hasInHand(UUID id) { return hand.contains(id); }
    public void removeFromHand(UUID id) {
        if (!hand.remove(id)) throw new IllegalStateException("Card is not in hand");
    }
    void addToDiscard(UUID id) { discard.add(Objects.requireNonNull(id)); }
    Optional<UUID> removeMostRecentDiscard(java.util.function.Predicate<UUID> predicate) {
        for (int index = discard.size() - 1; index >= 0; index--) {
            UUID id = discard.get(index);
            if (predicate.test(id)) { discard.remove(index); return Optional.of(id); }
        }
        return Optional.empty();
    }
    public void restoreGp(int amount) {
        if (amount < 0) throw new IllegalArgumentException("GP restoration cannot be negative");
        currentGp += amount;
        maximumGp = Math.max(maximumGp, currentGp);
    }
    public void spendGp(int amount) {
        if (amount < 0 || amount > currentGp) throw new IllegalArgumentException("Insufficient GP");
        currentGp -= amount;
    }
    void initializeGp(int availableGp) {
        if (availableGp < 0) throw new IllegalArgumentException("Available GP cannot be negative");
        maximumGp = availableGp;
        currentGp = availableGp;
    }
    public void beginTurn() { restoreGp(1); }
}
