package com.infiniteconquest.gui;

import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Upright odd-row hex layout; perspective never changes authoritative coordinates. */
final class HexBoardPanel extends JPanel {
    // Painted once and reused: fonts, colors and paints are otherwise rebuilt
    // on every repaint (including the ~8fps selection-pulse repaints).
    private static final Font TITLE_FONT = new Font("Palatino Linotype", Font.BOLD, 28);
    private static final Font TAGLINE_FONT = new Font("Georgia", Font.ITALIC, 16);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font INSPECT_TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font INSPECT_BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Color STORM_TOP = new Color(28, 57, 77);
    private static final Color TABLE_TOP = new Color(42, 40, 38);
    private static final Color GRADIENT_BOTTOM = new Color(8, 14, 23);
    private static final Color DIAGONAL = new Color(205, 222, 235, 18);
    private static final Color OPPONENT_LABEL = new Color(227, 180, 182);
    private static final Color YOU_LABEL = new Color(125, 217, 231);
    private static final Color TITLE_TEXT = new Color(232, 216, 176);
    private static final Color TAGLINE_TEXT = new Color(209, 222, 230);
    private static final Color RULE_GOLD = new Color(210, 174, 100, 170);
    /** Dark wash baked over the background art once, instead of per repaint. */
    private static final Color BG_TINT = new Color(7, 18, 30, 205);
    /** Panel-sized background (scaled art + tint baked in); rebuilt on resize/card change. */
    private BufferedImage bgCache;
    private int bgCacheW = -1, bgCacheH = -1;
    private CardDefinition bgCacheCard;
    private GradientPaint bgGradient;
    private int bgGradientW = -1, bgGradientH = -1;
    private boolean bgGradientStorm;
    private BoardPerspective perspective=new BoardPerspective(0);
    void setLocalPlayer(int player){perspective=new BoardPerspective(player);revalidate();repaint();}
    private double hexHeight(){return Math.max(1,Math.min((getHeight()-22)/4.75,(getWidth()-24)/(4.5*1.2)));}
    private boolean storm = true;
    private boolean showContext = true;
    void setShowContext(boolean value){showContext=value;repaint();}
    private CardDefinition backgroundCard;
    private CardDefinition inspectedCard;
    void setBackgroundCard(CardDefinition card){if(backgroundCard!=card){backgroundCard=card;bgCache=null;}repaint();}
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
        int width=getWidth(),height=getHeight();
        if(bgGradient==null||bgGradientW!=width||bgGradientH!=height||bgGradientStorm!=storm){
            bgGradient=new GradientPaint(0,0,storm?STORM_TOP:TABLE_TOP,width,height,GRADIENT_BOTTOM);
            bgGradientW=width;bgGradientH=height;bgGradientStorm=storm;
        }
        g.setPaint(bgGradient);
        g.fillRect(0,0,width,height);
        if(storm&&backgroundCard!=null){
            if(bgCache==null||bgCacheW!=width||bgCacheH!=height||bgCacheCard!=backgroundCard){
                bgCache=renderBackground(width,height,backgroundCard);
                bgCacheW=width;bgCacheH=height;bgCacheCard=backgroundCard;
            }
            g.drawImage(bgCache,0,0,null);
        }
        g.setColor(DIAGONAL);
        for(int x=-height;x<width;x+=65)g.drawLine(x,0,x+height,height);
        g.setColor(OPPONENT_LABEL);g.drawString("OPPONENT",18,24);
        g.setColor(YOU_LABEL);g.drawString("YOU · PLAYER 1 VIEW",18,height-12);
        double h=hexHeight();
        int margin=(int)((width-h*1.2*4.5)/2);
        if(showContext&&margin>230){
            g.setFont(TITLE_FONT);g.setColor(TITLE_TEXT);g.drawString(storm?"Stormfront":"Obsidian Table",30,85);
            g.setFont(TAGLINE_FONT);g.setColor(TAGLINE_TEXT);
            g.setColor(RULE_GOLD);g.drawLine(30,98,Math.min(margin-30,225),98);
            g.setColor(TAGLINE_TEXT);g.drawString("Claim the heights.",30,127);g.drawString("Command the field.",30,151);
            g.setFont(BODY_FONT);drawWrapped(g,"H = stack height. Structures block lower sightlines; units above them can see farther over cover.",30,192,margin-58,19);
            int x=width-margin+24,inspectWidth=margin-48;
            if(inspectedCard!=null){
                g.drawImage(CardArtFactory.iconFor(inspectedCard,200,100).getImage(),x,45,Math.min(200,inspectWidth),90,null);
                g.setFont(INSPECT_TITLE_FONT);
                drawWrapped(g,inspectedCard.name(),x,158,inspectWidth,20);
                g.setFont(INSPECT_BODY_FONT);
                drawWrapped(g,inspectedCard.faction()+" · "+inspectedCard.type(),x,202,inspectWidth,19);
                drawWrapped(g,"Right-click to inspect the full card and stack.",x,240,inspectWidth,20);
            }else{drawWrapped(g,"Select a card or hex to inspect it. Legal destinations light up on the board.",x,90,inspectWidth,24);}
        }
        g.dispose();
    }
    /**
     * Pre-renders the panel-sized background once: the 1200x600 art scaled to
     * the panel with the dark wash baked in. Per repaint this used to scale the
     * art image and refill the tint every time.
     */
    private static BufferedImage renderBackground(int width,int height,CardDefinition card){
        BufferedImage image=new BufferedImage(Math.max(1,width),Math.max(1,height),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(CardArtFactory.iconFor(card,1200,600).getImage(),0,0,width,height,null);
        g.setColor(BG_TINT);g.fillRect(0,0,width,height);
        g.dispose();
        return image;
    }
    private void drawWrapped(Graphics2D g,String text,int x,int y,int width,int lineHeight){
        FontMetrics metrics=g.getFontMetrics();
        String line="";for(String word:text.split(" ")){if(!line.isEmpty()&&metrics.stringWidth(line+" "+word)>width){g.drawString(line,x,y);y+=lineHeight;line="";}line+=(line.isEmpty()?"":" ")+word;}g.drawString(line,x,y);
    }
}
