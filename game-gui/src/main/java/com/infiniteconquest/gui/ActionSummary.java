package com.infiniteconquest.gui;

import java.util.Locale;

/** Presentation-only labels; the full chronological history retains the original detail. */
final class ActionSummary {
    private ActionSummary() { }

    static String label(String detail) {
        String text = detail.toLowerCase(Locale.ROOT);
        if (text.startsWith("reaction")) return "↩ Reaction";
        if (text.contains("gp spent")) return "− Spend";
        if (text.contains("income")) return "+ Income";
        if (text.contains("destroyed")) return "× Destroyed";
        if (text.contains("damage")) return "! Damage";
        if (text.startsWith("move") || text.startsWith("blink")) return "→ Move";
        if (text.startsWith("attack") || text.contains("opportunity")) return "! Attack";
        if (text.startsWith("cast")) return "* Spell";
        if (text.startsWith("place") || text.startsWith("burrow")) return "+ Deploy";
        if (text.contains("turn")) return "↻ Turn";
        return "› Event";
    }
}
