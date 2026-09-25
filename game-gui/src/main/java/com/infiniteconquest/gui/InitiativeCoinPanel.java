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
    private final int winner;
    private final Skin skin;
    private final BufferedImage[] faces;
    private double progress;
    InitiativeCoinPanel(int winner) { this(winner,Skin.OLYMPIAN_GOLD); }
    InitiativeCoinPanel(int winner,Skin skin) {
        if(winner<0 || winner>1)throw new IllegalArgumentException("Invalid coin winner");
        this.winner=winner;this.skin=skin;faces=facesFor(skin);setOpaque(false);
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
    void setProgress(double value){progress=Math.max(0,Math.min(1,value));repaint();}
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
        g.setPaint(new GradientPaint(0,0,new Color(27,44,65),0,getHeight(),new Color(10,18,31)));g.fillRect(0,0,getWidth(),getHeight());
        Pose pose=pose(progress,winner);double cosine=Math.cos(pose.angle());
        int cx=getWidth()/2,cy=(int)(225-pose.lift());
        int shadow=(int)(136-pose.lift()*.42);g.setColor(new Color(0,0,0,(int)(85-pose.lift()*.3)));g.fillOval(cx-shadow/2,306,shadow,16);
        Graphics2D model=(Graphics2D)g.create();model.translate(cx,cy);model.rotate(pose.tilt());
        double squash=Math.max(.025,Math.abs(cosine));double rim=7*Math.abs(Math.sin(pose.angle()))+2;
        g.setColor(skin.dark);
        model.setColor(skin.dark.darker());model.fill(new Ellipse2D.Double(-78,-78*squash,156,156*squash+rim));
        Graphics2D face=(Graphics2D)model.create();face.scale(1,squash);face.clip(new Ellipse2D.Double(-78,-78,156,156));
        face.drawImage(faces[cosine>=0?0:1],-78,-78,156,156,null);face.dispose();model.dispose();
        g.setFont(new Font(Font.SERIF,Font.BOLD,24));g.setColor(progress>=1?GOLD:Color.WHITE);
        centered(g,progress>=1?"PLAYER "+(winner+1)+" STARTS":"A TOSS OF FATE",355);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.setColor(new Color(184,202,218));
        centered(g,progress>=1?"The battlefield is ready.":skin.label+" · deciding initiative",381);g.dispose();
    }
    private void centered(Graphics2D g,String text,int y){g.drawString(text,(getWidth()-g.getFontMetrics().stringWidth(text))/2,y);}
}
