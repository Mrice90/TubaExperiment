package com.infiniteconquest.gui;

import com.infiniteconquest.cli.PrototypeCardPool;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.TerrainRules;
import com.infiniteconquest.data.Keyword;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The rulebook: the battle, the terrain keywords (described from real card data),
 * and the desktop controls.
 */
final class HowToPlayScreen extends SubScreen {
    private final PrototypeCardPool pool;

    HowToPlayScreen(GameShell shell, PrototypeCardPool pool) {
        super(shell, "How to Play");
        this.pool = pool;
    }

    @Override
    protected JComponent buildContent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        tabs.setBackground(new Color(20, 28, 44));
        tabs.setForeground(new Color(232, 236, 244));
        tabs.addTab("The Battle", scroll(battleText()));
        tabs.addTab("Keywords", scroll(keywordText()));
        tabs.addTab("Controls", scroll(controlsText()));
        tabs.setPreferredSize(new Dimension(640, 440));
        return tabs;
    }

    private static JScrollPane scroll(JTextArea text) {
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(14, 20, 34, 235));
        return scroll;
    }

    private static JTextArea styledArea() {
        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        text.setForeground(new Color(220, 226, 236));
        text.setBorder(new EmptyBorder(22, 28, 22, 28));
        return text;
    }

    private JTextArea battleText() {
        JTextArea text = styledArea();
        text.setText("""
                THE BATTLE

                Two gods. One mortal world. Destroy the enemy Capital to win.

                YOUR ARMY
                • You command Zeus or Poseidon — each with its own Capital, cards, and tempo.
                • Ally decks let both pantheons fight side by side in one deck.
                • Your Capital's passive shapes your whole strategy. Build around it.

                THE TURN
                1. Draw a card and collect gold. Gold carries between turns — saving up for an
                   apex card wins games.
                2. Play Lands and Structures to shape the board, Characters to hold it.
                3. Move and attack with your Characters. Ranged units strike from afar;
                   melee units get up close.
                4. Cast Spells at the right moment — a single spell can break a siege.
                5. End your turn and weather the storm.

                THE BOARD
                • Hexes, height, and line of sight matter. High Ground sees farther.
                • Turrets punish anything that wanders into range. Scout first.
                • Watchtowers extend your reach; Sanctuaries mend your wounded.
                • Protect your Capital. Lose it, and the world falls with it.""");
        return text;
    }

    private JTextArea keywordText() {
        JTextArea text = styledArea();
        StringBuilder body = new StringBuilder("TERRAIN KEYWORDS\n\n");
        body.append("Lands and Structures carry keywords. Here is what each one does,\n");
        body.append("straight from the cards in this alpha:\n\n");
        List<Keyword> keywords = new ArrayList<>();
        for (CardDefinition card : pool.cards())
            for (Keyword keyword : card.keywords())
                if (!keywords.contains(keyword)) keywords.add(keyword);
        keywords.sort(Comparator.comparing(Keyword::name));
        for (Keyword keyword : keywords) {
            CardDefinition sample = pool.cards().stream()
                    .filter(card -> card.keywords().contains(keyword)).findFirst().orElse(null);
            String description = sample == null ? "" : TerrainRules.describe(sample, keyword);
            body.append("• ").append(pretty(keyword)).append(" — ").append(description).append("\n\n");
        }
        text.setText(body.toString().trim());
        return text;
    }

    private static String pretty(Keyword keyword) {
        String[] words = keyword.name().toLowerCase().split("_");
        StringBuilder pretty = new StringBuilder();
        for (String word : words) {
            if (!pretty.isEmpty()) pretty.append(' ');
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return pretty.toString();
    }

    private JTextArea controlsText() {
        JTextArea text = styledArea();
        text.setText("""
                CONTROLS

                Title screen
                • ↑ / ↓ — move through the menu      Enter — choose
                • F1 — open this guide                     Esc — exit the game

                In battle
                • Click a card or unit, then choose a legal action.
                • Ctrl+D — deck builder        Ctrl+N — new match
                • F11 — fullscreen              F2 — legal actions
                • F3 — action history           F4 — board view
                • Esc — close panels / shrink the hand tray

                Your settings — window size, sound, animations — live under
                Settings on the title screen and are remembered between visits.""");
        return text;
    }
}
