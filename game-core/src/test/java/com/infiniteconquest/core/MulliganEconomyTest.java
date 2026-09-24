package com.infiniteconquest.core;

import com.infiniteconquest.data.CardCatalog;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MulliganEconomyTest {
    private List<CardDefinition> validDeck(String prefix) {
        List<CardDefinition> cards = new ArrayList<>();
        for (int id = 0; id < 10; id++) {
            CardDefinition definition = new CardDefinition(prefix + id, prefix + id,
                    CardType.LAND, "DEV", 1, 0, 0, 0, 0, 5);
            for (int copy = 0; copy < 4; copy++) cards.add(definition);
        }
        return cards;
    }

    @Test void mulliganDiscardsAndRedrawsAtMostThreeOpeningCards() {
        GameState state = new MatchFactory().create(42L, MatchRules.current(), validDeck("a"), validDeck("b"));
        List<UUID> opening = new ArrayList<>(state.player(0).hand());
        Set<UUID> discarded = new LinkedHashSet<>(opening.subList(0, 2));

        state.mulligan(0, discarded);

        assertEquals(opening.size(), state.player(0).hand().size());
        assertTrue(state.player(0).hand().stream().noneMatch(discarded::contains));
        assertEquals(discarded.size(), state.player(0).discard().size());
        assertThrows(IllegalStateException.class, () -> state.mulligan(0, discarded));
    }

    @Test void mulliganRejectsDiscardingMoreThanThreeCards() {
        GameState state = new MatchFactory().create(43L, MatchRules.current(), validDeck("a"), validDeck("b"));
        assertThrows(IllegalArgumentException.class,
                () -> state.mulligan(0, state.player(0).hand().subList(0, 4)));
    }

    @Test void standardIncomeRisesEveryTwoDevelopmentTurns() {
        assertEquals(1, DevelopmentRules.standardGp(CardType.LAND, 1));
        assertEquals(1, DevelopmentRules.standardGp(CardType.STRUCTURE, 2));
        assertEquals(2, DevelopmentRules.standardGp(CardType.LAND, 3));
        assertEquals(3, DevelopmentRules.standardGp(CardType.LAND, 6));
        assertEquals(5, DevelopmentRules.standardGp(CardType.STRUCTURE, 10));
    }

    @Test void controlledDevelopmentGeneratesItsPrintedIncome() {
        GameState state = new GameState(8L);
        add(state, new CardDefinition("land", "Land", CardType.LAND, "DEV", 1,
                0, 0, 0, 0, 5, Set.of(), List.of(), 1, DevelopmentPassive.NONE), 0, new BoardPosition(0, 0));
        add(state, new CardDefinition("structure", "Structure", CardType.STRUCTURE, "DEV", 5,
                0, 0, 0, 0, 5, Set.of(), List.of(), 3, DevelopmentPassive.NONE), 0, new BoardPosition(1, 0));

        state.advanceTurn();
        state.advanceTurn();

        assertEquals(4, state.player(0).currentGp());
        assertEquals(4, state.gpIncomePerTurn(0));
    }

    @Test void factionSetContainsLowerIncomeUtilityDevelopments() {
        List<CardDefinition> cards = CardCatalog.loadResource("/cards/faction-cards.json").definitions();
        List<CardDefinition> utility = cards.stream()
                .filter(card -> card.developmentPassive() != DevelopmentPassive.NONE).toList();
        long factions = cards.stream().map(CardDefinition::faction).distinct().count();
        assertEquals(factions, utility.size(), "one utility development per faction");
        assertTrue(utility.stream().allMatch(card -> card.gpGeneration()
                < DevelopmentRules.standardGp(card.type(), card.cost())));
    }

    private CardInstance add(GameState state, CardDefinition definition, int owner, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }
}
