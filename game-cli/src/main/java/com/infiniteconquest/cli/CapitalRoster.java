package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.data.CardCatalog;

import java.util.*;

public final class CapitalRoster {
    private final List<CardDefinition> capitals;
    private final Map<String, CardDefinition> byId;

    public CapitalRoster() {
        capitals = CardCatalog.loadResource("/cards/faction-capitals.json").definitions();
        Map<String, CardDefinition> indexed = new LinkedHashMap<>();
        for (CardDefinition capital : capitals) {
            if (capital.type() != CardType.CAPITAL) throw new IllegalStateException(capital.id() + " is not a Capital");
            if (indexed.putIfAbsent(capital.id(), capital) != null) throw new IllegalStateException("Duplicate Capital: " + capital.id());
        }
        for (String faction : FactionDecks.FACTIONS) {
            if (forFaction(faction).size() < 3) throw new IllegalStateException(faction + " requires at least three Capitals");
        }
        byId = Collections.unmodifiableMap(indexed);
    }

    public List<CardDefinition> all() { return capitals; }

    public List<CardDefinition> forFaction(String faction) {
        return capitals.stream().filter(card -> card.faction().equalsIgnoreCase(faction)).toList();
    }

    public CardDefinition require(String id) {
        CardDefinition capital = byId.get(id);
        if (capital == null) throw new IllegalArgumentException("Unknown Capital ID: " + id);
        return capital;
    }

    public Optional<CardDefinition> defaultForDeck(List<CardDefinition> deck) {
        Set<String> factions = new LinkedHashSet<>();
        for (CardDefinition card : deck) if (FactionDecks.FACTIONS.contains(card.faction())) factions.add(card.faction());
        if (factions.size() != 1) return Optional.empty();
        return forFaction(factions.iterator().next()).stream().findFirst();
    }
}
