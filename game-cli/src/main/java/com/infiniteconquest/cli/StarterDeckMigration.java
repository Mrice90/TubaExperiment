package com.infiniteconquest.cli;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteconquest.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Upgrade only exact pre-0.4.2 single-faction starter copies; preserve edited decks. */
public final class StarterDeckMigration {
    /** Factions removed by the Zeus/Poseidon strip-down; their saved decks are archived, never loaded. */
    private static final Set<String> RETIRED_FACTIONS = Set.of("HADES", "ARES", "ATHENA", "HEPHAESTUS");

    /** Back up saved decks for retired factions so the originals survive while the loader ignores them. */
    public static void archiveRetiredFactionDecks(Path deckDirectory) {
        for (String faction : RETIRED_FACTIONS) {
            Path file = deckDirectory.resolve(faction.toLowerCase(Locale.ROOT) + ".json");
            if (!Files.isRegularFile(file)) continue;
            Path backup = file.resolveSibling(file.getFileName() + ".pre-strip-zeus-poseidon.bak");
            try {
                if (!Files.exists(backup)) Files.copy(file, backup);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("Could not back up retired faction deck", e);
            }
        }
    }

    public static DeckBuild upgrade(Path path, DeckBuild build, PrototypeCardPool pool, DeckBuildStore store) {
        if(build.allyFaction()!=null)return build;
        try(var input=StarterDeckMigration.class.getResourceAsStream("/cards/previous-faction-starters.json")) {
            var entries=new ObjectMapper().readTree(input).get(build.primaryFaction());
            Map<String,Long> previous=new HashMap<>();
            for(var entry:entries)previous.merge(entry.asText(),1L,Long::sum);
            var actual=build.cards().stream().collect(Collectors.groupingBy(CardDefinition::id,Collectors.counting()));
            if(!previous.equals(actual))return build;
            DeckBuild updated=new DeckBuild(build.name(),build.primaryFaction(),null,build.capital(),new FactionDecks(pool).starter(build.primaryFaction()));
            Path backup=path.resolveSibling(path.getFileName()+".pre-starter-0.4.2.bak");
            if(!Files.exists(backup))Files.copy(path,backup);
            store.save(path,updated);
            return updated;
        }catch(java.io.IOException e){throw new IllegalArgumentException("Could not back up and refresh starter deck",e);}
    }
}
