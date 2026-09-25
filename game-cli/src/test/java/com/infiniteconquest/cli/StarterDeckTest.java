package com.infiniteconquest.cli;
import com.infiniteconquest.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
class StarterDeckTest {
    @TempDir Path folder;
    @Test void redesignedCatalogStaysUniqueParsesAndStartersValidate(){
        // PrototypeCardPool loads every catalog through CardCatalog (throws on
        // parse failure) and rejects duplicate ids across all catalogs.
        var pool=new PrototypeCardPool();
        assertNotNull(pool.require("zeus_ion_storm_lattice"));
        assertNotNull(pool.require("poseidon_moonwell_tidegate"));
        assertNotNull(pool.require("poseidon_drowned_archive"));
        var decks=new FactionDecks(pool);
        for(String f:FactionDecks.FACTIONS){
            var starter=decks.starter(f);
            assertEquals(60,starter.size(),f);
            assertTrue(new DeckValidator().validate(starter).isEmpty(),
                    "Starter fails DeckValidator for "+f+": "+new DeckValidator().validate(starter));
        }
    }
    @Test void startersHaveReliableCurvesAndRepeatableFactionPlans(){
        var pool=new PrototypeCardPool();var decks=new FactionDecks(pool);
        for(String f:FactionDecks.FACTIONS){var cards=decks.starter(f);
            assertEquals(cards,decks.starter(f.toLowerCase(Locale.ROOT)));
            assertEquals(20,cards.stream().filter(c->c.type()==CardType.CHARACTER).count(),f);
            assertEquals(8,cards.stream().filter(c->c.type()==CardType.SPELL).count(),f);
            assertTrue(cards.stream().filter(c->c.type()==CardType.LAND&&c.cost()<=1&&c.goldCost()==0).count()>=8,f);
            assertTrue(cards.stream().filter(c->c.type()==CardType.LAND&&c.cost()<=2&&c.goldCost()==0).count()>=12,f);
            assertTrue(cards.stream().filter(c->c.type()==CardType.STRUCTURE&&c.cost()<=3&&c.goldCost()==0).count()>=6,f);
            assertTrue(cards.stream().filter(c->c.type()==CardType.CHARACTER&&c.cost()<=3).count()>=9,f);
            assertTrue(cards.stream().allMatch(c->c.cost()<=6),f);
            assertTrue(cards.stream().collect(Collectors.groupingBy(CardDefinition::id,Collectors.counting())).values().stream().allMatch(n->n>=2&&n<=4),f);
        }
    }
    @Test void exactOldStarterIsBackedUpOnceAndUpgradedWithoutChangingCapital()throws Exception{
        var pool=new PrototypeCardPool();var roster=new CapitalRoster();var store=new DeckBuildStore(pool,roster);
        var old=new ObjectMapper().readTree(getClass().getResourceAsStream("/cards/previous-faction-starters.json"));
        for(String f:FactionDecks.FACTIONS){
            var cards=new ArrayList<CardDefinition>();for(var id:old.get(f))cards.add(pool.require(id.asText()));Collections.reverse(cards);
            var build=new DeckBuild("My starter",f,null,roster.forFaction(f).get(2),cards);
            Path file=folder.resolve(f+".json");store.save(file,build);byte[] original=Files.readAllBytes(file);
            var updated=StarterDeckMigration.upgrade(file,build,pool,store);
            assertEquals(new FactionDecks(pool).starter(f),updated.cards());assertEquals(build.capital(),updated.capital());assertEquals(build.name(),updated.name());
            Path backup=folder.resolve(f+".json.pre-starter-0.4.2.bak");assertArrayEquals(original,Files.readAllBytes(backup));
            assertEquals(updated,StarterDeckMigration.upgrade(file,updated,pool,store));assertArrayEquals(original,Files.readAllBytes(backup));
            assertEquals(updated.cards().stream().map(CardDefinition::id).sorted().toList(),store.load(file).cards().stream().map(CardDefinition::id).sorted().toList());
        }
    }
    @Test void customizedOrAlliedDecksAreNotReset()throws Exception{
        var pool=new PrototypeCardPool();var roster=new CapitalRoster();var store=new DeckBuildStore(pool,roster);
        var old=new ObjectMapper().readTree(getClass().getResourceAsStream("/cards/previous-faction-starters.json")).get("ZEUS");
        var cards=new ArrayList<CardDefinition>();for(var id:old)cards.add(pool.require(id.asText()));
        var allied=new DeckBuild("Allied","ZEUS","POSEIDON",roster.forFaction("ZEUS").get(0),cards);
        Path a=folder.resolve("ally.json");store.save(a,allied);byte[] data=Files.readAllBytes(a);
        assertSame(allied,StarterDeckMigration.upgrade(a,allied,pool,store));assertArrayEquals(data,Files.readAllBytes(a));
        cards.remove(0);var edited=new DeckBuild("Edited","ZEUS",null,allied.capital(),cards);
        Path b=folder.resolve("edited.json");store.save(b,edited);data=Files.readAllBytes(b);
        assertSame(edited,StarterDeckMigration.upgrade(b,edited,pool,store));assertArrayEquals(data,Files.readAllBytes(b));
        try(var files=Files.list(folder)){assertEquals(2,files.count());}
    }
}
