package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckValidator;

import java.util.*;

public final class DeckEditor {
    private final PrototypeCardPool pool;
    private final List<CardDefinition> cards;
    private String primary, ally;
    private CardDefinition capital;

    public void identity(String primary, String ally, CardDefinition capital) {
        if (!com.infiniteconquest.core.DeckBuild.FACTIONS.contains(primary)
                || (ally != null && (!com.infiniteconquest.core.DeckBuild.FACTIONS.contains(ally) || ally.equals(primary)))
                || capital.type() != com.infiniteconquest.core.CardType.CAPITAL || !capital.faction().equals(primary))
            throw new IllegalArgumentException("Choose a primary faction, at most one different ally, and a primary Capital");
        if (cards.stream().anyMatch(c -> !com.infiniteconquest.core.DeckBuild.eligible(c, primary, ally)))
            throw new IllegalArgumentException("Existing cards do not match this identity; reset to a faction starter first");
        this.primary=primary;this.ally=ally;this.capital=capital;
    }
    public com.infiniteconquest.core.DeckBuild build() {
        if (primary == null) throw new IllegalArgumentException("Choose identity <primary> [ally] before sharing");
        return new com.infiniteconquest.core.DeckBuild("Custom Deck",primary,ally,capital,cards);
    }
    public boolean hasIdentity(){return primary!=null;}

    public DeckEditor(PrototypeCardPool pool, List<CardDefinition> startingDeck) {
        this.pool = Objects.requireNonNull(pool);
        this.cards = new ArrayList<>(Objects.requireNonNull(startingDeck));
    }

    public List<CardDefinition> cards() { return Collections.unmodifiableList(cards); }

    public void reset(List<CardDefinition> replacement) {
        cards.clear();
        cards.addAll(Objects.requireNonNull(replacement));
        primary=null;ally=null;capital=null;
    }

    public Map<String, Long> counts() {
        Map<String, Long> result = new TreeMap<>();
        for (CardDefinition card : cards) result.merge(card.id(), 1L, Long::sum);
        return Collections.unmodifiableMap(result);
    }

    public void add(String id) {
        CardDefinition card = pool.require(id);
        if (primary != null && !com.infiniteconquest.core.DeckBuild.eligible(card,primary,ally)) throw new IllegalArgumentException("Card is outside your primary/ally factions");
        long copies = cards.stream().filter(existing -> existing.id().equals(id)).count();
        if (copies >= DeckValidator.MAX_COPIES) {
            throw new IllegalArgumentException(id + " already has four copies");
        }
        cards.add(card);
    }

    public void remove(String id) {
        int index = -1;
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).id().equals(id)) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException(id + " is not in the deck");
        cards.remove(index);
    }

    public void swap(String removeId, String addId) {
        pool.require(addId);
        if (primary != null && !com.infiniteconquest.core.DeckBuild.eligible(pool.require(addId),primary,ally)) throw new IllegalArgumentException("Card is outside your primary/ally factions");
        long addCopies = cards.stream().filter(card -> card.id().equals(addId)).count();
        if (addCopies >= DeckValidator.MAX_COPIES) {
            throw new IllegalArgumentException(addId + " already has four copies");
        }
        int removeIndex = -1;
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).id().equals(removeId)) { removeIndex = i; break; }
        if (removeIndex < 0) throw new IllegalArgumentException(removeId + " is not in the deck");
        cards.set(removeIndex, pool.require(addId));
    }

    public List<String> validationErrors() {
        if(primary!=null)return com.infiniteconquest.core.DeckBuild.errors(primary,ally,capital,cards);
        return new DeckValidator().validate(cards);
    }
}
