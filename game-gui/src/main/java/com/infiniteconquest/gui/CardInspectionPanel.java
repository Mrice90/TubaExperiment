package com.infiniteconquest.gui;
import com.infiniteconquest.core.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

final class CardInspectionPanel extends JPanel {
    static final Color BACKGROUND = new Color(28,40,53);
    CardInspectionPanel(GameState state, CardInstance card) {
        super(new BorderLayout(12,12));setBackground(BACKGROUND);setBorder(new EmptyBorder(12,12,12,12));
        CardDefinition def=card.definition();
        JLabel art=new JLabel(CardArtFactory.iconFor(def,240,160));art.setVerticalAlignment(SwingConstants.TOP);
        add(art,BorderLayout.WEST);
        JEditorPane rules=new JEditorPane("text/html",details(state,card));rules.setEditable(false);
        rules.setBackground(BACKGROUND);rules.setForeground(new Color(237,240,241));
        rules.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true);rules.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        rules.setCaretPosition(0);
        JScrollPane scroll=new JScrollPane(rules);scroll.getViewport().setBackground(BACKGROUND);scroll.setBackground(BACKGROUND);
        scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll,BorderLayout.CENTER);setPreferredSize(new Dimension(800,520));
    }
    static String details(GameState state,CardInstance card){
        CardDefinition d=card.definition();String live="";
        if(d.type()==CardType.CHARACTER)live="Current Attack "+new GameEngine().effectiveAttack(state,card)+" · Defense remaining "+card.defenseRemaining()+" / "+card.effectiveDefense()+" · Effective range "+new GameEngine().effectiveRange(state,card)+" · Movement spent "+card.movementSpent()+" / "+d.movement()+" · "+(card.attackedThisTurn()?"Attack already used":"Attack not used");
        else if(d.isPermanent())live="Current HP "+Math.max(0,d.hitPoints()-card.damage())+" / "+d.hitPoints();
        return CardRulesText.details(d).replace("</h2>","</h2><p><b>"+live+"</b></p><h3>Printed card and rules</h3>");
    }
}
