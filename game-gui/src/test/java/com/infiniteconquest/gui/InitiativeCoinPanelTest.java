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
}
