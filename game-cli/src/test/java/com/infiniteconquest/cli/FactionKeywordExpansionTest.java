package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FactionKeywordExpansionTest {
    @Test
    void everyFactionGetsThreeTieredPrimaryAndTwoTieredSecondaryCards() {
        PrototypeCardPool pool = new PrototypeCardPool();
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> expansion = pool.cardsForFaction(faction).stream()
                    .filter(card -> card.id().contains("_keyword_")).toList();
            assertEquals(5, expansion.size(), faction);
            assertKeywordCards(expansion, FactionDecks.PRIMARY_KEYWORDS.get(faction), faction.equals("ZEUS") ? List.of(2, 5, 9) : List.of(2, 5, 8));
            assertKeywordCards(expansion, FactionDecks.SECONDARY_KEYWORDS.get(faction), faction.equals("ATHENA") ? List.of(4, 6) : List.of(3, 6));
        }
    }

    @Test
    void everyFactionHasThreeSelectableCapitalsOutsideTheDeckPool() {
        CapitalRoster roster = new CapitalRoster();
        PrototypeCardPool pool = new PrototypeCardPool();
        assertEquals(6, roster.all().size());
        for (String faction : FactionDecks.FACTIONS) {
            List<CardDefinition> capitals = roster.forFaction(faction);
            assertEquals(3, capitals.size(), faction);
            assertTrue(capitals.stream().allMatch(card -> card.type() == CardType.CAPITAL && card.hitPoints() == 20));
            assertTrue(pool.cardsForFaction(faction).stream().noneMatch(card -> card.type() == CardType.CAPITAL));
        }
    }

    @Test
    void chosenCapitalIsDeployedAndMustMatchSingleFactionDeck() {
        DemoMatchFactory matches = new DemoMatchFactory();
        List<CardDefinition> zeus = new FactionDecks(matches.pool()).starter("ZEUS");
        List<CardDefinition> poseidon = new FactionDecks(matches.pool()).starter("POSEIDON");
        CardDefinition zeusCapital = matches.capitals().require("zeus_capital_keraunos_spire");
        var state = matches.create(99L, zeus, poseidon, zeusCapital,
                matches.capitals().require("poseidon_capital_atlantis_nexus"));
        assertEquals(zeusCapital.id(), state.board().topAt(new com.infiniteconquest.core.BoardPosition(1, 0))
                .flatMap(state::card).orElseThrow().definition().id());
        assertThrows(IllegalArgumentException.class, () -> matches.create(99L, zeus, poseidon,
                matches.capitals().require("poseidon_capital_atlantis_nexus"),
                matches.capitals().require("zeus_capital_keraunos_spire")));
    }

    private void assertKeywordCards(List<CardDefinition> cards, Keyword keyword, List<Integer> costs) {
        List<CardDefinition> matches = cards.stream().filter(card -> card.hasKeyword(keyword)).toList();
        assertEquals(costs, matches.stream().map(CardDefinition::cost).sorted().toList(), keyword.name());
        assertTrue(matches.stream().allMatch(card -> card.type() == CardType.CHARACTER));
    }
}
