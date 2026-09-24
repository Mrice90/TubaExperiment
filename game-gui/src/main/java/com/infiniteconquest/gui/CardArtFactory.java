package com.infiniteconquest.gui;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

/** Painted card art with a deterministic procedural renderer for cards awaiting bespoke art. */
final class CardArtFactory {
    private static final Map<String, ImageIcon> CACHE = new HashMap<>();
    /** Keep a small working set of full-resolution paintings as the faction art library grows. */
    private static final Map<String, BufferedImage> PAINTED_ART = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 16;
        }
    };
    private static final BufferedImage WORLDS = loadWorlds();
    private static final Map<String,Integer> WORLD = Map.of(
            "ZEUS",0,"POSEIDON",1);

    private CardArtFactory() { }

    /**
     * Decodes the paintings the title screen needs so first paint never hitches.
     * Safe to call off the EDT; failures are ignored and retried lazily later.
     */
    static void warmCache() {
        for (String id : new String[]{
                "zeus_capital_olympus_citadel", "zeus_capital_keraunos_spire", "zeus_capital_cloud_throne",
                "poseidon_capital_atlantis_nexus", "poseidon_capital_trident_bastion", "poseidon_capital_abyssal_court"}) {
            try (InputStream stream = CardArtFactory.class.getResourceAsStream("/art/capitals/" + id + ".jpg")) {
                if (stream == null) continue;
                BufferedImage loaded = ImageIO.read(stream);
                if (loaded != null) PAINTED_ART.put(id, loaded);
            } catch (IOException ignored) {
            }
        }
    }

    /** Full-resolution capital painting for title-screen flourishes; null when missing. */
    static BufferedImage capitalPainting(String id) {
        BufferedImage cached = PAINTED_ART.get(id);
        if (cached != null) return cached;
        try (InputStream stream = CardArtFactory.class.getResourceAsStream("/art/capitals/" + id + ".jpg")) {
            if (stream == null) return null;
            BufferedImage loaded = ImageIO.read(stream);
            if (loaded != null) PAINTED_ART.put(id, loaded);
            return loaded;
        } catch (IOException ignored) {
            return null;
        }
    }

    /** The shared world backdrop, already decoded at class load. May be null. */
    static BufferedImage worldBackdrop() {
        return WORLDS;
    }

    static ImageIcon iconFor(CardDefinition card, int width, int height) {
        return CACHE.computeIfAbsent(card.id()+":"+width+"x"+height, key -> render(card,width,height));
    }

    static ImageIcon boardIconFor(CardDefinition card) {
        return CACHE.computeIfAbsent(card.id()+":board-compact", key -> render(card,78,56));
    }

    private static ImageIcon render(CardDefinition card, int w, int h) {
        BufferedImage painted = paintedArt(card);
        if (painted != null) return cover(painted, w, h);
        BufferedImage image = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics(); quality(g);
        Random random=new Random(((long)card.id().hashCode()<<32)^card.name().hashCode());
        world(g,card,w,h,random); atmosphere(g,card,w,h,random);
        subject(g,card,w,h,random); motifs(g,card,w,h,random); particleFinish(g,card,w,h); finish(g,card,w,h);
        g.dispose(); return new ImageIcon(image);
    }

    static boolean hasPaintedArt(CardDefinition card) {
        return paintedArt(card) != null;
    }

    private static BufferedImage paintedArt(CardDefinition card) {
        String folder = switch (card.type()) {
            case CAPITAL -> "capitals";
            case CHARACTER -> "characters";
            case SPELL -> "spells";
            case LAND -> "lands";
            case STRUCTURE -> "structures";
            default -> null;
        };
        if (folder == null) return null;
        BufferedImage cached = PAINTED_ART.get(card.id());
        if (cached != null) return cached;
        String path = "/art/" + folder + "/" + card.id() + ".jpg";
        try (InputStream stream = CardArtFactory.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            BufferedImage loaded = ImageIO.read(stream);
            if (loaded != null) PAINTED_ART.put(card.id(), loaded);
            return loaded;
        } catch (IOException ignored) {
            return null;
        }
    }

    /** Center-crops rather than distorting artwork across hand, inspector, and compact board formats. */
    private static ImageIcon cover(BufferedImage source, int w, int h) {
        double scale = Math.max((double) w / source.getWidth(), (double) h / source.getHeight());
        int sw = Math.max(1, (int) Math.round(w / scale));
        int sh = Math.max(1, (int) Math.round(h / scale));
        int sx = Math.max(0, (source.getWidth() - sw) / 2);
        int sy = Math.max(0, (source.getHeight() - sh) / 2);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        quality(g);
        g.drawImage(source, 0, 0, w, h, sx, sy, sx + sw, sy + sh, null);
        g.dispose();
        return new ImageIcon(image);
    }

    private static void world(Graphics2D g, CardDefinition card, int w, int h, Random random) {
        Integer i=WORLD.get(card.faction().toUpperCase(Locale.ROOT));
        if(WORLDS!=null&&i!=null){
            int sw=WORLDS.getWidth()/3,sh=WORLDS.getHeight()/2, sx=(i%3)*sw,sy=(i/3)*sh;
            int pan=Math.max(1,sw/10),left=sx+random.nextInt(pan),right=sx+sw-random.nextInt(pan);
            g.drawImage(WORLDS,0,0,w,h,left,sy,right,sy+sh,null);
        }else{
            Color c=faction(card.faction()); g.setPaint(new GradientPaint(0,0,c.darker(),w,h,c.brighter())); g.fillRect(0,0,w,h);
        }
        Color c=faction(card.faction()); g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),48)); g.fillRect(0,0,w,h);
    }

    private static void atmosphere(Graphics2D g, CardDefinition card, int w, int h, Random r) {
        Color c=highlight(card.faction());
        for(int i=0;i<8+Math.floorMod(card.id().hashCode(),8);i++){
            int s=Math.max(2,h/25+r.nextInt(Math.max(2,h/12))),x=r.nextInt(w),y=r.nextInt(h);
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),75+r.nextInt(75)));
            if(card.faction().equalsIgnoreCase("POSEIDON"))g.drawOval(x,y,s,s);else g.fillOval(x,y,s,s/2+1);
        }
    }

    private static void subject(Graphics2D g, CardDefinition card, int w, int h, Random r) {
        int x=w/2+(r.nextInt(17)-8)*w/100,y=h/2+h/10,s=Math.max(9,Math.min(w,h)*34/100);
        Color ink=new Color(4,9,17,210),rim=highlight(card.faction());
        g.setStroke(new BasicStroke(Math.max(1.5f,w/95f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(card.type()){
            case CHARACTER->character(g,x,y,s,ink,rim,card.name().hashCode());
            case LAND->land(g,x,y,s,ink,rim);
            case STRUCTURE->structure(g,x,y,s,ink,rim,false,r);
            case CAPITAL->structure(g,x,y,s+s/4,ink,rim,true,r);
            case SPELL->spell(g,x,y,s,ink,rim,r);
        }
    }

    private static void character(Graphics2D g,int x,int y,int s,Color ink,Color rim,int seed){
        Path2D p=new Path2D.Double();p.moveTo(x,y-s);p.curveTo(x-s,y-s/2.0,x-s,y+s,x-s/2.0,y+s);
        p.lineTo(x+s/2.0,y+s);p.curveTo(x+s,y,x+s,y-s/2.0,x,y-s);p.closePath();
        g.setColor(ink);g.fill(p);g.setColor(rim);g.draw(p);g.fillOval(x-s/3,y-s*6/5,s*2/3,s*2/3);
        g.setStroke(new BasicStroke(Math.max(2f,s/10f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(Math.floorMod(seed,4)){
            case 0->{g.drawLine(x+s/2,y+s/2,x+s,y-s);g.fillOval(x+s-s/6,y-s-s/6,s/3,s/3);}
            case 1->{g.drawLine(x+s/3,y+s/2,x+s,y-s/2);g.drawLine(x+s/4,y,x+s/2,y+s/4);}
            case 2->{g.drawLine(x-s*2/3,y+s,x-s*2/3,y-s);g.drawOval(x-s,y-s-s/3,s*2/3,s*2/3);}
            default->{g.drawArc(x+s/4,y-s,s,s*2,80,200);g.drawLine(x+s/2,y-s,x+s/2,y+s);}
        }
    }

    private static void land(Graphics2D g,int x,int y,int s,Color ink,Color rim){
        Path2D p=new Path2D.Double();p.moveTo(x-s*2,y+s);p.lineTo(x-s*3/2.0,y-s/3.0);p.lineTo(x-s/2.0,y);
        p.lineTo(x,y-s);p.lineTo(x+s/2.0,y-s/4.0);p.lineTo(x+s,y-s*3/4.0);p.lineTo(x+s*2,y+s);p.closePath();
        g.setColor(ink);g.fill(p);g.setColor(rim);g.draw(p);for(int i=-1;i<=1;i++)g.drawLine(x+i*s/2,y+s/4,x+i*s,y+s);
    }

    private static void structure(Graphics2D g,int x,int y,int s,Color ink,Color rim,boolean capital,Random r){
        int towers=capital?3:1+r.nextInt(3);g.setColor(ink);g.fillRoundRect(x-s,y-s/2,s*2,s*3/2,s/8,s/8);
        for(int i=0;i<towers;i++){int tx=towers==1?x:x-s+i*s*2/(towers-1),th=s+r.nextInt(Math.max(2,s/2));
            g.fillRect(tx-s/4,y-th,s/2,th+s);Path2D roof=new Path2D.Double();roof.moveTo(tx-s/3.0,y-th);roof.lineTo(tx,y-th-s/2.0);roof.lineTo(tx+s/3.0,y-th);roof.closePath();g.fill(roof);}
        g.setColor(rim);g.drawRoundRect(x-s,y-s/2,s*2,s*3/2,s/8,s/8);g.fillRect(x-s/7,y+s/3,s*2/7,s*2/3);
        if(capital)g.drawArc(x-s,y-s*2,s*2,s,10,160);
    }

    private static void spell(Graphics2D g,int x,int y,int s,Color ink,Color rim,Random r){
        g.setColor(new Color(ink.getRed(),ink.getGreen(),ink.getBlue(),155));g.fillOval(x-s,y-s,s*2,s*2);g.setColor(rim);
        int rays=6+r.nextInt(5);for(int i=0;i<rays;i++){double a=Math.PI*2*i/rays;g.drawLine(x+(int)(Math.cos(a)*s/3),y+(int)(Math.sin(a)*s/3),x+(int)(Math.cos(a)*s*3/2),y+(int)(Math.sin(a)*s*3/2));}g.fillOval(x-s/3,y-s/3,s*2/3,s*2/3);
    }

    private static void motifs(Graphics2D g,CardDefinition card,int w,int h,Random random){
        String text=(card.name()+" "+card.id()).toLowerCase(Locale.ROOT).replace('_',' ');List<Motif> found=find(text,card.type());
        for(int i=0;i<Math.min(3,found.size());i++){int s=Math.max(8,Math.min(w,h)/(i==0?4:5));int x=i==0?w*22/100:w*(78-(i-1)*55)/100;drawMotif(g,found.get(i),x,h*25/100,s,highlight(card.faction()));}
    }

    private static void particleFinish(Graphics2D g,CardDefinition card,int w,int h){
        int size=Math.max(28,Math.min(w,h)*4/5);Color glow=highlight(card.faction());
        VisualEffects.Sprite sprite=switch(card.faction().toUpperCase(Locale.ROOT)){
            case"POSEIDON"->VisualEffects.Sprite.RING;default->VisualEffects.Sprite.MAGIC;};
        VisualEffects.draw(g,sprite,w/2,h/2,size,glow,.20f,0);
        if(card.type()==CardType.SPELL)VisualEffects.draw(g,VisualEffects.Sprite.LIGHT,w/2,h/2,
                Math.max(22,size*3/4),Color.WHITE,.34f,0);
    }

    private static List<Motif> find(String t,CardType type){
        LinkedHashSet<Motif> m=new LinkedHashSet<>();
        add(m,t,Motif.BOLT,"storm","thunder","lightning","bolt","spark","sky","tempest");
        add(m,t,Motif.WAVE,"sea","tide","ocean","reef","abyss","trench","shoal","coral","siren");
        add(m,t,Motif.SOUL,"soul","grave","death","ashen","shade","memory","passage","crypt","underworld");
        add(m,t,Motif.FIRE,"fire","flame","ember","furnace","volcan","molten","lava","scorch");
        add(m,t,Motif.GEAR,"forge","iron","bronze","machine","gear","engine","automaton","foundry","ore","riveter");
        add(m,t,Motif.SPEAR,"war","ares","spartan","spear","lancer","hoplite","vanguard","skirmisher","ravager");
        add(m,t,Motif.EYE,"oracle","seer","watch","sentinel","owl","athena","pallas","strateg","council");
        add(m,t,Motif.STAR,"moon","star","aether","divine","heaven","olymp","celestial","crown");
        add(m,t,Motif.GATE,"gate","bridge","crossing","wall","bastion","citadel","tower","fort","relay");
        add(m,t,Motif.BLADE,"blade","assassin","cut","strike","hunter","stalker","duel","guard");
        add(m,t,Motif.WING,"wing","flight","runner","courier","tailwind","skimmer","swift","blink");
        add(m,t,Motif.LEAF,"grove","garden","field","bloom","living","sanctum","healing");
        add(m,t,Motif.HAMMER,"hammer","anvil","ram","breaker","siege","crusher","titan","colossus");
        add(m,t,Motif.BOOK,"archive","academy","scholar","analyst","tactic","pattern","wisdom");
        if(m.isEmpty())m.add(switch(type){case CHARACTER->Motif.BLADE;case LAND->Motif.STAR;case STRUCTURE->Motif.GATE;case SPELL->Motif.BOLT;case CAPITAL->Motif.CROWN;});return List.copyOf(m);
    }

    private static void add(Set<Motif> set,String text,Motif motif,String...words){for(String word:words)if(text.contains(word)){set.add(motif);return;}}

    private static void drawMotif(Graphics2D g,Motif m,int x,int y,int s,Color c){
        g.setStroke(new BasicStroke(Math.max(1.5f,s/7f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(3,8,15,175));g.fillOval(x-s,y-s,s*2,s*2);g.setColor(c);
        switch(m){
            case BOLT->{Path2D p=new Path2D.Double();p.moveTo(x+s/5.0,y-s);p.lineTo(x-s/2.0,y);p.lineTo(x,y);p.lineTo(x-s/4.0,y+s);p.lineTo(x+s/2.0,y-s/5.0);p.lineTo(x,y-s/5.0);p.closePath();g.fill(p);}
            case WAVE->{g.drawArc(x-s,y-s/2,s*2,s,10,170);g.drawArc(x-s/2,y,s*3/2,s,20,150);}
            case SOUL->{g.drawOval(x-s/2,y-s/2,s,s);g.drawLine(x-s/3,y+s/3,x-s/2,y+s);g.drawLine(x,y+s/2,x,y+s);g.drawLine(x+s/3,y+s/3,x+s/2,y+s);}
            case FIRE->{Path2D p=new Path2D.Double();p.moveTo(x,y-s);p.curveTo(x+s,y,x+s/2.0,y+s,x,y+s);p.curveTo(x-s,y+s/2.0,x-s/2.0,y,x,y-s);p.closePath();g.draw(p);}
            case GEAR->{g.drawOval(x-s*2/3,y-s*2/3,s*4/3,s*4/3);g.drawOval(x-s/4,y-s/4,s/2,s/2);for(int i=0;i<8;i++){double a=i*Math.PI/4;g.drawLine(x+(int)(Math.cos(a)*s*2/3),y+(int)(Math.sin(a)*s*2/3),x+(int)(Math.cos(a)*s),y+(int)(Math.sin(a)*s));}}
            case SPEAR->{g.drawLine(x-s,y+s,x+s,y-s);g.fillOval(x+s-s/5,y-s-s/5,s*2/5,s*2/5);}
            case EYE->{g.drawArc(x-s,y-s/2,s*2,s,0,180);g.drawArc(x-s,y-s/2,s*2,s,180,180);g.fillOval(x-s/4,y-s/4,s/2,s/2);}
            case STAR->{Path2D p=new Path2D.Double();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5,r=i%2==0?s:s*.4,px=x+Math.cos(a)*r,py=y+Math.sin(a)*r;if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}p.closePath();g.draw(p);}
            case GATE->{g.drawRect(x-s,y-s,s*2,s*2);g.drawArc(x-s/2,y-s/2,s,s,0,180);g.drawLine(x-s/2,y,x-s/2,y+s);g.drawLine(x+s/2,y,x+s/2,y+s);}
            case BLADE->{g.drawLine(x-s,y+s,x+s,y-s);g.drawLine(x-s,y-s,x+s,y+s);}
            case WING->{g.drawArc(x-s,y-s,s,s*2,270,180);g.drawArc(x,y-s,s,s*2,90,180);}
            case LEAF->{g.drawOval(x-s/2,y-s,s,s*2);g.drawLine(x,y-s,x,y+s);}
            case HAMMER->{g.fillRoundRect(x-s,y-s,s*3/2,s*2/3,s/5,s/5);g.drawLine(x,y-s/2,x+s,y+s);}
            case BOOK->{g.drawRect(x-s,y-s*2/3,s,s*4/3);g.drawRect(x,y-s*2/3,s,s*4/3);}
            case CROWN->{Path2D p=new Path2D.Double();p.moveTo(x-s,y-s/2.0);p.lineTo(x-s/2.0,y);p.lineTo(x,y-s);p.lineTo(x+s/2.0,y);p.lineTo(x+s,y-s/2.0);p.lineTo(x+s*3/4.0,y+s);p.lineTo(x-s*3/4.0,y+s);p.closePath();g.draw(p);}
        }
    }

    private static void finish(Graphics2D g,CardDefinition card,int w,int h){
        g.setPaint(new GradientPaint(0,h*.55f,new Color(0,0,0,0),0,h,new Color(0,0,0,160)));g.fillRect(0,h/2,w,h/2);
        g.setColor(new Color(255,236,174,205));g.setStroke(new BasicStroke(Math.max(1.2f,w/120f)));g.drawRoundRect(1,1,w-3,h-3,Math.max(8,h/6),Math.max(8,h/6));
        String mark=Arrays.stream(card.name().split("[^A-Za-z0-9]+")).filter(s->!s.isBlank()).limit(2).map(s->s.substring(0,1).toUpperCase(Locale.ROOT)).reduce("",String::concat);
        g.setFont(new Font(Font.SERIF,Font.BOLD,Math.max(8,h/7)));g.setColor(new Color(255,245,210,210));g.drawString(mark,Math.max(4,w/28),h-Math.max(4,h/18));
        if(!card.abilities().isEmpty()){int r=Math.max(4,Math.min(w,h)/14);g.setColor(new Color(155,231,255,220));g.drawOval(w-r*3,r,r*2,r*2);g.fillOval(w-r*2-r/3,r+r*2/3,r*2/3,r*2/3);}
    }

    private static BufferedImage loadWorlds(){try{return ImageIO.read(Objects.requireNonNull(CardArtFactory.class.getResourceAsStream("/art/faction-environments.png")));}catch(IOException|NullPointerException e){return null;}}
    private static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);}
    private static Color faction(String f){return switch(f.toUpperCase(Locale.ROOT)){case"POSEIDON"->new Color(32,137,171);case"ZEUS"->new Color(178,154,63);default->new Color(93,110,130);};}
    private static Color highlight(String f){return switch(f.toUpperCase(Locale.ROOT)){case"POSEIDON"->new Color(139,246,255);case"ZEUS"->new Color(255,239,157);default->new Color(224,234,245);};}
    private enum Motif{BOLT,WAVE,SOUL,FIRE,GEAR,SPEAR,EYE,STAR,GATE,BLADE,WING,LEAF,HAMMER,BOOK,CROWN}
}
