package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlliedDeckTest {
    @TempDir Path directory;
    final DemoMatchFactory factory=new DemoMatchFactory();
    final DeckBuildStore store=new DeckBuildStore(factory.pool(),factory.capitals());
    DeckBuild mixed(){
        List<CardDefinition> cards=new ArrayList<>();
        factory.pool().cardsForFaction("ZEUS").stream().limit(7).forEach(c->{for(int i=0;i<4;i++)cards.add(c);});
        factory.pool().cardsForFaction("POSEIDON").stream().limit(3).forEach(c->{for(int i=0;i<4;i++)cards.add(c);});
        return new DeckBuild("Allied", "ZEUS","POSEIDON",factory.capitals().forFaction("ZEUS").get(1),cards);
    }
    @Test void saveLoadAndCodeRetainIdentity(){
        DeckBuild original=mixed();Path file=directory.resolve("deck.json");store.save(file,original);
        DeckBuild loaded=store.load(file);assertEquals(original.primaryFaction(),loaded.primaryFaction());assertEquals(original.allyFaction(),loaded.allyFaction());assertEquals(original.capital(),loaded.capital());
        String code=store.exportCode(original);assertEquals(code,store.exportCode(store.importCode(code)));assertEquals(code,store.exportCode(loaded));
        assertEquals(code,store.exportCode(store.importCode(code.replace(".",".\n"))));
        assertThrows(IllegalArgumentException.class,()->store.importCode(code.substring(0,code.length()-1)+"x"));
        assertThrows(IllegalArgumentException.class,()->store.importCode(code.replace("ICD1","ICD2")));
    }
    @Test void importsActualBrowserPrototypeCode() throws Exception {
        String code=new String(getClass().getResourceAsStream("/prototype-icd1.txt").readAllBytes(),StandardCharsets.UTF_8).trim();
        DeckBuild deck=store.importCode(code);assertEquals("ZEUS",deck.primaryFaction());assertEquals("POSEIDON",deck.allyFaction());
        assertEquals("zeus_capital_keraunos_spire",deck.capital().id());assertEquals(40,deck.cards().size());
        assertEquals(code,store.exportCode(deck));
    }
    @Test void rejectsMalformedPayloadEvenWithCorrectChecksum(){
        String body=new String(Base64.getUrlDecoder().decode(store.exportCode(mixed()).split("\\.")[1]),StandardCharsets.UTF_8);
        for(String bad:List.of(body.replace("POSEIDON","ARES"),body.replace("zeus_capital_keraunos_spire","ares_capital_red_citadel"),body.replace(",4]",",5]"),body.replace(",4]",",4.5]"),"null","[1,2,3,4]")){
            String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(bad.getBytes(StandardCharsets.UTF_8));
            int hash=0x811c9dc5;for(byte b:encoded.getBytes(StandardCharsets.UTF_8))hash=(hash^(b&255))*0x01000193;
            String code="ICD1."+encoded+"."+String.format(Locale.ROOT,"%08x",hash);
            assertThrows(IllegalArgumentException.class,()->store.importCode(code));
        }
        assertThrows(IllegalArgumentException.class,()->store.importCode("x".repeat(64001)));
    }
    @Test void rejectsWrongCapitalThirdFactionAndMultipleAllies(){
        DeckBuild good=mixed();
        assertThrows(IllegalArgumentException.class,()->new DeckBuild("bad","ZEUS","ZEUS",good.capital(),good.cards()));
        assertThrows(IllegalArgumentException.class,()->new DeckBuild("bad","ZEUS","POSEIDON,ARES",good.capital(),good.cards()));
        assertThrows(IllegalArgumentException.class,()->new DeckBuild("bad","ZEUS","POSEIDON",factory.capitals().forFaction("POSEIDON").get(0),good.cards()));
        List<CardDefinition> bad=new ArrayList<>(good.cards());bad.add(factory.pool().cardsForFaction("DEMO").get(0));
        assertThrows(IllegalArgumentException.class,()->new DeckBuild("bad","ZEUS","POSEIDON",good.capital(),bad));
        bad.remove(bad.size()-1);bad.add(good.capital());
        assertThrows(IllegalArgumentException.class,()->new DeckBuild("bad","ZEUS","POSEIDON",good.capital(),bad));
    }
    @Test void migratesUnambiguousLegacyAndPreservesOriginal() throws Exception {
        Path file=directory.resolve("old.json");new DeckFileStore().save(file,"Old",new FactionDecks(factory.pool()).starter("ZEUS"));
        String original=Files.readString(file);DeckBuild migrated=store.load(file);assertNull(migrated.allyFaction());assertEquals("ZEUS",migrated.primaryFaction());
        store.save(file,migrated);assertEquals(original,Files.readString(directory.resolve("old.json.legacy-v1.bak")));
        new DeckFileStore().save(directory.resolve("mixed.json"),"Old Mixed",mixed().cards());
        assertThrows(IllegalArgumentException.class,()->store.load(directory.resolve("mixed.json")));
    }
    @Test void legalBotMatchesUseAlliedDeckOnHexBoard(){
        DeckBuild human=mixed();DeckBuild bot=new DeckBuild("Bot","POSEIDON",null,factory.capitals().forFaction("POSEIDON").get(0),new FactionDecks(factory.pool()).starter("POSEIDON"));
        for(int seed=1;seed<=6;seed++){
            GameState state=factory.create(seed,human,bot,new BoardPosition(1,0),new BoardPosition(2,5),BoardGeometry.HEX);
            assertEquals(BoardGeometry.HEX,state.rules().geometry());
            CommandProcessor commands=new CommandProcessor(state);BotPlayer ai=new BotPlayer();
            int actions=0;
            while(state.phase()!=Phase.GAME_OVER&&state.turnNumber()<30&&actions++<1200){
                BotPlayer.Decision decision=ai.takeNextAction(state,commands,state.activePlayer());
                assertTrue(decision.result().startsWith("OK:"),decision.command()+": "+decision.result());
            }
            assertTrue(state.phase()==Phase.GAME_OVER||state.turnNumber()>=30,"Bot made no progress");
        }
    }
}
