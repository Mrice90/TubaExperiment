package com.infiniteconquest.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infiniteconquest.core.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class BalanceSimulator {
    public static final int DEFAULT_MATCHES_PER_CAPITAL_PAIR = 2;
    public static final int MAX_TURNS = 120;
    public static final int MAX_ACTIONS_PER_TURN = 200;

    private final DemoMatchFactory matches;
    private final FactionDecks decks;
    private final BotPlayer bot = new BotPlayer();

    public BalanceSimulator() {
        matches = new DemoMatchFactory();
        decks = new FactionDecks(matches.pool());
    }

    public BalanceReport simulate(int matchesPerCapitalPair, long baseSeed) {
        if (matchesPerCapitalPair < 1) throw new IllegalArgumentException("Matches per Capital pair must be positive");
        Accumulator totals = new Accumulator(matchesPerCapitalPair, baseSeed);
        List<String> factions = new ArrayList<>(FactionDecks.FACTIONS);
        Collections.sort(factions);
        long matchIndex = 0;
        for (String firstFaction : factions) {
            for (String secondFaction : factions) {
                for (CardDefinition firstCapital : matches.capitals().forFaction(firstFaction)) {
                    for (CardDefinition secondCapital : matches.capitals().forFaction(secondFaction)) {
                        for (int repetition = 0; repetition < matchesPerCapitalPair; repetition++) {
                            long seed = mixSeed(baseSeed, matchIndex++);
                            MatchResult result = play(seed, firstFaction, secondFaction, firstCapital, secondCapital);
                            totals.add(result);
                        }
                    }
                }
            }
        }
        return totals.report();
    }

    public MatchResult play(long seed, String firstFaction, String secondFaction,
                            CardDefinition firstCapital, CardDefinition secondCapital) {
        List<CardDefinition> firstDeck = decks.starter(firstFaction);
        List<CardDefinition> secondDeck = decks.starter(secondFaction);
        return play(seed, firstFaction, secondFaction, firstCapital, secondCapital, firstDeck, secondDeck);
    }

    public MatchResult play(long seed, String firstFaction, String secondFaction,
                            CardDefinition firstCapital, CardDefinition secondCapital,
                            List<CardDefinition> firstDeck, List<CardDefinition> secondDeck) {
        GameState state = matches.create(seed,
                new DeckBuild("Balance", firstFaction, ally(firstFaction, firstDeck), firstCapital, firstDeck),
                new DeckBuild("Balance", secondFaction, ally(secondFaction, secondDeck), secondCapital, secondDeck),
                new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        CommandProcessor commands = new CommandProcessor(state);
        int unusedGp = 0;
        int endedTurns = 0;
        int actionsThisTurn = 0;

        while (state.phase() != Phase.GAME_OVER && state.turnNumber() <= MAX_TURNS) {
            int active = state.activePlayer();
            BotPlayer.Decision decision = bot.takeNextAction(state, commands, active);
            if (!decision.result().startsWith("OK:")) {
                throw new IllegalStateException("Bot selected rejected action: " + decision.command() + " — " + decision.result());
            }
            if (decision.command().equals("end")) {
                unusedGp += state.player(1 - state.activePlayer()).currentGp();
                endedTurns++;
                actionsThisTurn = 0;
            } else {
                actionsThisTurn++;
                if (state.phase() != Phase.GAME_OVER) bot.react(state, commands, 1 - active);
                if (actionsThisTurn >= MAX_ACTIONS_PER_TURN && state.phase() != Phase.GAME_OVER) {
                    unusedGp += state.player(active).currentGp();
                    new GameEngine().apply(state, new GameAction.EndTurn(active));
                    endedTurns++;
                    actionsThisTurn = 0;
                }
            }
        }

        Integer winner = state.winner().isPresent() ? state.winner().getAsInt() : null;
        boolean exhaustion = state.events().stream().anyMatch(event -> event.type() == GameEvent.Type.EXHAUSTION_DAMAGE);
        Map<String, Integer> cardPlays = new HashMap<>();
        int[] passiveTriggers = new int[2];
        for (GameEvent event : state.events()) {
            if (event.type() == GameEvent.Type.CARD_PLAYED) {
                state.card(UUID.fromString(event.detail())).ifPresent(card ->
                        cardPlays.merge(card.owner() + ":" + card.definition().id(), 1, Integer::sum));
            } else if (event.type() == GameEvent.Type.CAPITAL_PASSIVE_TRIGGERED) {
                passiveTriggers[event.playerId()]++;
            }
        }
        return new MatchResult(firstFaction, secondFaction, firstCapital.id(), secondCapital.id(),
                firstDeck, secondDeck, winner, Math.min(state.turnNumber(), MAX_TURNS), exhaustion,
                endedTurns == 0 ? 0 : (double) unusedGp / endedTurns,
                (state.player(0).hand().size() + state.player(1).hand().size()) / 2.0,
                passiveTriggers, cardPlays);
    }

    public void write(BalanceReport report, Path path) {
        try {
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(path.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not write balance report: " + path, exception);
        }
    }

    private String ally(String primary, List<CardDefinition> deck) {
        return deck.stream().map(CardDefinition::faction).filter(f -> !f.equals(primary) && DeckBuild.FACTIONS.contains(f))
                .findFirst().orElse(null);
    }

    private long mixSeed(long baseSeed, long index) {
        long value = baseSeed + 0x9E3779B97F4A7C15L * (index + 1);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record MatchResult(String firstFaction, String secondFaction,
                              String firstCapital, String secondCapital,
                              List<CardDefinition> firstDeck, List<CardDefinition> secondDeck,
                              Integer winner, int turns, boolean exhaustion,
                              double averageUnusedGp, double averageEndingHand,
                              int[] passiveTriggers, Map<String, Integer> cardPlays) {}

    static final class Accumulator {
        private final int matchesPerPair;
        private final long seed;
        private final Map<String, MutableResult> factions = new TreeMap<>();
        private final Map<String, MutableCapital> capitals = new TreeMap<>();
        private final Map<String, MutableCard> cards = new TreeMap<>();
        private int total;
        private int completed;
        private int draws;
        private int firstWins;
        private int exhaustion;
        private long turns;
        private double unusedGp;
        private double endingHands;

        Accumulator(int matchesPerPair, long seed) { this.matchesPerPair = matchesPerPair; this.seed = seed; }

        void add(MatchResult match) {
            total++; turns += match.turns(); unusedGp += match.averageUnusedGp(); endingHands += match.averageEndingHand();
            if (match.exhaustion()) exhaustion++;
            MutableResult first = factions.computeIfAbsent(match.firstFaction(), ignored -> new MutableResult());
            MutableResult second = factions.computeIfAbsent(match.secondFaction(), ignored -> new MutableResult());
            first.games++; second.games++;
            MutableCapital firstCapital = capitals.computeIfAbsent(match.firstCapital(), ignored -> new MutableCapital(match.firstFaction()));
            MutableCapital secondCapital = capitals.computeIfAbsent(match.secondCapital(), ignored -> new MutableCapital(match.secondFaction()));
            firstCapital.games++; secondCapital.games++;
            firstCapital.triggers += match.passiveTriggers()[0]; secondCapital.triggers += match.passiveTriggers()[1];

            addDeck(0, match.firstDeck(), match.cardPlays());
            addDeck(1, match.secondDeck(), match.cardPlays());
            if (match.winner() == null) { draws++; return; }
            completed++;
            String winningFaction = match.winner() == 0 ? match.firstFaction() : match.secondFaction();
            String winningCapital = match.winner() == 0 ? match.firstCapital() : match.secondCapital();
            factions.get(winningFaction).wins++;
            capitals.get(winningCapital).wins++;
            if (match.winner() == 0) firstWins++;
            List<CardDefinition> winningDeck = match.winner() == 0 ? match.firstDeck() : match.secondDeck();
            int winner = match.winner();
            for (CardDefinition card : new LinkedHashSet<>(winningDeck)) if (match.cardPlays().containsKey(winner + ":" + card.id())) {
                cards.get(card.id()).winsWhenPlayed++;
            }
        }

        private void addDeck(int playerId, List<CardDefinition> deck, Map<String, Integer> plays) {
            for (CardDefinition card : new LinkedHashSet<>(deck)) {
                MutableCard stat = cards.computeIfAbsent(card.id(), ignored -> new MutableCard(card.faction()));
                stat.appearances++;
                stat.plays += plays.getOrDefault(playerId + ":" + card.id(), 0);
            }
        }

        BalanceReport report() {
            List<BalanceReport.FactionResult> factionResults = factions.entrySet().stream().map(entry ->
                    new BalanceReport.FactionResult(entry.getKey(), entry.getValue().games, entry.getValue().wins,
                            rate(entry.getValue().wins, entry.getValue().games))).toList();
            List<BalanceReport.CapitalResult> capitalResults = capitals.entrySet().stream().map(entry ->
                    new BalanceReport.CapitalResult(entry.getKey(), entry.getValue().faction, entry.getValue().games,
                            entry.getValue().wins, rate(entry.getValue().wins, entry.getValue().games), entry.getValue().triggers,
                            rate(entry.getValue().triggers, entry.getValue().games))).toList();
            List<BalanceReport.CardResult> cardResults = cards.entrySet().stream().map(entry ->
                    new BalanceReport.CardResult(entry.getKey(), entry.getValue().faction, entry.getValue().appearances,
                            entry.getValue().plays, rate(entry.getValue().plays, entry.getValue().appearances),
                            entry.getValue().winsWhenPlayed))
                    .sorted(Comparator.comparingDouble(BalanceReport.CardResult::playRate).reversed()
                            .thenComparing(BalanceReport.CardResult::cardId)).toList();
            List<String> flags = new ArrayList<>();
            factionResults.forEach(result -> {
                if (result.winRate() < 0.45) flags.add(result.faction() + " faction win rate below 45%");
                if (result.winRate() > 0.55) flags.add(result.faction() + " faction win rate above 55%");
            });
            capitalResults.forEach(result -> {
                if (result.games() >= 10 && result.winRate() < 0.40) flags.add(result.capitalId() + " Capital win rate below 40%");
                if (result.games() >= 10 && result.winRate() > 0.60) flags.add(result.capitalId() + " Capital win rate above 60%");
                if (result.triggersPerGame() < 0.25) flags.add(result.capitalId() + " passive rarely triggers");
            });
            if (draws > total / 10) flags.add("Draw rate above 10%");
            return new BalanceReport(matchesPerPair, seed, total, completed, draws, rate(turns, total),
                    rate(firstWins, completed), rate(unusedGp, total), rate(endingHands, total), exhaustion,
                    factionResults, capitalResults, cardResults, List.copyOf(flags));
        }

        private double rate(double numerator, double denominator) {
            return denominator == 0 ? 0 : Math.round((numerator / denominator) * 10_000.0) / 10_000.0;
        }
    }

    private static final class MutableResult { int games; int wins; }
    private static final class MutableCapital {
        final String faction; int games; int wins; int triggers;
        MutableCapital(String faction) { this.faction = faction; }
    }
    private static final class MutableCard {
        final String faction; int appearances; int plays; int winsWhenPlayed;
        MutableCard(String faction) { this.faction = faction; }
    }
}
