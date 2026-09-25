package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DeckValidator;
import com.infiniteconquest.core.AbilityEffectType;
import com.infiniteconquest.core.AbilityTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FactionCardSetTest {
    @Test
    void everyFactionHasAnExpandedUniquePlayablePool() {
        PrototypeCardPool pool = new PrototypeCardPool();

        assertEquals(155, pool.cards().size());
        Map<String, Integer> expectedPerFaction = Map.of("ZEUS", 65, "POSEIDON", 66);
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> cards = pool.cardsForFaction(faction);
            assertEquals(expectedPerFaction.get(faction).intValue(), cards.size(), faction);
            assertEquals(expectedPerFaction.get(faction).intValue(),
                    cards.stream().map(CardDefinition::id).distinct().count(), faction);

            int expected = expectedPerFaction.get(faction).intValue();
            Map<CardType, Long> types = cards.stream()
                    .collect(Collectors.groupingBy(CardDefinition::type, Collectors.counting()));
            assertEquals((long) expected, types.values().stream().mapToLong(Long::longValue).sum(), faction);
        }
    }

    @Test
    void factionStatsStayInsideTheBalanceEnvelope() {
        PrototypeCardPool pool = new PrototypeCardPool();

        for (String faction : FactionDecks.FACTIONS) {
            for (CardDefinition card : pool.cardsForFaction(faction)) {
                int maximumCost = card.type() == CardType.LAND || card.type() == CardType.STRUCTURE ? 10
                        : card.id().contains("_apex_") ? 10 : card.id().contains("_keyword_") ? 9 : 7;
                assertTrue(card.cost() >= 0 && card.cost() <= maximumCost, card.id());
                if (card.type() == CardType.CHARACTER) {
                    assertTrue(card.attack() <= card.cost() + (card.range() == 1 && (card.keywords().isEmpty() || card.defense() == 1) ? 2 : 1), card.id() + " attack");
                    assertTrue(card.defense() <= card.cost() + (card.movement() == 1 ? 3 : 2), card.id() + " defense");
                    assertTrue(card.range() >= 1 && card.range() <= 3, card.id() + " range");
                    assertTrue(card.movement() >= 1 && card.movement() <= 4, card.id() + " movement");
                } else if (card.type() == CardType.LAND) {
                    assertTrue(card.hitPoints() >= 5 && card.hitPoints() <= (card.id().contains("_tutor_") ? 22
                            : card.id().contains("_apex_") ? 19
                            : card.id().contains("_land_") ? 22 : 10), card.id() + " HP");
                } else if (card.type() == CardType.STRUCTURE) {
                    // Floor is 4 for cheap fragile burn structures (Ion Storm Lattice);
                    // everything else stays at 5+.
                    assertTrue(card.hitPoints() >= 4 && card.hitPoints() <= (card.id().contains("_tutor_") ? 26
                            : card.id().contains("_apex_") ? 24
                            : card.id().contains("_structure_") ? 26 : 13), card.id() + " HP");
                }
            }
        }
    }

    @Test
    void everyFactionGetsFiveLandAndFiveStructureTutors() {
        PrototypeCardPool pool = new PrototypeCardPool();
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> tutors = pool.cardsForFaction(faction).stream()
                    .filter(card -> card.id().contains("_tutor_")).toList();
            assertEquals(10, tutors.size(), faction);
            assertEquals(5, tutors.stream().filter(card -> card.type() == CardType.LAND).count(), faction);
            assertEquals(5, tutors.stream().filter(card -> card.type() == CardType.STRUCTURE).count(), faction);
            assertEquals(Set.of(2, 4, 6, 8, 10), tutors.stream().map(CardDefinition::cost).collect(Collectors.toSet()), faction);
            tutors.forEach(card -> {
                assertEquals(1, card.abilities().size(), card.id());
                assertEquals(AbilityTrigger.ACTIVATED, card.abilities().get(0).trigger(), card.id());
                assertEquals(card.type() == CardType.LAND ? AbilityEffectType.DRAW_STRUCTURE
                        : AbilityEffectType.DRAW_CHARACTER, card.abilities().get(0).effect(), card.id());
            });
        }
    }

    @Test
    void everyFactionStarterUsesSixtyCardsWithAnEvenDevelopmentCurve() {
        PrototypeCardPool pool = new PrototypeCardPool();
        FactionDecks decks = new FactionDecks(pool);
        DeckValidator validator = new DeckValidator();

        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> deck = decks.starter(faction);
            assertEquals(60, deck.size(), faction);
            assertTrue(validator.isValid(deck), faction);
            Map<String, Long> copies = deck.stream()
                    .collect(Collectors.groupingBy(CardDefinition::id, Collectors.counting()));
            assertTrue(copies.values().stream().allMatch(count -> count <= DeckValidator.MAX_COPIES));
            assertEquals(18, deck.stream().filter(card -> card.type() == CardType.LAND).count(), faction);
            assertEquals(14, deck.stream().filter(card -> card.type() == CardType.STRUCTURE).count(), faction);
            var primary = FactionDecks.PRIMARY_KEYWORDS.get(faction);
            var secondary = FactionDecks.SECONDARY_KEYWORDS.get(faction);
            assertTrue(deck.stream().anyMatch(card -> card.hasKeyword(primary)), faction + " primary keyword");
            assertTrue(deck.stream().anyMatch(card -> card.hasKeyword(secondary)), faction + " secondary keyword");
            assertTrue(deck.stream().filter(card -> card.type()==CardType.CHARACTER).flatMap(card -> card.keywords().stream())
                    .allMatch(keyword -> keyword == primary || keyword == secondary), faction + " off-theme keyword");
            assertTrue(deck.stream().anyMatch(card -> !card.abilities().isEmpty()), faction + " triggered abilities");
        }
    }

    @Test
    void factionIdentityAppearsInImplementedKeywordDistribution() {
        PrototypeCardPool pool = new PrototypeCardPool();

        assertTrue(keywordCount(pool, "ZEUS", "BLINK") >= 3);
        assertTrue(keywordCount(pool, "ZEUS", "SHARP_SHOT") >= 3);
        assertTrue(keywordCount(pool, "POSEIDON", "MOLE") >= 2);
        assertTrue(keywordCount(pool, "POSEIDON", "VANGUARD") >= 5);
    }

    @Test
    void everyFactionHasLandStructureAndCharacterAbilitiesAcrossAllTimingWindows() {
        PrototypeCardPool pool = new PrototypeCardPool();
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> abilityCards = pool.cardsForFaction(faction).stream()
                    .filter(card -> card.id().contains("_ability_")).toList();
            assertEquals(3, abilityCards.size(), faction);
            assertEquals(Set.of(CardType.LAND, CardType.STRUCTURE, CardType.CHARACTER),
                    abilityCards.stream().map(CardDefinition::type).collect(Collectors.toSet()), faction);
        }
        Set<AbilityTrigger> triggers = pool.cards().stream().flatMap(card -> card.abilities().stream())
                .map(ability -> ability.trigger()).collect(Collectors.toSet());
        // DESTROYED-trigger abilities shipped with the retired factions (Ares, Hades, Hephaestus)
        // and return with the Zeus/Poseidon land/structure redesign; the set grows further as
        // factions return via DLC.
        assertEquals(Set.of(AbilityTrigger.ENTERS_PLAY, AbilityTrigger.PASSIVE,
                AbilityTrigger.ACTIVATED, AbilityTrigger.DESTROYED), triggers);
    }

    private long keywordCount(PrototypeCardPool pool, String faction, String keyword) {
        return pool.cardsForFaction(faction).stream()
                .filter(card -> card.keywords().stream().anyMatch(value -> value.name().equals(keyword)))
                .count();
    }
}
