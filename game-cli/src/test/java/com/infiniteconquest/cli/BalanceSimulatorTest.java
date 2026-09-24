package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BalanceSimulatorTest {
    @Test
    void headlessMatchLetsBotsControlBothPlayersDeterministically() {
        DemoMatchFactory factory = new DemoMatchFactory();
        BalanceSimulator simulator = new BalanceSimulator();
        CardDefinition zeusCapital = factory.capitals().require("zeus_capital_keraunos_spire");
        CardDefinition poseidonCapital = factory.capitals().require("poseidon_capital_atlantis_nexus");

        BalanceSimulator.MatchResult first = simulator.play(42L, "ZEUS", "POSEIDON", zeusCapital, poseidonCapital);
        BalanceSimulator.MatchResult second = simulator.play(42L, "ZEUS", "POSEIDON", zeusCapital, poseidonCapital);

        assertEquals(first.winner(), second.winner());
        assertEquals(first.turns(), second.turns());
        assertEquals(first.cardPlays(), second.cardPlays());
        assertTrue(first.turns() <= BalanceSimulator.MAX_TURNS);
        assertFalse(first.cardPlays().isEmpty());
    }

    @Test
    void duplicateDeckCopiesDoNotMultiplyPlayEventsOrWins() {
        var card = new PrototypeCardPool().require("zeus_cloudline_courier");
        var totals = new BalanceSimulator.Accumulator(1, 1L);
        totals.add(new BalanceSimulator.MatchResult("POSEIDON", "ZEUS", "poseidon_capital", "zeus_capital",
                java.util.List.of(card, card, card, card), java.util.List.of(), 0, 10, false,
                0, 0, new int[]{0, 0}, java.util.Map.of("0:" + card.id(), 2)));
        var result = totals.report().cards().get(0);
        assertEquals(1, result.deckAppearances());
        assertEquals(2, result.plays());
        assertEquals(1, result.winsWhenPlayed());
        assertEquals(1.0, totals.report().seatZeroWinRate());
    }

    @Test
    void reportSchemaCarriesCoreBalanceSignals() {
        BalanceReport report = new BalanceReport(1, 7L, 9, 8, 1, 18.5, 0.5,
                1.2, 4.0, 2, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of("Draw rate above 10%"));

        assertEquals(9, report.totalMatches());
        assertEquals(2, report.matchesWithExhaustion());
        assertFalse(report.balanceFlags().isEmpty());
    }
}
