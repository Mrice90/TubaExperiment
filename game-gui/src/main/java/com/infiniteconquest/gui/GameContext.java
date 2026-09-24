package com.infiniteconquest.gui;

import com.infiniteconquest.cli.CapitalRoster;
import com.infiniteconquest.cli.DeckBuildStore;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.cli.PrototypeCardPool;
import com.infiniteconquest.cli.StarterDeckMigration;
import com.infiniteconquest.core.CapitalPassiveRules;
import com.infiniteconquest.core.DeckBuild;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Everything expensive the game needs, loaded once on the loading screen and shared
 * by the shell and the match window. Loading it once keeps Play and the deck builder
 * instant after startup.
 */
final class GameContext {
    final DemoMatchFactory matchFactory;
    final PrototypeCardPool pool;
    final CapitalRoster capitals;
    final FactionDecks factionDecks;
    final DeckBuildStore buildStore;
    final CapitalPassiveRules passiveRules;
    final Path deckDirectory;
    final Map<String, DeckBuild> savedDecks;
    final GameSettings settings;

    private GameContext(DemoMatchFactory matchFactory, GameSettings settings,
                        Path deckDirectory, Map<String, DeckBuild> savedDecks) {
        this.matchFactory = matchFactory;
        this.pool = matchFactory.pool();
        this.capitals = matchFactory.capitals();
        this.factionDecks = new FactionDecks(pool);
        this.buildStore = new DeckBuildStore(pool, capitals);
        this.passiveRules = new CapitalPassiveRules();
        this.deckDirectory = deckDirectory;
        this.savedDecks = savedDecks;
        this.settings = settings;
    }

    DeckBuild buildForFaction(String faction) {
        DeckBuild saved = savedDecks.get(faction);
        if (saved != null) return saved;
        return new DeckBuild(faction + " Starter", faction, null,
                capitals.forFaction(faction).get(0), factionDecks.starter(faction));
    }

    /**
     * Loads the context, reporting human-readable progress. Runs off the EDT;
     * callers must not touch Swing components from the progress callback.
     */
    static GameContext load(GameSettings settings, Consumer<String> progress) throws Exception {
        progress.accept("Reading the card catalog");
        DemoMatchFactory matchFactory = new DemoMatchFactory();
        // Touch the pool so every JSON file parses now, not on first click.
        int cards = matchFactory.pool().cards().size();
        progress.accept("Preparing " + cards + " cards for battle");
        Path deckDirectory = Path.of(System.getProperty("user.home"), ".infinite-conquest", "decks");
        Files.createDirectories(deckDirectory);
        progress.accept("Warming the war-room");
        FactionDecks factionDecks = new FactionDecks(matchFactory.pool());
        DeckBuildStore buildStore = new DeckBuildStore(matchFactory.pool(), matchFactory.capitals());
        progress.accept("Filing your saved decks");
        StarterDeckMigration.archiveRetiredFactionDecks(deckDirectory);
        Map<String, DeckBuild> savedDecks = new HashMap<>();
        for (String faction : FactionDecks.FACTIONS) {
            Path file = deckDirectory.resolve(faction.toLowerCase(Locale.ROOT) + ".json");
            if (!Files.exists(file)) continue;
            try {
                DeckBuild build = buildStore.load(file);
                if (!build.primaryFaction().equals(faction))
                    throw new IllegalArgumentException("Deck primary faction does not match its saved slot");
                build = StarterDeckMigration.upgrade(file, build, matchFactory.pool(), buildStore);
                savedDecks.put(faction, build);
            } catch (RuntimeException ignored) {
                // A corrupt deck never blocks startup; the deck builder can replace it.
            }
        }
        progress.accept("Tuning the war-horns");
        SoundEffects.setMuted(!settings.soundEnabled);
        SoundEffects.setVolume(settings.volume / 100.0);
        // Decode a few key paintings now so the title screen never hitches.
        CardArtFactory.warmCache();
        return new GameContext(matchFactory, settings, deckDirectory, savedDecks);
    }
}
