package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HexGeometryTest {
    private BoardPosition rotate(BoardPosition p){return new BoardPosition(3-p.x(),5-p.y());}
    private GameState hex(){return new GameState(1,MatchRules.hex(),true);}
    private CardInstance put(GameState state,BoardPosition p,CardType type,int owner,int range){
        CardDefinition def=new CardDefinition("fixture","Fixture",type,"NEUTRAL",0,3,10,1,range);
        CardInstance card=new CardInstance(UUID.randomUUID(),def,owner,Zone.BATTLEFIELD);state.register(card);state.board().push(p,card.instanceId());return card;
    }
    @Test void sixNeighborsAndRotationallySymmetricDistance(){
        List<BoardPosition> cells=new ArrayList<>(new BoardState().positions());
        assertEquals(6,BoardGeometry.HEX.neighbors(new BoardPosition(1,2)).size());
        for(BoardPosition a:cells)for(BoardPosition b:cells){
            assertEquals(BoardGeometry.HEX.distance(a,b),BoardGeometry.HEX.distance(b,a));
            assertEquals(BoardGeometry.HEX.distance(a,b),BoardGeometry.HEX.distance(rotate(a),rotate(b)));
        }
        assertEquals(2,BoardGeometry.HEX.distance(new BoardPosition(0,0),new BoardPosition(1,1)));
        assertEquals(1,BoardGeometry.SQUARE.distance(new BoardPosition(0,0),new BoardPosition(1,1)));
    }
    @Test void movementAndRangeUseHexGeometry(){
        GameState state=hex();BoardPosition from=new BoardPosition(1,2);
        CardInstance runner=put(state,from,CardType.CHARACTER,0,1);
        Set<BoardPosition> legal=new MovementRules().legalDestinations(state,runner);
        assertEquals(new HashSet<>(BoardGeometry.HEX.neighbors(from)),legal);
        BoardPosition diagonal=new BoardPosition(2,3);
        CardInstance enemy=put(state,diagonal,CardType.CHARACTER,1,1);
        assertFalse(new GameEngine().apply(state,new GameAction.Attack(0,runner.instanceId(),enemy.instanceId())).accepted());
        assertTrue(new MovementRules().shortestLegalPath(state,runner,new BoardPosition(1,3)).size()==1);
    }
    @Test void sightIsSymmetricAndRotationInvariantForEverySingleBlocker(){
        List<BoardPosition> cells=new ArrayList<>(new BoardState().positions());LineOfSightRules sight=new LineOfSightRules();
        for(BoardPosition blocker:cells){
            GameState normal=hex(),rotated=hex();put(normal,blocker,CardType.STRUCTURE,1,0);put(rotated,rotate(blocker),CardType.STRUCTURE,1,0);
            for(BoardPosition a:cells)for(BoardPosition b:cells){
                boolean visible=sight.hasLineOfSight(normal,a,b);
                assertEquals(visible,sight.hasLineOfSight(normal,b,a),"sight reversal");
                assertEquals(visible,sight.hasLineOfSight(rotated,rotate(a),rotate(b)),"sight rotation");
            }
        }
    }
    @Test void blockerStopsSightButEndpointsDoNot(){
        GameState state=hex();BoardPosition a=new BoardPosition(0,2),b=new BoardPosition(3,2);LineOfSightRules sight=new LineOfSightRules();
        put(state,a,CardType.CAPITAL,0,0);put(state,b,CardType.CAPITAL,1,0);assertTrue(sight.hasLineOfSight(state,a,b));
        put(state,new BoardPosition(1,2),CardType.STRUCTURE,1,0);assertFalse(sight.hasLineOfSight(state,a,b));
    }
    @Test void edgeAmbiguityAllowsEitherOpenTraceButTwoBlockersStopSight(){
        List<BoardPosition> cells=new ArrayList<>(new BoardState().positions());
        for(BoardPosition a:cells)for(BoardPosition b:cells){
            var left=new HashSet<>(BoardGeometry.HEX.hexTrace(a,b,0.000001));
            var right=new HashSet<>(BoardGeometry.HEX.hexTrace(a,b,-0.000001));
            var onlyLeft=new HashSet<>(left);onlyLeft.removeAll(right);
            var onlyRight=new HashSet<>(right);onlyRight.removeAll(left);
            if(onlyLeft.isEmpty()||onlyRight.isEmpty())continue;
            GameState state=hex();LineOfSightRules sight=new LineOfSightRules();
            put(state,onlyLeft.iterator().next(),CardType.STRUCTURE,0,0);assertTrue(sight.hasLineOfSight(state,a,b));
            put(state,onlyRight.iterator().next(),CardType.STRUCTURE,1,0);assertFalse(sight.hasLineOfSight(state,a,b));return;
        }
        fail("Expected a shared-edge ray fixture");
    }
}
