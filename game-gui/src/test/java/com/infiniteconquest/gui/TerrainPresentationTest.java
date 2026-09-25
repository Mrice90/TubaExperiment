package com.infiniteconquest.gui;
import com.infiniteconquest.cli.*;
import com.infiniteconquest.core.*;
import com.infiniteconquest.data.Keyword;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TerrainPresentationTest {
    @Test void lethalTurretStopsTheVisualMoveAtTheEntryHexAndMarksDestructionThere() {
        GameState state=new DemoMatchFactory().create(424242L);
        int owner=state.activePlayer();
        for(BoardPosition p:state.board().positions())while(!state.board().isEmpty(p)){
            CardInstance old=state.card(state.board().pop(p)).orElseThrow();old.moveTo(Zone.DISCARD);
        }
        var turret=new CardDefinition("turret","Turret",CardType.STRUCTURE,"NEUTRAL",0,0,0,0,0,10,Set.of(Keyword.TURRET),List.of(),0,DevelopmentPassive.NONE,List.of(),Map.of(Keyword.TURRET,new KeywordValue(1,10)),Set.of(),0);
        var source=new CardInstance(UUID.randomUUID(),turret,1-owner,Zone.BATTLEFIELD);state.register(source);state.board().push(new BoardPosition(0,0),source.instanceId());
        var mover=new CardInstance(UUID.randomUUID(),new CardDefinition("mover","Mover",CardType.CHARACTER,"NEUTRAL",0,1,5,6,1),owner,Zone.BATTLEFIELD);state.register(mover);state.board().push(new BoardPosition(3,0),mover.instanceId());
        var before=PresentationSnapshot.capture(state);
        assertTrue(new GameEngine().apply(state,new GameAction.MoveCharacter(owner,mover.instanceId(),new BoardPosition(0,1))).accepted());
        assertEquals(Zone.DISCARD,mover.zone());
        var presentation=PresentationSnapshot.between("move 3 0 0 1",before,state);
        var event=state.events().stream().filter(e->e.type()==GameEvent.Type.TERRAIN_TRIGGERED).findFirst().orElseThrow();
        String[] xy=event.detail().split(" ")[4].split(",");var hit=new BoardPosition(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));
        assertEquals(hit,presentation.destructionPosition(mover.instanceId()));assertEquals(hit,presentation.movementDestination());
    }
}
