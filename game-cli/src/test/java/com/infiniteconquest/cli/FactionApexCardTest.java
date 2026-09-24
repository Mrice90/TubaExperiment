package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FactionApexCardTest {
    @Test
    void everyFactionGetsFivePrimaryThreeSecondaryAndOneOtherType() {
        PrototypeCardPool pool = new PrototypeCardPool();
        FactionDecks decks = new FactionDecks(pool);

        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> apex = pool.cardsForFaction(faction).stream()
                    .filter(card -> card.id().contains("_apex_")).toList();
            assertEquals(10, apex.size(), faction);
            Map<CardType, Long> counts = apex.stream()
                    .collect(Collectors.groupingBy(CardDefinition::type, Collectors.counting()));
            CardType primary = FactionDecks.PRIMARY_TYPES.get(faction);
            CardType secondary = FactionDecks.SECONDARY_TYPES.get(faction);
            assertEquals(5L, counts.getOrDefault(primary, 0L), faction + " primary");
            assertEquals(3L, counts.getOrDefault(secondary, 0L), faction + " secondary");
            for (CardType type : List.of(CardType.CHARACTER, CardType.LAND, CardType.STRUCTURE, CardType.SPELL)) {
                if (type != primary && type != secondary) {
                    assertEquals(1L, counts.getOrDefault(type, 0L), faction + " " + type);
                }
            }
        }
    }

    @Test
    void apexCardsPriceTheirActualEffectsAndKeepLateGameBodies() {
        PrototypeCardPool pool = new PrototypeCardPool();
        for (String faction : FactionDecks.FACTIONS) {
            for (CardDefinition card : pool.cardsForFaction(faction).stream()
                    .filter(value -> value.id().contains("_apex_")).toList()) {
                assertTrue(card.cost() >= (card.type() == CardType.SPELL ? 2 : 5) && card.cost() <= 10, card.id());
                if (card.type() == CardType.SPELL) assertFalse(card.effects().isEmpty(), card.id());
                if (card.type() == CardType.LAND) assertTrue(card.hitPoints() >= 14, card.id());
                if (card.type() == CardType.STRUCTURE) assertTrue(card.hitPoints() >= 16, card.id());
            }
        }
    }

    @Test
    void startersContainSixtyFactionCards() {
        PrototypeCardPool pool = new PrototypeCardPool();
        FactionDecks decks = new FactionDecks(pool);
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> deck = decks.starter(faction);
            assertEquals(60, deck.size());
            assertTrue(new DeckValidator().isValid(deck));
            Map<String, Long> copies = deck.stream()
                    .collect(Collectors.groupingBy(CardDefinition::id, Collectors.counting()));
            assertTrue(copies.values().stream().allMatch(count -> count <= DeckValidator.MAX_COPIES), faction);
            assertEquals(32, deck.stream().filter(card -> card.type() == CardType.LAND
                    || card.type() == CardType.STRUCTURE).count(), faction);
        }
    }
}
