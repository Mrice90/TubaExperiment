package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared effect-timing constants behave: full motion keeps authored
 * durations, REDUCED collapses them to a single frame and kills particles,
 * and the easing curves stay within [0, 1]-ish bounds.
 */
class FxTest {
    private static GameSettings full() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-fx-full");
        System.setProperty("user.home", fakeHome.toString());
        try {
            GameSettings settings = GameSettings.load();
            settings.animationMode = GameSettings.AnimationMode.FULL;
            return settings;
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    private static GameSettings reduced() throws Exception {
        GameSettings settings = full();
        settings.animationMode = GameSettings.AnimationMode.REDUCED;
        return settings;
    }

    @Test
    void fullModeKeepsAuthoredDurations() throws Exception {
        GameSettings settings = full();
        assertEquals(300_000_000L, Fx.durationNanos(Fx.DEPLOY_MS, settings));
        assertEquals(1_200_000_000L, Fx.durationNanos(Fx.DAMAGE_FLOAT_MS, settings));
        assertTrue(Fx.particles(settings));
        assertFalse(Fx.reduced(settings));
    }

    @Test
    void reducedModeCollapsesMotionToOneFrame() throws Exception {
        GameSettings settings = reduced();
        assertEquals(1L, Fx.durationNanos(Fx.DEPLOY_MS, settings));
        assertEquals(1L, Fx.durationNanos(Fx.TURN_BANNER_MS, settings));
        assertFalse(Fx.particles(settings));
        assertTrue(Fx.reduced(settings));
    }

    @Test
    void nullSettingsBehaveAsFull() {
        assertFalse(Fx.reduced(null));
        assertTrue(Fx.particles(null));
        assertEquals(800_000_000L, Fx.durationNanos(Fx.GENERIC_MS, null));
    }

    @Test
    void easingCurvesAreWellBehaved() {
        assertEquals(0f, Fx.easeOutCubic(0f), 1e-6);
        assertEquals(1f, Fx.easeOutCubic(1f), 1e-6);
        assertTrue(Fx.easeOutCubic(0.5f) > 0.5f, "ease-out should front-load progress");
        assertEquals(0.5f, Fx.easeInOutQuad(0.5f), 1e-6);
        // Damped spring overshoots early but settles at 1.
        assertTrue(Fx.spring(0.2f) > 1f, "spring should overshoot while settling");
        assertEquals(1f, Fx.spring(1f), 0.02f);
        assertEquals(1f, Fx.spring(2f), 0.01f);
    }

    @Test
    void effectWindowsFitInsideTheirAnimations() {
        assertTrue(Fx.HIT_FLASH_MS < Fx.DAMAGE_FLOAT_MS);
        assertTrue(Fx.BADGE_FADE_MS < Fx.BADGE_MS / 2);
        assertTrue(Fx.SHAKE_MS < Fx.DAMAGE_FLOAT_MS);
    }

    @Test
    void playFlightAndHoverTimings() throws Exception {
        assertTrue(Fx.PLAY_FLIGHT_MS >= 350 && Fx.PLAY_FLIGHT_MS <= 450,
                "card-play flight must run 350-450ms, was " + Fx.PLAY_FLIGHT_MS);
        assertTrue(Fx.HOVER_MS > 0 && Fx.HOVER_MS < 150,
                "hover lift must stay under 150ms, was " + Fx.HOVER_MS);
        assertTrue(Fx.LAND_POP_MS < Fx.PLAY_FLIGHT_MS,
                "landing pop must fit inside the flight window");
        assertEquals(1L, Fx.durationNanos(Fx.PLAY_FLIGHT_MS, reduced()),
                "reduced mode must collapse the flight to one frame");
    }
}
