package com.infiniteconquest.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteconquest.core.CardDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CardCatalog {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<CardData> cards;

    private CardCatalog(List<CardData> cards) {
        this.cards = List.copyOf(cards);
    }

    public static CardCatalog load(InputStream input) {
        Objects.requireNonNull(input, "input");
        final CatalogDocument document;
        try {
            document = JSON.readValue(input, CatalogDocument.class);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid card catalog JSON", exception);
        }

        if (document.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported card schema version: " + document.schemaVersion());
        }
        if (document.cards() == null) {
            throw new IllegalArgumentException("Catalog cards are required");
        }

        Set<String> ids = new HashSet<>();
        for (CardData card : document.cards()) {
            if (card == null) throw new IllegalArgumentException("Catalog cannot contain null cards");
            if (!ids.add(card.id())) throw new IllegalArgumentException("Duplicate card ID: " + card.id());
            card.toDefinition();
        }
        return new CardCatalog(document.cards());
    }

    public static CardCatalog loadResource(String resourcePath) {
        InputStream input = CardCatalog.class.getResourceAsStream(resourcePath);
        if (input == null) throw new IllegalArgumentException("Card catalog resource not found: " + resourcePath);
        try (input) {
            return load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close card catalog resource", exception);
        }
    }

    public List<CardData> cards() {
        return cards;
    }

    public List<CardDefinition> definitions() {
        return cards.stream().map(CardData::toDefinition).toList();
    }

    public CardData require(String id) {
        return cards.stream()
                .filter(card -> card.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown card ID: " + id));
    }

    private record CatalogDocument(int schemaVersion, List<CardData> cards) {}
}
