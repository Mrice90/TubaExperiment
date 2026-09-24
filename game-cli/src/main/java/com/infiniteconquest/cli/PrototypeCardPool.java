package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.data.CardCatalog;

import java.util.*;

public final class PrototypeCardPool {
    private final List<CardDefinition> cards;
    private final Map<String, CardDefinition> byId;

    public PrototypeCardPool() {
        List<CardDefinition> loaded = new ArrayList<>();
        loaded.addAll(CardCatalog.loadResource("/cards/prototype-characters.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/development-cards.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-cards.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-spells.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-apex-cards.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-keyword-cards.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-development-expansion.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-development-expansion-2.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/tactical-keyword-cards.json").definitions());
        loaded.addAll(CardCatalog.loadResource("/cards/faction-ability-cards.json").definitions());
        loaded.addAll(FactionTutorExpansion.cards());
        Map<String, CardDefinition> indexed = new LinkedHashMap<>();
        for (CardDefinition card : loaded) {
            if (indexed.putIfAbsent(card.id(), card) != null) {
                throw new IllegalStateException("Duplicate prototype card ID: " + card.id());
            }
        }
        cards = List.copyOf(loaded);
        byId = Collections.unmodifiableMap(indexed);
    }

    public List<CardDefinition> cards() { return cards; }

    public List<CardDefinition> cardsForFaction(String faction) {
        return cards.stream().filter(card -> card.faction().equalsIgnoreCase(faction)).toList();
    }

    public CardDefinition require(String id) {
        CardDefinition card = byId.get(id);
        if (card == null) throw new IllegalArgumentException("Unknown card ID: " + id);
        return card;
    }
}
