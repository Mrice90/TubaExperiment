package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DeckEditorCli {
    private final PrototypeCardPool pool;
    private final DeckFileStore files;
    private final FactionDecks factionDecks;

    public DeckEditorCli(PrototypeCardPool pool, DeckFileStore files) {
        this.pool = pool;
        this.files = files;
        this.factionDecks = new FactionDecks(pool);
    }

    public void run(BufferedReader input, PrintStream output, List<CardDefinition> startingDeck) throws IOException {
        DeckEditor editor = new DeckEditor(pool, startingDeck);
        output.println("Infinite Conquest deck editor");
        output.println("Starting from the 60-card demo deck. Type help.");

        while (true) {
            output.print("deck> ");
            String line = input.readLine();
            if (line == null) return;
            String[] parts = line.trim().split("\\s+");
            try {
                switch (parts[0].toLowerCase()) {
                    case "help" -> output.println(help());
                    case "factions" -> output.println(String.join(", ", FactionDecks.FACTIONS));
                    case "pool" -> {
                        if (parts.length > 2) throw new IllegalArgumentException("Use pool or pool <faction>");
                        output.println(renderPool(parts.length == 2 ? parts[1] : null));
                    }
                    case "deck" -> output.println(renderDeck(editor));
                    case "reset" -> {
                        require(parts, 2);
                        editor.reset(factionDecks.starter(parts[1]));
                        String faction = parts[1].toUpperCase(java.util.Locale.ROOT);
                        editor.identity(faction, null, new CapitalRoster().forFaction(faction).get(0));
                        output.println("Loaded the " + parts[1].toUpperCase() + " 60-card starter deck.");
                    }
                    case "add" -> { require(parts, 2); editor.add(parts[1]); output.println("Added " + parts[1]); }
                    case "remove" -> { require(parts, 2); editor.remove(parts[1]); output.println("Removed " + parts[1]); }
                    case "swap" -> {
                        require(parts, 3); editor.swap(parts[1], parts[2]);
                        output.println("Swapped " + parts[1] + " for " + parts[2]);
                    }
                    case "save" -> {
                        require(parts, 2);
                        if (editor.hasIdentity()) new DeckBuildStore(pool,new CapitalRoster()).save(Path.of(parts[1]),editor.build());
                        else files.save(Path.of(parts[1]), "Custom Deck", editor.cards());
                        output.println("Saved valid deck (40-card minimum) to " + parts[1]);
                    }
                    case "validate" -> {
                        List<String> errors = editor.validationErrors();
                        output.println(errors.isEmpty() ? "Deck is valid." : String.join(System.lineSeparator(), errors));
                    }
                    case "identity" -> {
                        if(parts.length<2||parts.length>3)throw new IllegalArgumentException("Use identity <primary> [ally]");
                        String primary=parts[1].toUpperCase(java.util.Locale.ROOT),ally=parts.length==3?parts[2].toUpperCase(java.util.Locale.ROOT):null;
                        if(!com.infiniteconquest.core.DeckBuild.FACTIONS.contains(primary))throw new IllegalArgumentException("Unknown primary faction");
                        editor.identity(primary,ally,new CapitalRoster().forFaction(primary).get(0));
                    }
                    case "capital" -> {require(parts,2);var build=editor.build();editor.identity(build.primaryFaction(),build.allyFaction(),new CapitalRoster().require(parts[1]));}
                    case "share" -> output.println(new DeckBuildStore(pool,new CapitalRoster()).exportCode(editor.build()));
                    case "import" -> {require(parts,2);var build=new DeckBuildStore(pool,new CapitalRoster()).importCode(parts[1]);editor.reset(build.cards());editor.identity(build.primaryFaction(),build.allyFaction(),build.capital());output.println("Imported "+build.cards().size()+" cards.");}
                    case "quit", "exit" -> { return; }
                    case "" -> { }
                    default -> output.println("Unknown command. Type help.");
                }
            } catch (IllegalArgumentException exception) {
                output.println("Cannot complete command: " + exception.getMessage());
            }
        }
    }

    String renderPool(String faction) {
        List<CardDefinition> visible = faction == null ? pool.cards() : pool.cardsForFaction(faction);
        if (visible.isEmpty()) throw new IllegalArgumentException("No cards found for faction: " + faction);
        StringBuilder out = new StringBuilder();
        for (CardDefinition card : visible) {
            out.append(card.id()).append(" | ").append(card.name()).append(" | ")
                    .append(card.faction()).append(" | ").append(card.type())
                    .append(" | ").append(card.type() == CardType.LAND || card.type() == CardType.STRUCTURE
                            ? "Turn " + Math.max(1, card.cost()) + " ("+(card.developmentGoldCost()==0?"free":card.developmentGoldCost()+" Gold")+"), +" + card.income() + " GP/turn"
                            : card.cost() + " GP");
            if (!card.keywords().isEmpty()) out.append(" | ").append(card.keywords());
            out.append(System.lineSeparator());
        }
        return out.toString().stripTrailing();
    }

    String renderDeck(DeckEditor editor) {
        StringBuilder out = new StringBuilder("Cards: " + editor.cards().size() + " (40 minimum; starters have 60)");
        for (Map.Entry<String, Long> entry : editor.counts().entrySet()) {
            out.append(System.lineSeparator()).append(entry.getValue()).append("x ")
                    .append(pool.require(entry.getKey()).name()).append(" [")
                    .append(entry.getKey()).append(']');
        }
        return out.toString();
    }

    private void require(String[] parts, int length) {
        if (parts.length != length) throw new IllegalArgumentException("Wrong number of arguments");
    }

    private String help() {
        return """
                factions                     list the six launch factions
                identity <primary> [ally]    choose one primary and optional ally
                capital <id>                 choose a primary-faction Capital
                share                        print a complete ICD1 deck code
                import <code>                replace the draft with a validated build
                pool [faction]               list all cards or one faction's cards
                reset <faction>              load that faction's 60-card starter
                deck                         show the current deck and copy counts
                swap <remove-id> <add-id>    replace one card while staying at 40
                remove <card-id>             remove one copy
                add <card-id>                add one copy (maximum four)
                validate                     check 40-card minimum and copy-limit rules
                save <file.json>             save only if the deck is valid
                quit                         leave the editor
                """.strip();
    }
}
