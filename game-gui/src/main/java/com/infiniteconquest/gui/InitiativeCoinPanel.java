package com.infiniteconquest.gui;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import static com.infiniteconquest.gui.UiTheme.*;

/** A tossed disk: trajectory and face mapping are independent of the selected surface art. */
final class InitiativeCoinPanel extends JPanel {
    static final long DURATION_NANOS = 3_800_000_000L;
    enum Skin {
        OLYMPIAN_GOLD("Olympian Gold",new Color(255,235,149),new Color(156,92,25)),
        MOON_SILVER("Moon Silver",new Color(236,248,255),new Color(86,110,139)),
        OBSIDIAN("Obsidian",new Color(126,108,172),new Color(25,19,42)),
        ZEUS("Zeus",new Color(255,235,149),new Color(156,92,25),"/art/coins/zeus.png"),
        POSEIDON("Poseidon",new Color(236,248,255),new Color(44,84,130),"/art/coins/poseidon.png");
        final String label; final Color light,dark; final String artPath;
        Skin(String label,Color light,Color dark){this(label,light,dark,null);}
        Skin(String label,Color light,Color dark,String artPath){this.label=label;this.light=light;this.dark=dark;this.artPath=artPath;}
        @Override public String toString(){return label;}
    }
    record Pose(double lift,double angle,double tilt) { }
    private static final Font TITLE_FONT = new Font(Font.SERIF, Font.BOLD, 24);
    private static final Font SUBTITLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Color BACK_TOP = new Color(27, 44, 65);
    private static final Color BACK_BOTTOM = new Color(10, 18, 31);
    private static final Color SUBTITLE_TEXT = new Color(184, 202, 218);
    private final int winner;
    private final Skin skin;
    private final Color rimDark;
    private final BufferedImage[] faces;
    private GradientPaint backgroundPaint;
    private int backgroundH = -1;
    private double progress;
    /** Faction-styled result wording, e.g. "Zeus"; set by the caller. */
    private String winnerName;
    private long landedAtNanos = -1;
    private long resultAtNanos = -1;
    /** Progress at which the coin touches down and the landing beat begins. */
    private static final double LAND_PROGRESS = 0.78;
    InitiativeCoinPanel(int winner) { this(winner,Skin.OLYMPIAN_GOLD); }
    InitiativeCoinPanel(int winner,Skin skin) {
        if(winner<0 || winner>1)throw new IllegalArgumentException("Invalid coin winner");
        this.winner=winner;this.skin=skin;faces=facesFor(skin);rimDark=skin.dark.darker();setOpaque(false);
    }
    /** Package-visible for tests: the rendered face images, 240x240 each. */
    BufferedImage faceImage(int index){return faces[index];}
    /** Package-visible for tests: true when the skin's generated art loaded from resources. */
    boolean facesFromArt(){return skin.artPath!=null&&loadArt(skin.artPath)!=null;}
    private static BufferedImage[] facesFor(Skin skin) {
        if(skin.artPath!=null){
            BufferedImage art=loadArt(skin.artPath);
            if(art!=null)return new BufferedImage[]{art,art};
        }
        return new BufferedImage[]{surface(skin,"I"),surface(skin,"II")};
    }
    private static BufferedImage loadArt(String path) {
        try(var stream=InitiativeCoinPanel.class.getResourceAsStream(path)){
            if(stream==null)return null;
            BufferedImage image=ImageIO.read(stream);
            if(image==null||image.getWidth()!=240||image.getHeight()!=240)return null;
            return image;
        }catch(IOException e){return null;}
    }
    void setProgress(double value){
        double prev=progress;
        progress=Math.max(0,Math.min(1,value));
        long now=System.nanoTime();
        if(prev<LAND_PROGRESS&&progress>=LAND_PROGRESS)landedAtNanos=now;
        if(prev<1&&progress>=1)resultAtNanos=now;
        repaint();
    }
    /** Faction-styled result wording ("Zeus takes the initiative"); null keeps the legacy Player N text. */
    void setWinnerName(String name){winnerName=name;repaint();}
    static double angle(double progress,int winner){return pose(progress,winner).angle();}
    static Pose pose(double progress,int winner) {
        double p=Math.max(0,Math.min(1,progress)), flight=Math.min(1,p/.78);
        double rotation=(6*Math.PI+winner*Math.PI)*(flight<1?flight:1);
        double lift=4*132*flight*(1-flight);
        if(p>.78){double settle=(p-.78)/.22;lift=25*Math.abs(Math.sin(settle*Math.PI*2))*Math.pow(1-settle,2);}
        return new Pose(lift,rotation,Math.sin(flight*Math.PI)*.12);
    }
    private static BufferedImage surface(Skin skin,String symbol) {
        BufferedImage image=new BufferedImage(240,240,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(0,0,240,240));
        g.setPaint(new GradientPaint(15,10,skin.light,225,235,skin.dark));g.fillRect(0,0,240,240);
        g.setStroke(new BasicStroke(3));g.setColor(skin.light);g.drawOval(8,8,224,224);
        g.setColor(skin.dark);g.drawOval(27,27,186,186);
        for(int i=0;i<48;i++){double a=i*Math.PI/24;g.drawLine(120+(int)(Math.cos(a)*101),120+(int)(Math.sin(a)*101),120+(int)(Math.cos(a)*108),120+(int)(Math.sin(a)*108));}
        g.setFont(new Font(Font.SERIF,Font.BOLD,86));int x=(240-g.getFontMetrics().stringWidth(symbol))/2;
        g.setColor(skin.light);g.drawString(symbol,x+2,152);g.setColor(skin.dark);g.drawString(symbol,x,150);g.dispose();return image;
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (backgroundPaint == null || backgroundH != getHeight()) {
            backgroundPaint = new GradientPaint(0, 0, BACK_TOP, 0, getHeight(), BACK_BOTTOM);
            backgroundH = getHeight();
        }
        g.setPaint(backgroundPaint);g.fillRect(0,0,getWidth(),getHeight());
        long now=System.nanoTime();
        paintStormSide(g,now);
        paintTideSide(g,now);
        paintDivider(g);
        Pose pose=pose(progress,winner);double cosine=Math.cos(pose.angle());
        int cx=getWidth()/2,cy=(int)(225-pose.lift());
        int restY=306;
        int shadow=(int)(136-pose.lift()*.42);g.setColor(new Color(0,0,0,(int)(85-pose.lift()*.3)));g.fillOval(cx-shadow/2,restY,shadow,16);
        paintLandingBeat(g,cx,restY,now);
        Graphics2D model=(Graphics2D)g.create();model.translate(cx,cy);model.rotate(pose.tilt());
        double squash=Math.max(.025,Math.abs(cosine));double rim=7*Math.abs(Math.sin(pose.angle()))+2;
        g.setColor(skin.dark);
        model.setColor(rimDark);model.fill(new Ellipse2D.Double(-78,-78*squash,156,156*squash+rim));
        Graphics2D face=(Graphics2D)model.create();face.scale(1,squash);face.clip(new Ellipse2D.Double(-78,-78,156,156));
        face.drawImage(faces[cosine>=0?0:1],-78,-78,156,156,null);face.dispose();model.dispose();
        paintTitles(g,now);
        g.dispose();
    }
    /** Left half: Zeus storm — bruised clouds and flickering lightning. */
    private void paintStormSide(Graphics2D g,long now){
        int w=getWidth()/2,h=getHeight();
        g.setPaint(new GradientPaint(0,0,new Color(24,20,38),0,h,new Color(10,10,20)));
        g.fillRect(0,0,w,h);
        long frame=now/130_000_000L;
        for(int i=0;i<4;i++){
            java.util.Random r=new java.util.Random(frame*131L+i*17L+7L);
            if(r.nextDouble()<0.45)continue; // bolt rests between strikes
            int x0=(int)(w*(0.12+0.76*r.nextDouble()));
            int brightness=140+r.nextInt(115);
            g.setColor(new Color(255,235,170,Math.min(255,brightness)));
            g.setStroke(new BasicStroke(2.2f));
            int x=x0,y=0;
            java.awt.Polygon bolt=new java.awt.Polygon();
            bolt.addPoint(x,y);
            while(y<h*0.75){x+=(r.nextInt(46)-23);y+=18+r.nextInt(26);bolt.addPoint(x,y);}
            g.drawPolyline(bolt.xpoints,bolt.ypoints,bolt.npoints);
            g.setColor(new Color(255,250,225,brightness/4));
            g.fillOval(x0-46,0,92,60); // flash bloom at the cloud line
        }
        if(progress>=LAND_PROGRESS&&winner==0)paintWinnerGlow(g,0,w,new Color(255,214,110));
    }
    /** Right half: Poseidon tide — deep water, rising bubbles, low waves. */
    private void paintTideSide(Graphics2D g,long now){
        int half=getWidth()/2,w=getWidth()-half,h=getHeight();
        g.setPaint(new GradientPaint(half,0,new Color(8,32,54),half,h,new Color(4,14,28)));
        g.fillRect(half,0,w,h);
        double t=(now/1_000_000_000.0);
        for(int i=0;i<26;i++){
            long seed=i*2654435761L;
            java.util.Random r=new java.util.Random(seed);
            double fx=r.nextDouble(),speed=0.05+r.nextDouble()*0.09,size=2+r.nextDouble()*5;
            double fy=1-((t*speed+r.nextDouble())%1.0);
            int x=half+(int)(fx*w+Math.sin(t*1.7+i)*8),y=(int)(fy*h);
            int alpha=(int)(60+90*fy);
            g.setColor(new Color(150,220,255,Math.min(200,alpha)));
            g.fillOval(x,y,(int)size,(int)size);
        }
        g.setColor(new Color(120,200,240,70));
        for(int row=0;row<3;row++){
            int y0=h-26-row*22;
            for(int x=half;x<getWidth();x+=44){
                g.setStroke(new BasicStroke(2f));
                g.drawArc(x,y0-10,44,20,180,180);
            }
        }
        if(progress>=LAND_PROGRESS&&winner==1)paintWinnerGlow(g,half,w,new Color(120,210,250));
    }
    /** Soft radial glow on the winning half once the coin lands. */
    private void paintWinnerGlow(Graphics2D g,int x0,int w,Color tint){
        int cx=x0+w/2,cy=getHeight()/2;
        float radius=(float)(w*0.75);
        g.setPaint(new RadialGradientPaint(cx,cy,radius,
                new float[]{0f,1f},
                new Color[]{new Color(tint.getRed(),tint.getGreen(),tint.getBlue(),70),
                        new Color(tint.getRed(),tint.getGreen(),tint.getBlue(),0)}));
        g.fillRect(x0,0,w,getHeight());
    }
    /** Center seam between storm and tide. */
    private void paintDivider(Graphics2D g){
        int cx=getWidth()/2;
        g.setPaint(new GradientPaint(cx,0,new Color(255,255,255,0),cx,getHeight(),new Color(255,255,255,26)));
        g.fillRect(cx-1,0,2,getHeight());
    }
    /** Landing beat: impact flash plus expanding gold rings from the touchdown point. */
    private void paintLandingBeat(Graphics2D g,int cx,int restY,long now){
        if(landedAtNanos<0)return;
        double elapsed=(now-landedAtNanos)/1_000_000_000.0;
        if(elapsed>1.1)return;
        if(elapsed<0.4){ // impact flash
            float a=(float)(0.5*(1-elapsed/0.4));
            g.setPaint(new RadialGradientPaint(cx,restY,120,
                    new float[]{0f,1f},
                    new Color[]{new Color(255,240,200,(int)(a*255)),new Color(255,240,200,0)}));
            g.fillOval(cx-120,restY-120,240,240);
        }
        for(int i=0;i<3;i++){ // expanding rings, staggered
            double local=elapsed-i*0.14;
            if(local<0||local>0.85)continue;
            float radius=(float)(local*300);
            float alpha=(float)(0.75*(1-local/0.85));
            g.setColor(new Color(255,214,110,Math.max(0,(int)(alpha*255))));
            g.setStroke(new BasicStroke(3f));
            g.drawOval((int)(cx-radius),(int)(restY-radius/2),(int)(radius*2),(int)radius);
        }
    }
    /** Titles: the toss, then the winner's beat with a pop-in scale. */
    private void paintTitles(Graphics2D g,long now){
        boolean decided=progress>=1;
        if(!decided){
            g.setFont(TITLE_FONT);g.setColor(Color.WHITE);
            centered(g,"A TOSS OF FATE",355);
            g.setFont(SUBTITLE_FONT);g.setColor(SUBTITLE_TEXT);
            centered(g,skin.label+" · deciding initiative",381);
            return;
        }
        String name=winnerName!=null?winnerName.toUpperCase():"PLAYER "+(winner+1);
        String headline=name+" TAKES THE INITIATIVE";
        double pop=resultAtNanos<0?1:Math.min(1,(now-resultAtNanos)/350_000_000.0);
        float scale=(float)(0.6+0.4*easeOutBack(pop));
        Graphics2D t=(Graphics2D)g.create();
        t.translate(getWidth()/2.0,352);t.scale(scale,scale);
        t.setFont(TITLE_FONT.deriveFont(27f));t.setColor(GOLD);
        int sw=t.getFontMetrics().stringWidth(headline);
        t.drawString(headline,-sw/2,0);
        t.dispose();
        g.setFont(SUBTITLE_FONT);g.setColor(SUBTITLE_TEXT);
        centered(g,"The battlefield is ready.",381);
    }
    private static double easeOutBack(double t){
        double c=1.70158;
        return 1+(c+1)*Math.pow(t-1,3)+c*Math.pow(t-1,2);
    }
    private void centered(Graphics2D g,String text,int y){g.drawString(text,(getWidth()-g.getFontMetrics().stringWidth(text))/2,y);}
}
