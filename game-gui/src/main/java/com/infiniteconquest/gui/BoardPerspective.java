package com.infiniteconquest.gui;
import com.infiniteconquest.core.BoardPosition;
import java.awt.geom.Point2D;

record BoardPerspective(int localPlayer) {
    BoardPerspective { if(localPlayer<0 || localPlayer>1)throw new IllegalArgumentException("Unknown seat"); }
    Point2D.Double center(BoardPosition p) {
        double x=p.x()+.5*(p.y()%2)+.5;
        double y=(5-p.y())*.75+.5;
        return localPlayer==0 ? new Point2D.Double(x,y) : new Point2D.Double(4.5-x,4.75-y);
    }
    String label(int owner){return owner==localPlayer?"YOU · PLAYER 1 VIEW":"OPPONENT";}
}
