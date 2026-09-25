package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainRulesTest {
    private final GameEngine engine=new GameEngine();
    private GameState state(){return new GameState(42,MatchRules.hex(),true);}
    private CardDefinition definition(CardType type,Keyword keyword,int range,int amount,int fee) {
        return new CardDefinition("test_card","Test",type,"NEUTRAL",0,type==CardType.CHARACTER?3:0,
                type==CardType.CHARACTER?8:0,type==CardType.CHARACTER?4:0,type==CardType.CHARACTER?4:0,
                type==CardType.CHARACTER?0:12,keyword==null?Set.of():Set.of(keyword),List.of(),
                type==CardType.LAND||type==CardType.STRUCTURE?1:0,DevelopmentPassive.NONE,List.of(),
                keyword==null || (!TerrainRules.landKeywords().contains(keyword) && !TerrainRules.structureKeywords().contains(keyword))?Map.of():Map.of(keyword,new KeywordValue(range,amount)),Set.of(),fee);
    }
    private CardInstance put(GameState s,BoardPosition p,CardType type,int owner,Keyword k,int range,int amount) {
        CardInstance c=new CardInstance(UUID.randomUUID(),definition(type,k,range,amount,0),owner,Zone.BATTLEFIELD);
        s.register(c);s.board().push(p,c.instanceId());return c;
    }
    private CardInstance put(GameState s,BoardPosition p,CardType type,int owner){return put(s,p,type,owner,null,0,1);}
    private CardInstance hand(GameState s,CardDefinition def,int owner){var c=new CardInstance(UUID.randomUUID(),def,owner,Zone.HAND);s.register(c);s.player(owner).addToHand(c.instanceId());return c;}
    @Test void normalLandIsTransparentButCoveredBuildingsStillBlock() {
        var s=state();var a=new BoardPosition(0,2);var b=new BoardPosition(3,2);var middle=new BoardPosition(1,2);
        put(s,a,CardType.CHARACTER,0);put(s,b,CardType.CHARACTER,1);put(s,middle,CardType.LAND,0);
        var sight=new LineOfSightRules();assertTrue(sight.hasLineOfSight(s,a,b));
        put(s,middle,CardType.STRUCTURE,0);put(s,middle,CardType.CHARACTER,0);
        assertFalse(sight.hasLineOfSight(s,a,b));
    }
    @Test void elevatedCharacterSeesOverLowerBuildingButRangeDoesNotGrow() {
        var s=state();var a=new BoardPosition(0,2);var b=new BoardPosition(3,2);var middle=new BoardPosition(1,2);
        put(s,a,CardType.STRUCTURE,0);var archer=put(s,a,CardType.CHARACTER,0);put(s,b,CardType.CHARACTER,1);put(s,middle,CardType.STRUCTURE,1);
        var sight=new LineOfSightRules();assertEquals(1,TerrainRules.height(s,a));assertEquals(2,TerrainRules.eyeLevel(s,a));
        assertTrue(sight.hasLineOfSight(s,a,b));assertTrue(sight.hasLineOfSight(s,b,a));assertEquals(4,engine.effectiveRange(s,archer));
        put(s,middle,CardType.STRUCTURE,1);assertFalse(sight.hasLineOfSight(s,a,b));
    }
    @Test void highGroundAddsHeightWithoutBlockingAndOrdinaryCharactersAddNone() {
        var s=state();var p=new BoardPosition(1,2);put(s,p,CardType.LAND,0,Keyword.HIGH_GROUND,0,1);
        assertEquals(1,TerrainRules.height(s,p));assertEquals(0,TerrainRules.obstacleHeight(s,p));
        put(s,p,CardType.STRUCTURE,0);put(s,p,CardType.CHARACTER,0);put(s,p,CardType.CHARACTER,0);
        assertEquals(2,TerrainRules.height(s,p));assertEquals(3,TerrainRules.eyeLevel(s,p));assertEquals(2,TerrainRules.obstacleHeight(s,p));
    }
    @Test void paidDevelopmentRequiresGoldAndRejectedPlayIsAtomic() {
        var s=state();var c=hand(s,definition(CardType.LAND,Keyword.HIGH_GROUND,0,1,2),0);var destination=new BoardPosition(0,0);
        put(s,new BoardPosition(1,0),CardType.CAPITAL,0);
        assertFalse(engine.apply(s,new GameAction.PlayLand(0,c.instanceId(),destination)).accepted());
        assertEquals(Zone.HAND,c.zone());assertTrue(s.board().isEmpty(destination));assertTrue(s.canPlayDevelopment(0,CardType.LAND));
        s.player(0).restoreGp(3);assertTrue(engine.apply(s,new GameAction.PlayLand(0,c.instanceId(),destination)).accepted());assertEquals(1,s.player(0).currentGp());
    }
    @Test void paidStructureKeepsItsFoundationAndChargesOnlyAfterValidation() {
        var s=state();var c=hand(s,definition(CardType.STRUCTURE,Keyword.TURRET,2,1,2),0);var p=new BoardPosition(0,0);s.player(0).restoreGp(2);
        assertFalse(engine.apply(s,new GameAction.PlayStructure(0,c.instanceId(),p)).accepted());assertEquals(2,s.player(0).currentGp());
        var land=put(s,p,CardType.LAND,0);assertTrue(engine.apply(s,new GameAction.PlayStructure(0,c.instanceId(),p)).accepted());
        assertEquals(0,s.player(0).currentGp());assertEquals(List.of(land.instanceId(),c.instanceId()),s.board().stackAt(p));
    }
    @Test void turretHitsOnBoundaryEntryNotEachStepOrRepeatedEntryAndDoesNotHitFriendlies() {
        var s=state();var source=new BoardPosition(0,2);put(s,source,CardType.STRUCTURE,1,Keyword.TURRET,1,2);
        var p=new BoardPosition(1,2);var c=put(s,p,CardType.CHARACTER,0);
        TerrainRules.entered(s,c,new BoardPosition(3,2),p,false);assertEquals(2,c.combatDamage());
        TerrainRules.entered(s,c,source,p,false);assertEquals(2,c.combatDamage());
        TerrainRules.entered(s,c,new BoardPosition(3,2),p,false);assertEquals(2,c.combatDamage());
        var ally=put(s,new BoardPosition(0,1),CardType.CHARACTER,1);TerrainRules.entered(s,ally,null,new BoardPosition(0,1),true);assertEquals(0,ally.combatDamage());
    }
    @Test void turretNeedsSightAndRangeAndCanDestroyAnEnteringCharacter() {
        var s=state();var source=new BoardPosition(0,2);put(s,source,CardType.STRUCTURE,1,Keyword.TURRET,3,8);
        var blocker=put(s,new BoardPosition(1,2),CardType.STRUCTURE,1);var p=new BoardPosition(3,2);var c=put(s,p,CardType.CHARACTER,0);
        TerrainRules.entered(s,c,null,p,true);assertEquals(0,c.combatDamage());
        s.board().remove(blocker.instanceId());blocker.moveTo(Zone.DISCARD);
        TerrainRules.entered(s,c,null,p,true);assertEquals(Zone.DISCARD,c.zone());assertTrue(s.board().isEmpty(p));
    }
    @Test void medicHealsOnlyMarkedFriendlyDamageAndCannotOverhealOrLoop() {
        var s=state();var source=new BoardPosition(0,2);put(s,source,CardType.STRUCTURE,0,Keyword.MEDIC_TENT,1,3);
        var p=new BoardPosition(1,2);var c=put(s,p,CardType.CHARACTER,0);c.addCombatDamage(2);
        TerrainRules.entered(s,c,new BoardPosition(3,2),p,false);assertEquals(0,c.combatDamage());
        c.addCombatDamage(4);TerrainRules.entered(s,c,new BoardPosition(3,2),p,false);assertEquals(4,c.combatDamage());
        var enemy=put(s,new BoardPosition(0,1),CardType.CHARACTER,1);enemy.addCombatDamage(2);TerrainRules.entered(s,enemy,null,new BoardPosition(0,1),true);assertEquals(2,enemy.combatDamage());
    }
    @Test void actualMovementSummoningBlinkAndTeleportUseEntryTriggers() {
        var s=state();var tower=new BoardPosition(0,2);put(s,tower,CardType.STRUCTURE,1,Keyword.TURRET,1,2);
        var mover=put(s,new BoardPosition(2,2),CardType.CHARACTER,0);
        assertTrue(engine.apply(s,new GameAction.MoveCharacter(0,mover.instanceId(),new BoardPosition(1,2))).accepted());assertEquals(2,mover.combatDamage());
        var blink=put(s,new BoardPosition(3,4),CardType.CHARACTER,0,Keyword.BLINK,0,1);
        assertTrue(engine.apply(s,new GameAction.BlinkCharacter(0,blink.instanceId(),new BoardPosition(0,1))).accepted());assertEquals(2,blink.combatDamage());
        put(s,new BoardPosition(1,3),CardType.LAND,0);var summon=hand(s,definition(CardType.CHARACTER,null,0,1,0),0);
        assertTrue(engine.apply(s,new GameAction.SummonCharacter(0,summon.instanceId(),new BoardPosition(0,3))).accepted());assertEquals(2,summon.combatDamage());
        s.board().moveTop(new BoardPosition(0,1),new BoardPosition(3,0),blink.instanceId());
        var spell=new CardDefinition("teleport","Teleport",CardType.SPELL,"NEUTRAL",0,0,0,0,0,0,Set.of(),List.of(new SpellEffect(SpellEffectType.TELEPORT_CHARACTER,1,SpellTarget.FRIENDLY)));
        var spellCard=hand(s,spell,0);var traveler=put(s,new BoardPosition(3,5),CardType.CHARACTER,0);
        assertTrue(engine.apply(s,new GameAction.CastSpell(0,spellCard.instanceId(),traveler.instanceId(),new BoardPosition(0,2))).accepted()==false); // occupied destinations remain illegal
        assertTrue(engine.apply(s,new GameAction.CastSpell(0,spellCard.instanceId(),traveler.instanceId(),new BoardPosition(0,1))).accepted());assertEquals(2,traveler.combatDamage());
    }
    @Test void waystationRestoresMovementOnceAndWatchtowerAddsRange() {
        var s=state();var p=new BoardPosition(1,2);put(s,p,CardType.LAND,0,Keyword.WAYSTATION,0,1);var c=put(s,new BoardPosition(2,2),CardType.CHARACTER,0);
        assertTrue(engine.apply(s,new GameAction.MoveCharacter(0,c.instanceId(),p)).accepted());assertEquals(0,c.movementSpent());
        assertTrue(engine.apply(s,new GameAction.MoveCharacter(0,c.instanceId(),new BoardPosition(2,2))).accepted());
        assertTrue(engine.apply(s,new GameAction.MoveCharacter(0,c.instanceId(),p)).accepted());assertEquals(2,c.movementSpent());
        var watch=new BoardPosition(3,2);put(s,watch,CardType.STRUCTURE,0,Keyword.WATCHTOWER,0,1);var archer=put(s,watch,CardType.CHARACTER,0);assertEquals(5,engine.effectiveRange(s,archer));
    }
    @Test void coverOnlyReducesRangedDamageAndProtectionDoesNotStack() {
        var s=state();var p=new BoardPosition(1,2);put(s,p,CardType.LAND,0,Keyword.COVER,0,1);var c=put(s,p,CardType.CHARACTER,0);
        assertEquals(3,TerrainRules.reduceDamage(s,c,3,false));assertEquals(2,TerrainRules.reduceDamage(s,c,3,true));
        put(s,p,CardType.STRUCTURE,0,Keyword.BULWARK,0,2);
        assertEquals(1,TerrainRules.reduceDamage(s,c,3,true));assertEquals(1,TerrainRules.reduceDamage(s,c,3,false));assertEquals(0,TerrainRules.reduceDamage(s,c,1,true));
    }
    @Test void fertileSanctuaryAndWorkshopHaveDistinctEconomyAndRepairEffects() {
        var s=state();var p=new BoardPosition(1,2);var land=put(s,p,CardType.LAND,0,Keyword.FERTILE,0,1);assertEquals(2,s.gpIncomePerTurn(0));
        var sanctuary=put(s,new BoardPosition(2,2),CardType.LAND,0,Keyword.SANCTUARY,0,1);sanctuary.addDamage(3);
        var repair=put(s,new BoardPosition(3,2),CardType.STRUCTURE,0,Keyword.WORKSHOP,1,2);repair.addDamage(5);
        var other=put(s,new BoardPosition(3,3),CardType.STRUCTURE,1);other.addDamage(6);
        TerrainRules.startTurn(s,0);assertEquals(2,sanctuary.damage());assertEquals(3,repair.damage());assertEquals(6,other.damage());assertEquals(0,land.damage());
    }
    @Test void archiveDrawsOnDestructionAndBeaconOnlyBuffsSummons() {
        var s=state();var archive=put(s,new BoardPosition(0,0),CardType.LAND,0,Keyword.ARCHIVE,0,1);
        put(s,new BoardPosition(0,1),CardType.CAPITAL,0);var drawn=new CardInstance(UUID.randomUUID(),definition(CardType.CHARACTER,null,0,1,0),0,Zone.DECK);s.register(drawn);s.player(0).loadDeck(List.of(drawn.instanceId()));
        s.destroy(archive);assertTrue(s.player(0).hand().contains(drawn.instanceId()));
        var p=new BoardPosition(2,2);put(s,p,CardType.STRUCTURE,0,Keyword.BEACON,1,1);var c=put(s,new BoardPosition(3,2),CardType.CHARACTER,0);
        TerrainRules.entered(s,c,null,new BoardPosition(3,2),true);assertEquals(4,c.effectiveAttack());
        var moved=put(s,new BoardPosition(2,3),CardType.CHARACTER,0);TerrainRules.entered(s,moved,new BoardPosition(0,5),new BoardPosition(2,3),false);assertEquals(3,moved.effectiveAttack());
    }
    @Test void triggerAllowanceResetsOnTurnChange() {
        var s=state();put(s,new BoardPosition(0,2),CardType.STRUCTURE,1,Keyword.TURRET,1,1);
        var p=new BoardPosition(1,2);var c=put(s,p,CardType.CHARACTER,0);
        TerrainRules.entered(s,c,null,p,true);assertEquals(1,c.combatDamage());
        engine.apply(s,new GameAction.EndTurn(0));assertEquals(0,c.combatDamage());
        TerrainRules.entered(s,c,new BoardPosition(3,2),p,false);assertEquals(1,c.combatDamage());
    }
    @Test void keywordMetadataRejectsWrongTypesAndInvalidValues() {
        assertThrows(IllegalArgumentException.class,()->definition(CardType.CHARACTER,Keyword.TURRET,2,1,0));
        assertThrows(IllegalArgumentException.class,()->definition(CardType.STRUCTURE,Keyword.HIGH_GROUND,0,1,0));
        assertThrows(IllegalArgumentException.class,()->definition(CardType.LAND,Keyword.FERTILE,2,1,0));
        assertThrows(IllegalArgumentException.class,()->new KeywordValue(-1,1));
        assertThrows(IllegalArgumentException.class,()->new KeywordValue(1,0));
    }
}
