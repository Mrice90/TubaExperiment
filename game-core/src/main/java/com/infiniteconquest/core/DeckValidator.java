package com.infiniteconquest.core;

import java.util.*;
import java.util.stream.Collectors;

public final class DeckValidator {
    public static final int MINIMUM_SIZE = 40;
    public static final int REQUIRED_SIZE = MINIMUM_SIZE;
    public static final int MAX_COPIES = 4;
    public static final int MIN_DISTINCT = 10;

    public List<String> validate(List<CardDefinition> cards) {
        Objects.requireNonNull(cards);
        List<String> errors = new ArrayList<>();
        if (cards.size() < MINIMUM_SIZE) errors.add("Deck must contain at least 40 cards");

        Map<String, Long> counts = cards.stream()
                .collect(Collectors.groupingBy(CardDefinition::id, Collectors.counting()));
        counts.forEach((id, count) -> {
            if (count > MAX_COPIES) errors.add(id + " exceeds the four-copy limit");
        });
        if (counts.size() < MIN_DISTINCT) errors.add("Deck must contain at least 10 distinct card IDs");
        return List.copyOf(errors);
    }

    public boolean isValid(List<CardDefinition> cards) {
        return validate(cards).isEmpty();
    }
}
