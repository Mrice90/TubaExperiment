package com.infiniteconquest.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infiniteconquest.core.*;
import java.nio.file.*;
import java.util.*;

/** Fixed decks/seeds on the playable hex board; export includes generated cards and Capital rules. */
public final class BalancePassRunner {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]); Files.createDirectories(out);
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        PrototypeCardPool pool = new PrototypeCardPool(); CapitalRoster capitals = new CapitalRoster();
        List<CardDefinition> all = new ArrayList<>(pool.cards()); all.addAll(capitals.all());
        Map<String, String> passives = new TreeMap<>();
        capitals.all().forEach(c -> passives.put(c.id(), new CapitalPassiveRules().description(c)));
        json.writeValue(out.resolve("catalog.json").toFile(), Map.of("cards", all, "capitalPassives", passives));
        Path manifest = Path.of(args[1]);
        List<Scenario> scenarios;
        if (Files.exists(manifest)) {
            scenarios = Arrays.asList(json.readValue(manifest.toFile(), Scenario[].class));
        } else {
            scenarios = new ArrayList<>();
            List<String> factions = FactionDecks.FACTIONS.stream().sorted().toList();
            FactionDecks starters = new FactionDecks(pool);
            for (String a : factions) for (String b : factions) for (int cohort = 0; cohort < 4; cohort++) {
                List<CardDefinition> da = cohort == 3 ? starters.starter(a) : pool.cardsForFaction(a);
                List<CardDefinition> db = cohort == 3 ? starters.starter(b) : pool.cardsForFaction(b);
                scenarios.add(new Scenario(230926L + scenarios.size(), a, b,
                        capitals.forFaction(a).get(cohort % 3).id(), capitals.forFaction(b).get(cohort % 3).id(),
                        da.stream().map(CardDefinition::id).toList(), db.stream().map(CardDefinition::id).toList()));
            }
            // Each ordered primary/ally combination is exercised with 32 cards from each faction.
            for (String a : factions) for (String b : factions) if (!a.equals(b)) {
                List<String> mixed = new ArrayList<>();
                mixed.addAll(pool.cardsForFaction(a).stream().limit(32).map(CardDefinition::id).toList());
                mixed.addAll(pool.cardsForFaction(b).stream().limit(32).map(CardDefinition::id).toList());
                scenarios.add(new Scenario(230926L + scenarios.size(), a, b, capitals.forFaction(a).get(0).id(),
                        capitals.forFaction(b).get(0).id(), mixed, starters.starter(b).stream().map(CardDefinition::id).toList()));
            }
            json.writeValue(manifest.toFile(), scenarios);
        }
        BalanceSimulator simulator = new BalanceSimulator();
        BalanceSimulator.Accumulator totals = new BalanceSimulator.Accumulator(1, 230926L);
        int count = 0;
        for (Scenario s : scenarios) {
            totals.add(simulator.play(s.seed, s.first, s.second, capitals.require(s.firstCapital), capitals.require(s.secondCapital),
                    s.firstDeck.stream().map(pool::require).toList(), s.secondDeck.stream().map(pool::require).toList()));
            if (++count % 12 == 0) System.out.println("Hex balance matches " + count + "/" + scenarios.size());
        }
        simulator.write(totals.report(), out.resolve("matches.json"));
    }
    public record Scenario(long seed, String first, String second, String firstCapital, String secondCapital,
                           List<String> firstDeck, List<String> secondDeck) { }
}
