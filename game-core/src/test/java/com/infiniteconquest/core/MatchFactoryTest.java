package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchFactoryTest {
    private List<CardDefinition> validDeck(String prefix) {
        List<CardDefinition> cards = new ArrayList<>();
        for (int id = 0; id < 15; id++) {
            CardDefinition definition = new CardDefinition(
                    prefix + id, prefix + id, CardType.LAND, "DEV", 0, 0, 0, 0, 0);
            for (int copy = 0; copy < 4; copy++) cards.add(definition);
        }
        return cards;
    }

    @Test void sameSeedProducesSameOpeningState() {
        MatchFactory factory = new MatchFactory();
        GameState first = factory.create(849291L, MatchRules.current(), validDeck("a"), validDeck("b"));
        GameState second = factory.create(849291L, MatchRules.current(), validDeck("a"), validDeck("b"));

        assertEquals(first.player(0).hand(), second.player(0).hand());
        assertEquals(first.player(1).hand(), second.player(1).hand());
        assertEquals(first.startingPlayer(), second.startingPlayer());
        int starter = first.startingPlayer();
        assertEquals(5, first.player(starter).hand().size(), "Starting player opens with five cards");
        assertEquals(55, first.player(starter).deck().size());
        assertEquals(6, first.player(1 - starter).hand().size(), "Second player receives a sixth opening card");
        assertEquals(54, first.player(1 - starter).deck().size());
    }

    @Test void coinFlipVariesAndPlayerTwoGetsEconomyBonus() {
        MatchFactory factory = new MatchFactory();
        Set<Integer> winners = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            GameState state = factory.create(seed, MatchRules.current(), validDeck("a"), validDeck("b"));
            winners.add(state.startingPlayer());
            assertEquals(0, state.player(0).currentGp());
            assertEquals(1, state.player(1).currentGp());
        }
        assertEquals(Set.of(0, 1), winners);
    }

    @Test void differentSeedChangesOpeningOrder() {
        MatchFactory factory = new MatchFactory();
        GameState first = factory.create(1L, MatchRules.current(), validDeck("a"), validDeck("b"));
        GameState second = factory.create(2L, MatchRules.current(), validDeck("a"), validDeck("b"));
        assertNotEquals(first.player(0).hand(), second.player(0).hand());
    }

    @Test void rejectsInvalidDeckBeforeCreatingState() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchFactory().create(1L, MatchRules.current(), List.of(), validDeck("b")));
    }
}
