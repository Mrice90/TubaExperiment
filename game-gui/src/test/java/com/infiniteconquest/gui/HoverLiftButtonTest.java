package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-card hover lift: a soft shadow blooms and the card rises on hover
 * or keyboard focus, settling back when the pointer leaves. Fast in FULL
 * mode, instant in REDUCED mode.
 */
class HoverLiftButtonTest {
    private static GameSettings settings(GameSettings.AnimationMode mode) {
        GameSettings loaded = GameSettings.load();
        loaded.animationMode = mode;
        return loaded;
    }

    private static BufferedImage paint(HoverLiftButton button) {
        button.setSize(156, 40);
        button.doLayout();
        BufferedImage image = new BufferedImage(156, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static boolean differs(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
        return false;
    }

    private static void mouse(HoverLiftButton button, int id) {
        button.dispatchEvent(new MouseEvent(button, id, System.currentTimeMillis(),
                0, 10, 10, 0, false));
    }

    @Test void hoverLiftsAndSettlesBack() throws Exception {
        HoverLiftButton button = new HoverLiftButton("Test", null,
                settings(GameSettings.AnimationMode.FULL));
        BufferedImage rest = paint(button);
        mouse(button, MouseEvent.MOUSE_ENTERED);
        Thread.sleep(200); // the 16ms timer completes the 120ms lift
        assertTrue(differs(rest, paint(button)),
                "hover should lift the card and bloom a shadow");
        mouse(button, MouseEvent.MOUSE_EXITED);
        Thread.sleep(200);
        assertFalse(differs(rest, paint(button)),
                "unhover should settle back to the rest state");
    }

    @Test void reducedModeAppliesHoverInstantly() {
        HoverLiftButton button = new HoverLiftButton("Test", null,
                settings(GameSettings.AnimationMode.REDUCED));
        BufferedImage rest = paint(button);
        mouse(button, MouseEvent.MOUSE_ENTERED);
        assertTrue(differs(rest, paint(button)),
                "reduced mode should apply the hover end state at once");
    }

    @Test void keyboardFocusLiftsLikeHover() {
        HoverLiftButton button = new HoverLiftButton("Test", null,
                settings(GameSettings.AnimationMode.REDUCED));
        BufferedImage rest = paint(button);
        FocusEvent gained = new FocusEvent(button, FocusEvent.FOCUS_GAINED);
        for (FocusListener listener : button.getFocusListeners()) listener.focusGained(gained);
        assertTrue(differs(rest, paint(button)),
                "keyboard focus should lift the card exactly like hover");
    }
}
