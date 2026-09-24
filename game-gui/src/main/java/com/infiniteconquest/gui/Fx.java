package com.infiniteconquest.gui;

/**
 * Central home for animation timing and easing curves.
 *
 * <p>Every battle duration the game plays lives here (in milliseconds), so game
 * feel can be tuned in one place instead of hunting literals across the GUI.
 * All motion must respect {@link GameSettings.AnimationMode#REDUCED}: reduced
 * mode skips transitions and particles.
 */
public final class Fx {
    private Fx() {}

    // ---- battle overlay (existing) -----------------------------------------
    public static final int DEPLOY_MS = 300;
    public static final int MOVE_MS = 320;
    public static final int MELEE_MS = 360;
    public static final int DESTROY_MS = 320;
    public static final int SNAP_BACK_MS = 180;
    public static final int GENERIC_MS = 800;

    // ---- battle overlay (new in 0.7.0) -------------------------------------
    /** Floating damage numbers: rise, hold, and a slow readable fade. */
    public static final int DAMAGE_FLOAT_MS = 1200;
    /** White/red flash washed over a struck tile. */
    public static final int HIT_FLASH_MS = 350;
    /** Glass-pane shake on capital hits and lethal destruction. */
    public static final int SHAKE_MS = 450;
    /** "YOUR TURN / ENEMY TURN" banner sweep. */
    public static final int TURN_BANNER_MS = 1300;
    /** Board badge lifetime (damage/destroyed/blink labels). */
    public static final int BADGE_MS = 2200;
    /** Badge fade in/out at each end of its lifetime. */
    public static final int BADGE_FADE_MS = 500;
    /** Deploy landing pop: scale overshoot after the card arc lands. */
    public static final int LAND_POP_MS = 120;

    /** Milliseconds to nanoseconds, the overlay's native unit. */
    public static long nanos(int millis) {
        return millis * 1_000_000L;
    }

    /** True when the player asked for minimal motion. */
    public static boolean reduced(GameSettings settings) {
        return settings != null
                && settings.animationMode == GameSettings.AnimationMode.REDUCED;
    }

    /**
     * Duration for the given base length under the player's animation mode.
     * REDUCED collapses motion to a single frame so presentation sequences
     * still complete in order, just without the travel time.
     */
    public static long durationNanos(int baseMillis, GameSettings settings) {
        if (reduced(settings)) {
            return 1L;
        }
        return nanos(baseMillis);
    }

    /** Whether particles and debris may render under this animation mode. */
    public static boolean particles(GameSettings settings) {
        return !reduced(settings);
    }

    // ---- easing ------------------------------------------------------------

    /** Cubic ease-out: fast start, gentle landing. The game's default motion. */
    public static float easeOutCubic(float value) {
        float u = 1f - value;
        return 1f - u * u * u;
    }

    /** Quadratic ease-in-out: gentle at both ends. Banners, slides, fades. */
    public static float easeInOutQuad(float value) {
        if (value < .5f) return 2f * value * value;
        float u = -2f * value + 2f;
        return 1f - u * u / 2f;
    }

    /** Damped spring: overshoots then settles. Snap-backs and landing pops. */
    public static float spring(float value) {
        return (float) (1.0 - Math.exp(-5.0 * value) * Math.cos(9.0 * value));
    }
}
