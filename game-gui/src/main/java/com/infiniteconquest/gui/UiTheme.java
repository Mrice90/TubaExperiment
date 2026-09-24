package com.infiniteconquest.gui;

import java.awt.Color;

/**
 * Shared visual language for the desktop client.
 *
 * Keep presentation constants here so the large Swing shell can be decomposed
 * incrementally without allowing colors and spacing to drift between views.
 */
final class UiTheme {
    static final Color INK = new Color(14, 20, 31);
    static final Color PANEL = new Color(25, 35, 52);
    static final Color PANEL_LIGHT = new Color(36, 49, 70);
    static final Color BOARD_STAGE = new Color(10, 16, 27);
    static final Color BOARD_STAGE_EDGE = new Color(48, 65, 88);
    static final Color GOLD = new Color(240, 191, 73);
    static final Color HUMAN_PLOT = new Color(28, 62, 76);
    static final Color BOT_PLOT = new Color(69, 38, 50);
    static final Color SELECTED = new Color(91, 209, 255);
    static final Color MOVE = new Color(72, 181, 230);
    static final Color ATTACK = new Color(244, 92, 92);
    static final Color DEPLOY = new Color(104, 211, 139);
    static final Color BURROW = new Color(190, 121, 235);
    static final Color CAST = new Color(246, 194, 78);

    static final int SCREEN_GAP = 8;
    static final int TILE_GAP = 6;
    static final int TILE_PADDING = 7;
    static final int COMPACT_TILE_PADDING = 3;
    static final int HAND_PADDING = 7;
    static final int IDLE_BORDER = 1;
    static final int EMPHASIS_BORDER = 5;
    static final float TARGET_TINT = .22f;
    static final float SELECTED_TINT = .24f;

    private UiTheme() {
    }
}
