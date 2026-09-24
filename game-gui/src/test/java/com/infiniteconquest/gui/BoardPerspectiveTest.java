package com.infiniteconquest.gui;
import com.infiniteconquest.core.BoardPosition;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;
class BoardPerspectiveTest {
    @Test void eitherSeatSeesItsTerritoryBelowTheOpponentWithoutChangingCoordinates(){
        for(int seat=0;seat<2;seat++){
            var p=new BoardPerspective(seat);
            assertTrue(p.center(new BoardPosition(1,seat==0?0:5)).y>p.center(new BoardPosition(1,seat==0?5:0)).y);
            assertEquals("YOU · PLAYER 1 VIEW",p.label(seat));assertEquals("OPPONENT",p.label(1-seat));
            var panel=new HexBoardPanel();panel.setLocalPlayer(seat);panel.setSize(900,500);
            for(int y=0;y<6;y++)for(int x=0;x<4;x++){var b=new JButton();b.putClientProperty("position",new BoardPosition(x,y));panel.add(b);}
            panel.doLayout();
            for(Component b:panel.getComponents()){
                assertTrue(new Rectangle(0,0,900,500).contains(b.getBounds()));
                assertSame(b,panel.getComponentAt(b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2));
                var logical=(BoardPosition)((JComponent)b).getClientProperty("position");
                assertEquals(logical.isOnPlayerSide(seat),b.getY()+b.getHeight()/2>250);
            }
        }
    }
    @Test void oppositeViewsAreAnExactHalfTurn(){
        for(int y=0;y<6;y++)for(int x=0;x<4;x++){
            var p=new BoardPosition(x,y);var a=new BoardPerspective(0).center(p);var b=new BoardPerspective(1).center(p);
            assertEquals(4.5,a.x+b.x,.0001);assertEquals(4.75,a.y+b.y,.0001);
        }
        assertThrows(IllegalArgumentException.class,()->new BoardPerspective(2));
    }
}
