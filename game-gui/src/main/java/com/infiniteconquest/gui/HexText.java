package com.infiniteconquest.gui;

import java.awt.FontMetrics;

/** Text must fit the narrowest horizontal span of its entire line inside a pointed-top hex. */
final class HexText {
    static int lineWidth(int width, int height, int top, int bottom) {
        double edge = Math.min(Math.max(0, top), Math.max(0, height - bottom));
        double span = width * Math.min(1, 4 * edge / Math.max(1, height));
        return Math.max(0, (int) span - 14);
    }
    static String fit(String text, FontMetrics metrics, int width) {
        if (text == null || width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        String dots = "…";
        if (metrics.stringWidth(dots) > width) return "";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end).stripTrailing() + dots) > width) end--;
        return text.substring(0, end).stripTrailing() + dots;
    }
    static String status(String text) {
        if (text == null) return "";
        if (text.contains("DESTROYED")) return "LOST";
        if (text.contains("DMG")) return text.substring(text.lastIndexOf('•') + 1).trim();
        if (text.contains("BLOCKED")) return "BLOCKED";
        if (text.contains("BURROW")) return "BURROW";
        if (text.contains("PLACED")) return "PLACED";
        if (text.contains("RETALIATION")) return "COUNTER";
        if (text.contains("ATTACK")) return "ATTACK";
        return text;
    }
}
