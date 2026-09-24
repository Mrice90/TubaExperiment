package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeckValidatorTest {
    private CardDefinition land(String id) {
        return new CardDefinition(id, id, CardType.LAND, "DEV", 0, 0, 0, 0, 0);
    }

    @Test void acceptsFortyCardsWithTenDistinctIdsAndFourCopiesEach() {
        List<CardDefinition> cards = new ArrayList<>();
        for (int i = 0; i < 10; i++) for (int copy = 0; copy < 4; copy++) cards.add(land("dev_" + i));
        assertTrue(new DeckValidator().isValid(cards));
    }

    @Test void rejectsWrongSizeCopyLimitAndInsufficientDiversity() {
        List<CardDefinition> cards = new ArrayList<>();
        for (int i = 0; i < 40; i++) cards.add(land("same"));
        List<String> errors = new DeckValidator().validate(cards);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("four-copy")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("10 distinct")));
    }
}
