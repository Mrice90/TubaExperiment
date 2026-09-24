package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class VisualEffectsTest {
    @Test void cachedEffectNeverSpillsOutsideRequestedBoundsAcrossSizeBuckets() {
        for(int size=17;size<=83;size++){
            BufferedImage image=new BufferedImage(140,140,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();VisualEffects.draw(g,VisualEffects.Sprite.LIGHT,70,70,size,Color.WHITE,1,0);g.dispose();
            Rectangle expected=new Rectangle(70-size/2,70-size/2,size,size);int pixels=0;
            for(int y=0;y<140;y++)for(int x=0;x<140;x++)if((image.getRGB(x,y)>>>24)!=0){pixels++;assertTrue(expected.contains(x,y),"Size bucket leaked at size "+size);}
            assertTrue(pixels>0,"Effect must render");
        }
    }
}
