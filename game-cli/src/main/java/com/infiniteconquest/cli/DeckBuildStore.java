package com.infiniteconquest.cli;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.infiniteconquest.core.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Production deck persistence and ICD1 exchange, compatible with the approved prototype. */
public final class DeckBuildStore {
    private final ObjectMapper json = new ObjectMapper();
    private final PrototypeCardPool pool;
    private final CapitalRoster capitals;
    public DeckBuildStore(PrototypeCardPool pool, CapitalRoster capitals) { this.pool = pool; this.capitals = capitals; }

    public DeckBuild load(Path path) {
        try {
            if (Files.size(path) > 100_000) throw new IllegalArgumentException("Deck file is too large");
            JsonNode root = json.readTree(path.toFile());
            if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid deck file");
            if (root.path("schemaVersion").asInt() == 1) {
                List<CardDefinition> cards = new DeckFileStore().load(path, pool);
                Set<String> factions = new HashSet<>();
                cards.stream().map(CardDefinition::faction).filter(f -> !f.equals("NEUTRAL")).forEach(factions::add);
                if (factions.size() != 1 || !DeckBuild.FACTIONS.contains(factions.iterator().next()))
                    throw new IllegalArgumentException("Legacy mixed or Neutral deck needs a primary faction and optional ally. Original file preserved: " + path);
                String primary = factions.iterator().next();
                return new DeckBuild(root.path("name").asText("Custom Deck"), primary, null, capitals.forFaction(primary).get(0), cards);
            }
            if (root.path("schemaVersion").asInt() != 2) throw new IllegalArgumentException("Unsupported deck schema");
            ArrayNode entries = json.createArrayNode();
            if (!root.path("cards").isArray()) throw new IllegalArgumentException("Missing cards");
            for (JsonNode card : root.path("cards")) entries.addArray().add(card.get("id")).add(card.get("copies"));
            return decodeDeck(root.path("name").asText("Custom Deck"), json.createArrayNode()
                    .add(root.get("primaryFaction")).add(root.get("allyFaction")).add(root.get("capitalId")).add(entries));
        } catch (IOException e) { throw new IllegalArgumentException("Could not read deck", e); }
    }

    public void save(Path path, DeckBuild deck) {
        ObjectNode root = json.createObjectNode();
        root.put("schemaVersion", 2).put("name", deck.name()).put("primaryFaction", deck.primaryFaction());
        root.put("allyFaction", deck.allyFaction()).put("capitalId", deck.capital().id());
        ArrayNode entries = root.putArray("cards");
        counts(deck).forEach((id, n) -> entries.addObject().put("id", id).put("copies", n));
        Path temporary = null;
        try {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            if (Files.exists(absolute) && json.readTree(absolute.toFile()).path("schemaVersion").asInt() == 1) {
                Path backup = absolute.resolveSibling(absolute.getFileName() + ".legacy-v1.bak");
                if (!Files.exists(backup)) Files.copy(absolute, backup);
            }
            temporary = Files.createTempFile(absolute.getParent(), "deck-", ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalArgumentException("Could not save deck", e); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    public String exportCode(DeckBuild deck) {
        ArrayNode payload = json.createArrayNode().add(deck.primaryFaction()).add(deck.allyFaction()).add(deck.capital().id());
        ArrayNode entries = payload.addArray();
        counts(deck).forEach((id,n) -> entries.addArray().add(id).add(n));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return "ICD1." + body + "." + checksum(body);
    }

    public DeckBuild importCode(String input) {
        if (input == null || input.length() > 64_000) throw new IllegalArgumentException("Deck code is too long");
        String code = input.replaceAll("\\s", "");
        String[] parts = code.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals("ICD1")) throw new IllegalArgumentException("Use a complete ICD1 deck code");
        if (!parts[1].matches("[A-Za-z0-9_-]+") || !checksum(parts[1]).equals(parts[2]))
            throw new IllegalArgumentException("Deck code is damaged; copy the whole code again");
        try { return decodeDeck("Imported Deck", json.readTree(Base64.getUrlDecoder().decode(parts[1]))); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid deck code data", e); }
    }

    private DeckBuild decodeDeck(String name, JsonNode data) {
        if (data == null || !data.isArray() || data.size() != 4 || !data.get(0).isTextual()
                || !(data.get(1).isNull() || data.get(1).isTextual()) || !data.get(2).isTextual()
                || !data.get(3).isArray() || data.get(3).size() > pool.cards().size())
            throw new IllegalArgumentException("Invalid deck metadata");
        List<CardDefinition> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : data.get(3)) {
            if (!entry.isArray() || entry.size() != 2 || !entry.get(0).isTextual() || !entry.get(1).isIntegralNumber()
                    || !entry.get(1).canConvertToInt() || entry.get(1).intValue() < 1 || entry.get(1).intValue() > 4
                    || !seen.add(entry.get(0).textValue())) throw new IllegalArgumentException("Invalid or duplicate card entry");
            CardDefinition card = pool.require(entry.get(0).textValue());
            for (int i = 0; i < entry.get(1).intValue(); i++) cards.add(card);
        }
        return new DeckBuild(name, data.get(0).textValue(), data.get(1).isNull() ? null : data.get(1).textValue(),
                capitals.require(data.get(2).textValue()), cards);
    }
    private Map<String, Integer> counts(DeckBuild deck) {
        Map<String, Integer> counts = new TreeMap<>();
        deck.cards().forEach(c -> counts.merge(c.id(), 1, Integer::sum));
        return counts;
    }
    private String checksum(String body) {
        int hash = 0x811c9dc5;
        for (byte b : body.getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (b & 255)) * 0x01000193;
        return String.format(Locale.ROOT, "%08x", hash);
    }
}
