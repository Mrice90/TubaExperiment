package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CapitalPassiveRules;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Phase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

public final class InfiniteConquestCli {
    private InfiniteConquestCli() {}

    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        DemoMatchFactory matches = new DemoMatchFactory();

        if (args.length > 0 && args[0].equalsIgnoreCase("simulate")) {
            if (args.length > 4) throw new IllegalArgumentException("Use: simulate [matches-per-capital-pair] [seed] [report.json]");
            int repetitions = args.length >= 2 ? parsePositiveInt(args[1]) : BalanceSimulator.DEFAULT_MATCHES_PER_CAPITAL_PAIR;
            long simulationSeed = args.length >= 3 ? parseSeed(args[2]) : 1L;
            Path output = Path.of(args.length >= 4 ? args[3] : "balance-report.json");
            BalanceSimulator simulator = new BalanceSimulator();
            BalanceReport report = simulator.simulate(repetitions, simulationSeed);
            simulator.write(report, output);
            System.out.println("Simulated " + report.totalMatches() + " matches across every faction and Capital pairing.");
            System.out.println("Completed: " + report.completedMatches() + " | Draws: " + report.draws()
                    + " | Average turns: " + report.averageTurns());
            System.out.println("Balance flags: " + report.balanceFlags().size());
            System.out.println("Report: " + output.toAbsolutePath());
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("deck")) {
            new DeckEditorCli(matches.pool(), new DeckFileStore())
                    .run(input, System.out, matches.demoDeck());
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("capitals")) {
            System.out.println("Selectable Capitals (choose one matching your faction):");
            CapitalPassiveRules passives = new CapitalPassiveRules();
            for (String faction : new java.util.TreeSet<>(FactionDecks.FACTIONS)) {
                System.out.println(faction + ":");
                matches.capitals().forFaction(faction).forEach(capital ->
                        System.out.println("  " + capital.id() + " — " + capital.name()
                                + " — HP " + capital.hitPoints() + " — " + passives.description(capital)));
            }
            return;
        }

        long seed;
        List<CardDefinition> humanDeck;
        List<CardDefinition> botDeck;
        CardDefinition humanCapital = null;
        CardDefinition botCapital = null;
        if (args.length > 0 && args[0].equalsIgnoreCase("play")) {
            if (args.length < 3 || args.length > 6) {
                throw new IllegalArgumentException("Use: play <human-deck.json> <bot-deck.json> [seed] [human-capital-id] [bot-capital-id]");
            }
            DeckBuildStore files = new DeckBuildStore(matches.pool(), matches.capitals());
            com.infiniteconquest.core.DeckBuild humanBuild = files.load(Path.of(args[1]));
            com.infiniteconquest.core.DeckBuild botBuild = files.load(Path.of(args[2]));
            seed = args.length >= 4 ? parseSeed(args[3]) : 1L;
            if (args.length >= 5) humanBuild = new com.infiniteconquest.core.DeckBuild(humanBuild.name(), humanBuild.primaryFaction(), humanBuild.allyFaction(), matches.capitals().require(args[4]), humanBuild.cards());
            if (args.length >= 6) botBuild = new com.infiniteconquest.core.DeckBuild(botBuild.name(), botBuild.primaryFaction(), botBuild.allyFaction(), matches.capitals().require(args[5]), botBuild.cards());
            runMatch(matches.create(seed, humanBuild, botBuild, new com.infiniteconquest.core.BoardPosition(1,0),
                    new com.infiniteconquest.core.BoardPosition(2,5), com.infiniteconquest.core.BoardGeometry.HEX), seed, input);
            return;
        } else {
            seed = args.length == 0 ? 1L : parseSeed(args[0]);
            humanDeck = matches.demoDeck();
            botDeck = matches.demoDeck();
        }

        runMatch(matches.create(seed, humanDeck, botDeck, humanCapital, botCapital), seed, input);
    }

    private static void runMatch(GameState state, long seed, BufferedReader input) throws IOException {
        BattlefieldRenderer renderer = new BattlefieldRenderer();
        CommandProcessor commands = new CommandProcessor(state);
        ActionHints hints = new ActionHints();
        BotPlayer bot = new BotPlayer();

        System.out.println("Infinite Conquest — Player 1 vs Bot");
        System.out.println("You are Player 1. The bot is Player 2.");
        System.out.println("Seed: " + seed);
        System.out.println(CommandProcessor.help());

        while (!commands.quitRequested() && state.phase() != Phase.GAME_OVER) {
            if (state.activePlayer() == BotPlayer.BOT_ID) {
                BotPlayer.Decision decision = bot.takeNextAction(state, commands);
                System.out.println("Bot: " + decision.command() + " — " + decision.result());
                if (!decision.command().equals("end") && state.phase() != Phase.GAME_OVER) {
                    offerHumanReaction(state, input, renderer, commands, hints);
                }
                continue;
            }

            System.out.println();
            System.out.println(renderer.render(state));
            System.out.print("> ");
            String result = commands.execute(input.readLine());
            if (!result.isBlank()) System.out.println(result);
            if (result.startsWith("OK:") && state.activePlayer() == 0 && state.phase() != Phase.GAME_OVER) {
                BotPlayer.Decision reaction = bot.react(state, commands);
                if (reaction != null) {
                    System.out.println("Bot reaction: " + reaction.command() + " — " + reaction.result());
                }
            }
        }

        if (state.phase() == Phase.GAME_OVER) {
            System.out.println();
            System.out.println(renderer.render(state));
            System.out.println(state.winner().isEmpty() ? "The match is a draw."
                    : state.winner().getAsInt() == 0 ? "You win!" : "The bot wins.");
        }
    }

    private static void offerHumanReaction(GameState state, BufferedReader input,
                                           BattlefieldRenderer renderer, CommandProcessor commands,
                                           ActionHints hints) throws IOException {
        List<String> reactions = hints.spellActionsForPlayer(state, 0);
        if (reactions.isEmpty()) return;
        System.out.println();
        System.out.println("Reaction window — saved GP: " + state.player(0).currentGp());
        System.out.println(renderer.renderHand(state, 0));
        System.out.println("Legal reactions:");
        reactions.forEach(action -> System.out.println("  " + action));
        System.out.print("reaction> ");
        String response = input.readLine();
        if (response == null || response.isBlank() || response.equalsIgnoreCase("pass")) {
            System.out.println("Reaction passed.");
            return;
        }
        if (!response.startsWith("react 0 ")) {
            System.out.println("Reaction passed: use one listed 'react 0' command.");
            return;
        }
        System.out.println(commands.execute(response));
    }

    private static long parseSeed(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Optional seed must be a whole number", exception);
        }
    }

    private static int parsePositiveInt(String value) {
        try {
            int result = Integer.parseInt(value);
            if (result < 1) throw new IllegalArgumentException("Match repetitions must be positive");
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Match repetitions must be a whole number", exception);
        }
    }
}
