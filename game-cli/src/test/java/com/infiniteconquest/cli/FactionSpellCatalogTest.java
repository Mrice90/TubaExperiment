package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DeckValidator;
import com.infiniteconquest.core.SpellTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FactionSpellCatalogTest {
    @Test
    void everyFactionHasFiveExecutableLowCostSpells() {
        PrototypeCardPool pool = new PrototypeCardPool();
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> spells = pool.cardsForFaction(faction).stream()
                    .filter(card -> card.type() == CardType.SPELL)
                    .filter(card -> !card.id().contains("_apex_")).toList();
            assertEquals(5, spells.size(), faction);
            for (CardDefinition spell : spells) {
                assertFalse(spell.effects().isEmpty(), spell.id());
                assertTrue(spell.cost() >= 2 && spell.cost() <= 4, spell.id());
                assertNotEquals(SpellTarget.NONE, spell.effects().get(0).target(), spell.id());
            }
        }
    }

    @Test
    void factionStartersRemainLegalAfterAddingSpells() {
        PrototypeCardPool pool = new PrototypeCardPool();
        FactionDecks decks = new FactionDecks(pool);
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> deck = decks.starter(faction);
            assertEquals(60, deck.size());
            assertTrue(new DeckValidator().isValid(deck));
            assertTrue(deck.stream().anyMatch(card -> card.type() == CardType.SPELL));
        }
    }
}
