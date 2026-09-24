package com.infiniteconquest.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class DeckFileStore {
    private static final int SCHEMA_VERSION = 1;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void save(Path path, String name, List<CardDefinition> deck) {
        List<String> errors = new DeckValidator().validate(deck);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        Map<String, Integer> counts = new TreeMap<>();
        for (CardDefinition card : deck) counts.merge(card.id(), 1, Integer::sum);
        List<CardEntry> entries = counts.entrySet().stream()
                .map(entry -> new CardEntry(entry.getKey(), entry.getValue())).toList();
        try {
            json.writeValue(path.toFile(), new DeckDocument(SCHEMA_VERSION, name, entries));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not save deck: " + path, exception);
        }
    }

    public List<CardDefinition> load(Path path, PrototypeCardPool pool) {
        final DeckDocument document;
        try {
            if (json.readTree(path.toFile()).path("schemaVersion").asInt() == 2) {
                return new DeckBuildStore(pool, new CapitalRoster()).load(path).cards();
            }
            document = json.readValue(path.toFile(), DeckDocument.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not load deck: " + path, exception);
        }
        if (document.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported deck schema version: " + document.schemaVersion());
        }
        List<CardDefinition> deck = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CardEntry entry : document.cards()) {
            if (!seen.add(entry.id())) throw new IllegalArgumentException("Duplicate deck entry: " + entry.id());
            if (entry.copies() < 1 || entry.copies() > DeckValidator.MAX_COPIES) {
                throw new IllegalArgumentException(entry.id() + " copies must be between 1 and 4");
            }
            CardDefinition definition = pool.require(entry.id());
            for (int copy = 0; copy < entry.copies(); copy++) deck.add(definition);
        }
        List<String> errors = new DeckValidator().validate(deck);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        return List.copyOf(deck);
    }

    private record DeckDocument(int schemaVersion, String name, List<CardEntry> cards) {}
    private record CardEntry(String id, int copies) {}
}
