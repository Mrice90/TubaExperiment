package com.infiniteconquest.cli;
import com.infiniteconquest.core.*;
import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TerrainCatalogTest {
    @Test void keywordsExistOnPlayableCardsWithSixOnEachDevelopmentType() {
        var pool=new PrototypeCardPool();
        assertEquals(6,TerrainRules.landKeywords().size());assertEquals(6,TerrainRules.structureKeywords().size());
        for(var keyword:TerrainRules.landKeywords()){
            // COVER, FERTILE and ARCHIVE retired with the cut factions; they return via DLC
            if(keyword==Keyword.COVER||keyword==Keyword.FERTILE||keyword==Keyword.ARCHIVE) continue;
            assertTrue(pool.cards().stream().anyMatch(c->c.type()==CardType.LAND && c.hasKeyword(keyword)),keyword.name());
        }
        for(var keyword:TerrainRules.structureKeywords()){
            // WORKSHOP and BEACON retired with the cut factions; they return via DLC
            if(keyword==Keyword.WORKSHOP||keyword==Keyword.BEACON) continue;
            assertTrue(pool.cards().stream().anyMatch(c->c.type()==CardType.STRUCTURE && c.hasKeyword(keyword)),keyword.name());
        }
        for (String faction : FactionDecks.FACTIONS) {
            var cards = pool.cardsForFaction(faction);
            for (CardType type : List.of(CardType.LAND, CardType.STRUCTURE)) {
                assertTrue(cards.stream().anyMatch(c -> c.type()==type && c.cost()<=2 && c.goldCost()==0), faction+" free opening "+type);
                assertTrue(cards.stream().anyMatch(c -> c.type()==type && c.goldCost()>0), faction+" paid utility "+type);
            }
            assertTrue(cards.stream().filter(c -> c.cost()<=2 && c.goldCost()==0 && c.type()==CardType.LAND).count()>=2, faction);
        }
        assertTrue(pool.cards().stream().filter(c->c.developmentGoldCost()>0).allMatch(c->c.goldCost()<=3));
        assertEquals(0,pool.require("zeus_olympian_cloudbank").goldCost());
        assertEquals(3,pool.require("zeus_apex_worldstorm_spire").developmentGoldCost());
        assertEquals(8,pool.require("zeus_apex_worldstorm_spire").cost());
        assertEquals(2,pool.require("zeus_apex_worldstorm_spire").keywordValue(Keyword.TURRET).range());
    }
    @Test void archetypesAreExplicitAndSearchableWithoutBecomingCombatKeywords() {
        var pool=new PrototypeCardPool();
        assertTrue(pool.require("zeus_cloudline_courier").archetypes().contains("HUMAN"));
        assertTrue(pool.require("poseidon_kraken_tendril_drone").archetypes().contains("AUTOMATON"));
        assertTrue(pool.require("poseidon_triton_waveguard").archetypes().contains("MERFOLK"));
        assertTrue(pool.require("zeus_chain_lightning").archetypes().contains("STORM"));
        assertTrue(pool.require("zeus_cloudwall_bastion").archetypes().contains("FORTRESS"));
        assertTrue(pool.require("zeus_eagles_perch_array").archetypes().contains("HIGHLAND"));
        assertTrue(pool.cards().stream().anyMatch(c->c.archetypes().isEmpty()));
    }
    @Test void botsAndHintsRespectTheGoldSurcharge() {
        var factory=new DemoMatchFactory();var s=factory.create(42L);
        while(s.phase()==Phase.PLAY && s.personalTurnNumber(s.activePlayer())<3)new GameEngine().apply(s,new GameAction.EndTurn(s.activePlayer()));
        int owner=s.activePlayer();var card=new CardInstance(UUID.randomUUID(),factory.pool().require("zeus_eagles_perch_array"),owner,Zone.HAND);s.register(card);s.player(owner).addToHand(card.instanceId());
        s.player(owner).spendGp(s.player(owner).currentGp());int index=s.player(owner).hand().indexOf(card.instanceId());
        assertTrue(new ActionHints().forActivePlayer(s,new GameEngine()).stream().noneMatch(c->c.startsWith("play "+index+" ")));
        s.player(owner).restoreGp(1);
        assertTrue(new ActionHints().forActivePlayer(s,new GameEngine()).stream().anyMatch(c->c.startsWith("play "+index+" ")));
    }
}
