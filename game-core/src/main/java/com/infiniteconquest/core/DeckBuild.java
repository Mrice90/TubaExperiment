package com.infiniteconquest.core;

import java.util.*;

/** Explicit deck identity. Capital is separate from the draw pile. */
public record DeckBuild(String name, String primaryFaction, String allyFaction,
                        CardDefinition capital, List<CardDefinition> cards) {
    public static final Set<String> FACTIONS = Set.of("ZEUS", "POSEIDON");
    public DeckBuild {
        name = name == null || name.isBlank() ? "Custom Deck" : name;
        cards = List.copyOf(cards);
        Objects.requireNonNull(capital, "Choose a Capital");
        List<String> errors = errors(primaryFaction, allyFaction, capital, cards);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    }
    public static boolean eligible(CardDefinition card, String primary, String ally) {
        return card.type() != CardType.CAPITAL && (card.faction().equals(primary)
                || card.faction().equals(ally) || card.faction().equals("NEUTRAL"));
    }
    public static List<String> errors(String primary, String ally, CardDefinition capital, List<CardDefinition> cards) {
        List<String> errors = new ArrayList<>(new DeckValidator().validate(cards));
        if (primary == null || !FACTIONS.contains(primary)) errors.add("Choose a primary faction");
        if (ally != null && (!FACTIONS.contains(ally) || ally.equals(primary))) errors.add("Choose at most one different ally faction");
        if (capital == null || capital.type() != CardType.CAPITAL || !capital.faction().equals(primary))
            errors.add("Capital must belong to the primary faction");
        if (cards.stream().anyMatch(c -> !eligible(c, primary, ally))) errors.add("Cards must belong to your primary faction, optional ally, or Neutral; Capitals stay outside the deck");
        return List.copyOf(errors);
    }
}
