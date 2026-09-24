package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class HexTextTest {
    @Test void longNamesAndStatusesFitEntireLineInsideSlopedEdges() {
        Graphics2D g = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            for (int height : new int[]{70,85,96,120,180}) {
                int width = (int)(height * 1.15);
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(10,Math.min(14,height/8))));
                FontMetrics metrics = g.getFontMetrics();
                for (double row : new double[]{.16,.59,.75,.9}) {
                    int baseline = (int)(height*row);
                    int available = HexText.lineWidth(width,height,baseline-metrics.getAscent(),baseline+metrics.getDescent());
                    for (String value : new String[]{"Kraken Prime Ancient", "Maelstrom Bulwark", "Olympus Citadel", "RETALIATION • DESTROYED", "A 100 / D 100", "P2 · ×24"}) {
                        String fitted = HexText.fit(value,metrics,available);
                        assertTrue(metrics.stringWidth(fitted)<=available);
                        assertTrue(available <= width-14);
                    }
                }
            }
        } finally { g.dispose(); }
    }
    @Test void statusKeepsTheOutcomeInsteadOfClippingTheImportantPart() {
        assertEquals("LOST",HexText.status("RETALIATION • DESTROYED"));
        assertEquals("3 DMG",HexText.status("FREE ATTACK • 3 DMG"));
        assertEquals("PLACED",HexText.status("PLACED ON TOP"));
        assertEquals("BLOCKED",HexText.status("FREE ATTACK • BLOCKED"));
    }
}
