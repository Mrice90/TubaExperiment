package com.infiniteconquest.gui;

import com.infiniteconquest.cli.*;
import com.infiniteconquest.core.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

/** Playable deck workflow shared by new and imported builds. */
final class DeckBuilderDialog extends JDialog {
    private final PrototypeCardPool pool;
    private final CapitalRoster roster;
    private final DeckBuildStore store;
    private final List<CardDefinition> cards = new ArrayList<>();
    private final JComboBox<String> faction = new JComboBox<>(new String[]{"ZEUS","POSEIDON"});
    private final JComboBox<String> ally = new JComboBox<>();
    private final JPanel stage = new JPanel(new BorderLayout(12,12));
    private final JLabel heading = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton back = new JButton("Back"), next = new JButton("Continue");
    private CardDefinition capital;
    private int step;
    private DeckBuild result;
    private final DefaultListModel<CardDefinition> availableModel = new DefaultListModel<>();
    private final DefaultListModel<CardDefinition> deckModel = new DefaultListModel<>();
    private final JList<CardDefinition> available = new JList<>(availableModel), deck = new JList<>(deckModel);
    private final JEditorPane inspection = new JEditorPane("text/html", "");

    DeckBuilderDialog(Frame owner, PrototypeCardPool pool, CapitalRoster roster, DeckBuild initial) {
        super(owner, "Forge your deck", true);
        this.pool=pool; this.roster=roster; this.store=new DeckBuildStore(pool,roster);
        faction.setSelectedItem(initial.primaryFaction());refreshAllies(initial.allyFaction());
        capital=initial.capital();cards.addAll(initial.cards());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(14,14));root.setBorder(new EmptyBorder(18,18,18,18));
        heading.setFont(new Font(Font.SERIF,Font.BOLD,25));root.add(heading,BorderLayout.NORTH);root.add(stage);
        JPanel footer=new JPanel(new BorderLayout(12,12));
        JPanel navigation=new JPanel(new FlowLayout(FlowLayout.RIGHT));navigation.add(back);navigation.add(next);
        JButton cancel=new JButton("Cancel");cancel.addActionListener(e->dispose());navigation.add(cancel);
        JPanel sharing=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton export=new JButton("Share deck"),importDeck=new JButton("Import deck code");sharing.add(export);sharing.add(importDeck);
        footer.add(sharing,BorderLayout.WEST);footer.add(status,BorderLayout.NORTH);footer.add(navigation,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
        setContentPane(root);
        faction.addActionListener(e->{refreshAllies(null);capital=roster.forFaction(primary()).get(0);});
        back.addActionListener(e->{step--;render();});
        next.addActionListener(e->{
            if(step==2&&!confirmRemoval())return;
            if(step<3){step++;render();return;}
            try{result=build();dispose();}catch(IllegalArgumentException ex){error(ex);}
        });
        export.addActionListener(e->{try{showCode(store.exportCode(build()));}catch(IllegalArgumentException ex){error(ex);}});
        importDeck.addActionListener(e->importDeck());
        inspection.setEditable(false);inspection.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true);
        inspection.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        ListCellRenderer<CardDefinition> renderer=(list,card,index,selected,focus)->{
            long count=cards.stream().filter(c->c.id().equals(card.id())).count();
            JLabel label=new JLabel("<html><b>"+escape(card.name())+"</b><br>"+card.faction()+" · "+card.type()+" · "+cost(card)+"<br>In deck: "+count+" / 4</html>",CardArtFactory.iconFor(card,80,58),SwingConstants.LEFT);
            label.setBorder(new EmptyBorder(8,8,8,8));label.setOpaque(true);label.setBackground(selected?new Color(65,81,93):new Color(28,40,53));label.setForeground(Color.WHITE);return label;
        };
        available.setCellRenderer(renderer);deck.setCellRenderer(renderer);
        available.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);deck.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        available.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&available.getSelectedValue()!=null)inspect(available.getSelectedValue());});
        deck.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&deck.getSelectedValue()!=null)inspect(deck.getSelectedValue());});
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1180,screen.width-40),Math.min(740,screen.height-60));setLocationRelativeTo(owner);render();
    }
    DeckBuild choose(){setVisible(true);return result;}
    void captureForReview(int stageNumber, java.nio.file.Path path) {
        try {
            step=stageNumber;render();setModal(false);setVisible(true);validate();
            java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(getRootPane().getWidth(),getRootPane().getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=image.createGraphics();getRootPane().printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",path.toFile());
        }catch(java.io.IOException e){throw new IllegalStateException(e);}finally{dispose();}
    }
    private String primary(){return (String)faction.getSelectedItem();}
    private String ally(){return ally.getSelectedIndex()<=0?null:(String)ally.getSelectedItem();}
    private void refreshAllies(String selected){ally.removeAllItems();ally.addItem("No ally");for(String f:new TreeSet<>(DeckBuild.FACTIONS))if(!f.equals(primary()))ally.addItem(f);if(selected!=null)ally.setSelectedItem(selected);}
    private DeckBuild build(){return new DeckBuild(primary()+" Custom",primary(),ally(),capital,cards);}
    private boolean confirmRemoval(){
        List<CardDefinition> removed=cards.stream().filter(c->!DeckBuild.eligible(c,primary(),ally())).toList();
        if(removed.isEmpty())return true;
        JTextArea list=new JTextArea("These cards no longer match your faction and ally:\n\n"+summary(removed)+"\n\nRemove them from this draft? Saved decks are unchanged until you save.",14,50);list.setEditable(false);
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(list),"Review faction change",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return false;
        cards.removeAll(removed);return true;
    }
    private void render(){
        stage.removeAll();back.setEnabled(step>0);next.setText(step==3?"Save deck":"Continue");
        heading.setText((step+1)+" / 4 — "+new String[]{"Choose your faction","Choose one ally, or stand alone","Choose your Capital","Build your deck"}[step]);
        if(step<2){JPanel pick=new JPanel();pick.setLayout(new BoxLayout(pick,BoxLayout.Y_AXIS));pick.add(new JLabel(step==0?"Your primary faction determines your Capital.":"An optional ally adds its cards to your deck. You still have one Capital."));pick.add(Box.createVerticalStrut(24));JComboBox<String> box=step==0?faction:ally;box.setMaximumSize(new Dimension(450,48));box.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,20));pick.add(box);stage.add(pick,BorderLayout.NORTH);}
        else if(step==2){
            JPanel choices=new JPanel(new GridLayout(1,3,18,0));
            for(CardDefinition cap:roster.forFaction(primary())){
                JPanel choice=new JPanel(new BorderLayout(0,12));choice.setBorder(new EmptyBorder(12,12,12,12));
                choice.add(new JLabel(CardArtFactory.iconFor(cap,240,105)),BorderLayout.NORTH);
                JPanel copy=new JPanel(new BorderLayout(0,12));
                JLabel name=new JLabel("<html><b>"+escape(cap.name())+"</b><br><span style='font-size:12pt'>20 HP · +1 GP/turn</span></html>");name.setFont(new Font(Font.SERIF,Font.PLAIN,21));copy.add(name,BorderLayout.NORTH);
                JTextArea passive=new JTextArea(new CapitalPassiveRules().description(cap));passive.setLineWrap(true);passive.setWrapStyleWord(true);passive.setEditable(false);passive.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,17));passive.setMargin(new Insets(14,14,14,14));copy.add(new JScrollPane(passive));choice.add(copy);
                JButton choose=new JButton(cap.id().equals(capital.id())?"✓ Selected Capital":"Choose this Capital");choose.addActionListener(e->{capital=cap;render();});choice.add(choose,BorderLayout.SOUTH);choices.add(choice);
            }stage.add(choices);
        }else{
            availableModel.clear();pool.cards().stream().filter(c->DeckBuild.eligible(c,primary(),ally())).sorted(Comparator.comparing(CardDefinition::type).thenComparing(CardDefinition::name)).forEach(availableModel::addElement);
            JPanel lists=new JPanel(new GridLayout(1,2,12,0));lists.add(listPanel("Available cards",available));lists.add(listPanel("Your deck",deck));
            JPanel collection=new JPanel(new BorderLayout(8,8));collection.add(lists);
            JTextField search=new JTextField();search.setToolTipText("Search name, faction, type, keyword or archetype");
            JPanel searchPanel=new JPanel(new BorderLayout(8,0));searchPanel.add(new JLabel("Search cards"),BorderLayout.WEST);searchPanel.add(search);collection.add(searchPanel,BorderLayout.NORTH);
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                private void filter(){String query=search.getText().toLowerCase(Locale.ROOT);availableModel.clear();pool.cards().stream().filter(c->DeckBuild.eligible(c,primary(),ally())).filter(c->(c.name()+" "+c.faction()+" "+c.type()+" "+c.keywords()+" "+c.archetypes()).toLowerCase(Locale.ROOT).contains(query)).sorted(Comparator.comparing(CardDefinition::type).thenComparing(CardDefinition::name)).forEach(availableModel::addElement);}
                public void insertUpdate(javax.swing.event.DocumentEvent e){filter();}public void removeUpdate(javax.swing.event.DocumentEvent e){filter();}public void changedUpdate(javax.swing.event.DocumentEvent e){filter();}
            });
            JPanel buttons=new JPanel();JButton add=new JButton("Add copy →"),remove=new JButton("Remove copy"),starter=new JButton("Reset faction starter");buttons.add(add);buttons.add(remove);buttons.add(starter);collection.add(buttons,BorderLayout.SOUTH);
            add.addActionListener(e->{CardDefinition c=available.getSelectedValue();if(c!=null&&cards.stream().filter(d->d.id().equals(c.id())).count()<4){cards.add(c);refreshCards();}});
            remove.addActionListener(e->{CardDefinition c=deck.getSelectedValue();if(c!=null){cards.remove(c);refreshCards();}});
            starter.addActionListener(e->{if(JOptionPane.showConfirmDialog(this,"Reset this draft to the new 60-card faction starter, with no ally and the default Capital? Your saved deck changes only when you save.","Replace draft",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){refreshAllies(null);capital=roster.forFaction(primary()).get(0);cards.clear();cards.addAll(new FactionDecks(pool).starter(primary()));render();}});
            JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,collection,new JScrollPane(inspection));split.setResizeWeight(.64);split.setDividerLocation((int)(getWidth()*.60));stage.add(split);refreshCards();if(!availableModel.isEmpty())available.setSelectedIndex(0);
        }
        updateStatus();style(getContentPane());heading.setFont(new Font(Font.SERIF,Font.BOLD,25));stage.revalidate();stage.repaint();
    }
    private JPanel listPanel(String name,JList<?> list){JPanel p=new JPanel(new BorderLayout(0,8));p.add(new JLabel(name),BorderLayout.NORTH);p.add(new JScrollPane(list));return p;}
    private void refreshCards(){CardDefinition selected=deck.getSelectedValue();deckModel.clear();cards.stream().distinct().sorted(Comparator.comparing(CardDefinition::name)).forEach(deckModel::addElement);if(selected!=null)deck.setSelectedValue(selected,true);available.repaint();updateStatus();}
    private void updateStatus(){List<String> errors=DeckBuild.errors(primary(),ally(),capital,cards);status.setText("<html>"+primary()+(ally()==null?" · No ally":" + "+ally())+" · "+cards.size()+" cards · "+cards.stream().map(CardDefinition::id).distinct().count()+" distinct"+(step==3&&!errors.isEmpty()?"<br>"+escape(String.join("; ",errors)):"")+"</html>");next.setEnabled(step<3||errors.isEmpty());}
    private void inspect(CardDefinition c){inspection.setText(details(c));inspection.setCaretPosition(0);}
    static String details(CardDefinition c){ return CardRulesText.details(c); }
    private static String cost(CardDefinition c){return c.type()==CardType.LAND||c.type()==CardType.STRUCTURE?"Turn "+Math.max(1,c.cost())+" · "+(c.developmentGoldCost()==0?"free":c.developmentGoldCost()+" Gold"):c.cost()+" GP";}
    private void showCode(String code){JTextArea text=new JTextArea(code,8,55);text.setLineWrap(true);text.setWrapStyleWord(false);text.setEditable(false);JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JScrollPane(text));JButton copy=new JButton("Copy deck code");copy.addActionListener(e->{try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code),null);copy.setText("Copied");}catch(IllegalStateException ex){text.selectAll();text.requestFocusInWindow();}});panel.add(copy,BorderLayout.SOUTH);JOptionPane.showMessageDialog(this,panel,"Share this complete deck code",JOptionPane.PLAIN_MESSAGE);}
    private void importDeck(){JTextArea input=new JTextArea(8,55);input.setLineWrap(true);if(JOptionPane.showConfirmDialog(this,new JScrollPane(input),"Paste deck code",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;try{
        DeckBuild imported=store.importCode(input.getText());JTextArea preview=new JTextArea(imported.primaryFaction()+" + "+Objects.toString(imported.allyFaction(),"No ally")+"\n"+imported.capital().name()+"\n\n"+summary(imported.cards())+"\nReplace the current draft?",18,50);preview.setEditable(false);
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(preview),"Review imported deck",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        faction.setSelectedItem(imported.primaryFaction());refreshAllies(imported.allyFaction());capital=imported.capital();cards.clear();cards.addAll(imported.cards());step=3;render();
    }catch(IllegalArgumentException ex){error(ex);}}
    private static String summary(List<CardDefinition> cards){Map<String,Integer> counts=new TreeMap<>();cards.forEach(c->counts.merge(c.name(),1,Integer::sum));StringBuilder s=new StringBuilder();counts.forEach((name,n)->s.append(n).append(" × ").append(name).append('\n'));return s.toString();}
    private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Deck needs attention",JOptionPane.WARNING_MESSAGE);}
    private void style(Component component){
        if(component instanceof JPanel||component instanceof JViewport){component.setBackground(new Color(22,32,44));component.setForeground(new Color(236,237,229));}
        if(component instanceof JLabel){component.setForeground(new Color(236,237,229));}
        if(component instanceof JTextArea){component.setBackground(new Color(29,43,56));component.setForeground(new Color(239,242,239));}
        if(component instanceof JButton button){button.setBackground(new Color(44,67,82));button.setForeground(Color.WHITE);button.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));button.setPreferredSize(new Dimension(Math.max(100,button.getPreferredSize().width),44));}
        if(component instanceof JScrollPane scroll){scroll.setBorder(BorderFactory.createLineBorder(new Color(68,84,99)));}
        if(component instanceof Container container)for(Component child:container.getComponents())style(child);
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
