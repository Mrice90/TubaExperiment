package com.infiniteconquest.gui;

import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import javax.swing.*;
import java.awt.*;

/** Upright odd-row hex layout; perspective never changes authoritative coordinates. */
final class HexBoardPanel extends JPanel {
    private BoardPerspective perspective=new BoardPerspective(0);
    void setLocalPlayer(int player){perspective=new BoardPerspective(player);revalidate();repaint();}
    private double hexHeight(){return Math.max(1,Math.min((getHeight()-22)/4.75,(getWidth()-24)/(4.5*1.2)));}
    private boolean storm = true;
    private boolean showContext = true;
    void setShowContext(boolean value){showContext=value;repaint();}
    private CardDefinition backgroundCard;
    private CardDefinition inspectedCard;
    void setBackgroundCard(CardDefinition card){backgroundCard=card;repaint();}
    void inspect(CardDefinition card){inspectedCard=card;repaint();}
    HexBoardPanel(){super(null);setOpaque(false);}
    void toggleBackground(){storm=!storm;repaint();}
    @Override public void doLayout(){
        double h=hexHeight(),w=h*1.2;
        double originX=(getWidth()-w*4.5)/2,originY=(getHeight()-h*4.75)/2;
        for(Component component:getComponents()){
            BoardPosition p=(BoardPosition)((JComponent)component).getClientProperty("position");
            if(p!=null){((JComponent)component).putClientProperty("viewer",perspective.localPlayer());
                if(perspective.localPlayer()==1)component.setBackground(p.isOnPlayerSide(1)?UiTheme.HUMAN_PLOT:UiTheme.BOT_PLOT);
                var center=perspective.center(p);component.setBounds((int)(originX+(center.x-.5)*w),(int)(originY+(center.y-.5)*h),(int)w-3,(int)h-3);}
        }
    }
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0,0,storm?new Color(28,57,77):new Color(42,40,38),getWidth(),getHeight(),new Color(8,14,23)));
        g.fillRect(0,0,getWidth(),getHeight());
        if(storm&&backgroundCard!=null){
            g.drawImage(CardArtFactory.iconFor(backgroundCard,1200,600).getImage(),0,0,getWidth(),getHeight(),null);
            g.setColor(new Color(7,18,30,205));g.fillRect(0,0,getWidth(),getHeight());
        }
        g.setColor(new Color(205,222,235,18));
        for(int x=-getHeight();x<getWidth();x+=65)g.drawLine(x,0,x+getHeight(),getHeight());
        g.setColor(new Color(227,180,182));g.drawString("OPPONENT",18,24);
        g.setColor(new Color(125,217,231));g.drawString("YOU · PLAYER 1 VIEW",18,getHeight()-12);
        double h=hexHeight();
        int margin=(int)((getWidth()-h*1.2*4.5)/2);
        if(showContext&&margin>230){
            g.setFont(new Font("Palatino Linotype",Font.BOLD,28));g.setColor(new Color(232,216,176));g.drawString(storm?"Stormfront":"Obsidian Table",30,85);
            g.setFont(new Font("Georgia",Font.ITALIC,16));g.setColor(new Color(209,222,230));
            g.setColor(new Color(210,174,100,170));g.drawLine(30,98,Math.min(margin-30,225),98);
            g.setColor(new Color(209,222,230));g.drawString("Claim the heights.",30,127);g.drawString("Command the field.",30,151);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));drawWrapped(g,"H = stack height. Structures block lower sightlines; units above them can see farther over cover.",30,192,margin-58,19);
            int x=getWidth()-margin+24,width=margin-48;
            if(inspectedCard!=null){
                g.drawImage(CardArtFactory.iconFor(inspectedCard,200,100).getImage(),x,45,Math.min(200,width),90,null);
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15));
                drawWrapped(g,inspectedCard.name(),x,158,width,20);
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
                drawWrapped(g,inspectedCard.faction()+" · "+inspectedCard.type(),x,202,width,19);
                drawWrapped(g,"Right-click to inspect the full card and stack.",x,240,width,20);
            }else{drawWrapped(g,"Select a card or hex to inspect it. Legal destinations light up on the board.",x,90,width,24);}
        }
        g.dispose();
    }
    private void drawWrapped(Graphics2D g,String text,int x,int y,int width,int lineHeight){
        String line="";for(String word:text.split(" ")){if(!line.isEmpty()&&g.getFontMetrics().stringWidth(line+" "+word)>width){g.drawString(line,x,y);y+=lineHeight;line="";}line+=(line.isEmpty()?"":" ")+word;}g.drawString(line,x,y);
    }
}
