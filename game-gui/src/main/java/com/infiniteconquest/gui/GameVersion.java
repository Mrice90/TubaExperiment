package com.infiniteconquest.gui;

/**
 * Single source of truth for the game version. Bump this for every release;
 * the title screen, credits, window titles, and the in-app updater all read it.
 */
public final class GameVersion {
    /** Semantic version of this build, e.g. "0.6.0". */
    public static final String VERSION = "0.7.13";

    /** Release tag this version publishes under, e.g. "v0.6.0-alpha". */
    public static final String TAG = "v" + VERSION + "-alpha";

    private GameVersion() { }

    public static String displayName() {
        return "Alpha " + VERSION;
    }

    /**
     * Compares dotted numeric versions. Returns a positive number when
     * {@code newer} is newer than {@code older}; non-numeric suffixes
     * (like "-alpha") are ignored.
     */
    public static int compare(String older, String newer) {
        int[] a = parts(older);
        int[] b = parts(newer);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return y - x;
        }
        return 0;
    }

    /** True when {@code candidate} is a newer release than {@code current}. */
    public static boolean isNewerThan(String current, String candidate) {
        return compare(current, candidate) > 0;
    }

    private static int[] parts(String version) {
        String numeric = version.trim();
        int dash = numeric.indexOf('-');
        if (dash >= 0) numeric = numeric.substring(0, dash);
        if (numeric.startsWith("v") || numeric.startsWith("V")) numeric = numeric.substring(1);
        String[] tokens = numeric.split("\\.");
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                out[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
