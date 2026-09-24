package com.infiniteconquest.cli;

import java.util.List;

public record BalanceReport(
        int matchesPerCapitalPair,
        long baseSeed,
        int totalMatches,
        int completedMatches,
        int draws,
        double averageTurns,
        double seatZeroWinRate,
        double averageUnusedGpAtEndTurn,
        double averageEndingHandSize,
        int matchesWithExhaustion,
        List<FactionResult> factions,
        List<CapitalResult> capitals,
        List<CardResult> cards,
        List<String> balanceFlags
) {
    public record FactionResult(String faction, int games, int wins, double winRate) {}
    public record CapitalResult(String capitalId, String faction, int games, int wins,
                                double winRate, int passiveTriggers, double triggersPerGame) {}
    public record CardResult(String cardId, String faction, int deckAppearances, int plays,
                             double playRate, int winsWhenPlayed) {}
}
