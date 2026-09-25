package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitiativeCoinPanelTest {
    @Test void bothWinnersLandOnTheirCorrectFaceWithoutAFinalFrameSnap() {
        for (int winner=0;winner<2;winner++) {
            double finish=InitiativeCoinPanel.angle(1,winner);
            assertEquals(winner==0?1:-1,Math.cos(finish),1e-10);
            assertEquals(finish,InitiativeCoinPanel.angle(1.01,winner));
            assertTrue(Math.abs(finish-InitiativeCoinPanel.angle(.999,winner))<1e-6);
            double previous=0;
            for(int frame=0;frame<=140;frame++) {
                double angle=InitiativeCoinPanel.angle(frame/140.0,winner);
                assertTrue(angle>=previous);
                previous=angle;
            }
        }
    }
    @Test void tossHasALargeAirborneArcAndSmallerLandingBounces() {
        assertEquals(0,InitiativeCoinPanel.pose(0,0).lift());
        assertEquals(132,InitiativeCoinPanel.pose(.39,0).lift(),.001);
        assertEquals(0,InitiativeCoinPanel.pose(.78,0).lift(),.001);
        assertTrue(InitiativeCoinPanel.pose(.82,0).lift()>5);
        assertTrue(InitiativeCoinPanel.pose(.94,0).lift()<InitiativeCoinPanel.pose(.82,0).lift());
        assertEquals(0,InitiativeCoinPanel.pose(1,0).lift(),.001);
        assertTrue(InitiativeCoinPanel.DURATION_NANOS>=3_500_000_000L);
    }
    @Test void zeusAndPoseidonSkinsLoadTheirGeneratedArtFaces() {
        for(var skin:new InitiativeCoinPanel.Skin[]{InitiativeCoinPanel.Skin.ZEUS,InitiativeCoinPanel.Skin.POSEIDON}) {
            InitiativeCoinPanel panel=new InitiativeCoinPanel(0,skin);
            assertTrue(panel.facesFromArt(),skin+" should load its generated coin art");
            for(int i=0;i<2;i++) {
                assertNotNull(panel.faceImage(i));
                assertEquals(240,panel.faceImage(i).getWidth());
                assertEquals(240,panel.faceImage(i).getHeight());
            }
        }
        InitiativeCoinPanel zeus=new InitiativeCoinPanel(0,InitiativeCoinPanel.Skin.ZEUS);
        InitiativeCoinPanel poseidon=new InitiativeCoinPanel(0,InitiativeCoinPanel.Skin.POSEIDON);
        assertFalse(samePixels(zeus.faceImage(0),poseidon.faceImage(0)),"Zeus and Poseidon faces must differ");
    }
    @Test void classicSkinsKeepTheirProceduralFaces() {
        for(var skin:new InitiativeCoinPanel.Skin[]{InitiativeCoinPanel.Skin.OLYMPIAN_GOLD,InitiativeCoinPanel.Skin.MOON_SILVER,InitiativeCoinPanel.Skin.OBSIDIAN}) {
            InitiativeCoinPanel panel=new InitiativeCoinPanel(1,skin);
            assertFalse(panel.facesFromArt(),skin+" should keep its procedural faces");
            assertNotNull(panel.faceImage(0));
            assertNotNull(panel.faceImage(1));
            assertFalse(samePixels(panel.faceImage(0),panel.faceImage(1)),"procedural faces I and II must differ");
        }
    }
    private static boolean samePixels(java.awt.image.BufferedImage a,java.awt.image.BufferedImage b) {
        if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;
        for(int y=0;y<a.getHeight();y+=7)for(int x=0;x<a.getWidth();x+=7)
            if(a.getRGB(x,y)!=b.getRGB(x,y))return false;
        return true;
    }
}
