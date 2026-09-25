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
        tabs.addTab("Goal", scroll(goalText()));
        tabs.addTab("Your Turn", scroll(turnText()));
        tabs.addTab("Gold & Cards", scroll(economyText()));
        tabs.addTab("Battlefield", scroll(battlefieldText()));
        tabs.addTab("Abilities & Reactions", scroll(abilitiesText()));
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

    private JTextArea goalText() {
        JTextArea text = styledArea();
        text.setText("""
                THE GOAL

                Two gods. One mortal world. Destroy the enemy Capital to win.
                That is the whole game: if your Capital falls, you lose — no matter
                how many lands, structures, or soldiers you still have standing.
                And the reverse is true too: an empty battlefield means nothing
                while your Capital still stands.

                Protect your Capital. Kill theirs.

                BEFORE THE FIRST TURN
                • The initiative coin is tossed. The winner decides who goes first.
                  Going second is not all bad: the second player starts with
                  1 bonus gold and draws a 6th card.
                • You draw an opening hand of 5 cards (6 if you go second).
                • Mulligan: unhappy with your hand? Discard up to 3 cards and
                  draw replacements before the battle begins.
                • Each side places its Capital on the board. Where you plant it
                  shapes the whole war — a forward Capital pressures early, a
                  sheltered one buys time.""");
        return text;
    }

    private JTextArea turnText() {
        JTextArea text = styledArea();
        text.setText("""
                YOUR TURN, STEP BY STEP

                Every turn follows the same rhythm:

                1. DAWN — Collect and draw.
                   Gain gold: 1 from your Capital (only while you control it),
                   plus the income of every Land and Structure you own. Then draw
                   a card. (No draw on the very first turn of the game.) Gold is
                   never lost — unspent gold carries forward, so saving up for a
                   game-breaking play is a legitimate strategy.

                2. COMMAND — Spend your gold and your units.
                   • Play cards from your hand (see Gold & Cards).
                   • Move your Characters and attack with them.
                   • Fire off activated abilities.
                   • Cast Spells at the moment they hurt most.

                3. DUSK — End your turn.
                   Your opponent now gets their dawn. Weather the storm.

                THE EXHAUSTION CLOCK
                Decks are finite. When yours runs out, every dawn that fails to
                draw deals 1 damage to EACH of your permanents — including your
                own Capital. A long game can kill a Capital with no enemy in
                sight. Do not let the clock do your opponent's work.""");
        return text;
    }

    private JTextArea economyText() {
        JTextArea text = styledArea();
        text.setText("""
                GOLD & CARDS

                Gold (GP) pays for everything. Each card shows its cost; if you
                cannot pay, you cannot play it.

                THE FIVE CARD TYPES
                • CAPITAL — Your seat of power. Starts on the board, generates
                  1 gold a turn, and carries a passive ability that shapes your
                  entire strategy. Build around it. Lose it and the game ends.
                • LAND — Territory. Play at most ONE land per turn. Lands are the
                  economy: most generate gold every dawn, and many carry terrain
                  keywords that reshape the hexes around them.
                • STRUCTURE — Fortifications and engines: turrets that punish
                  anything wandering into range, watchtowers that extend your
                  reach, sanctuaries that mend your wounded. Unlike lands, you
                  may play several structures a turn if you can afford them.
                • CHARACTER — Your soldiers. They move, they attack, they hold
                  ground. Each has Attack, Defense (hit points), Movement, and
                  Range. Ranged units strike from afar; melee units get close —
                  but anything that attacks a defender in range takes
                  retaliation damage back.
                • SPELL — Instant effects: damage, healing, movement tricks,
                  battlefield-wide events. Cast them on your turn — or hold them
                  for a reaction (see Abilities & Reactions).

                PLAYING LANDS
                Lands enter on empty hexes near your territory. One per turn, so
                every placement is a commitment: economy now, or position for
                the push later?""");
        return text;
    }

    private JTextArea battlefieldText() {
        JTextArea text = styledArea();
        text.setText("""
                THE BATTLEFIELD

                The board is a field of hexes. Position is everything.

                DEPLOYING
                Characters deploy onto a friendly stack or within one hex of any
                friendly permanent. Your army grows outward from the ground you
                already hold — which is why forward lands and structures are
                worth fighting for.

                MOVEMENT & ATTACKING
                • A Character may move up to its Movement in hexes, then attack —
                  or attack without moving. It cannot do either twice.
                • Attacks target a hex in Range. Melee fighters (Range 1) must
                  stand adjacent; archers and siege engines reach farther.
                • Striking a Character provokes retaliation if the defender can
                  reach back. Striking a Land, Structure, or Capital never does —
                  buildings do not punch back.
                • Characters and damaged permanents show their wounds. A unit at
                  0 defense is destroyed; a Capital at 0 hit points ends the game.

                TERRAIN
                Hexes have height and terrain. High ground sees farther and
                strikes first in the ways that matter; turrets punish anything
                that wanders into range. Scout before you march.

                READING THE BOARD
                • A golden chevron marks a Character that can still attack.
                • A glowing sigil marks a Land or Structure with an unused
                  activated ability.
                • Your Capital is always the brightest banner on your side of
                  the field. Keep an eye on the enemy's.""");
        return text;
    }

    private JTextArea abilitiesText() {
        JTextArea text = styledArea();
        text.setText("""
                ABILITIES & REACTIONS

                ACTIVATED ABILITIES
                Some Lands and Structures carry abilities you fire yourself.
                Rules of the craft:
                • Only the TOP card of a stack can use its ability.
                • Each ability fires once per turn and costs gold.
                • Look for the glowing sigil: it means the ability is ready.

                CAPITAL PASSIVES
                Every Capital radiates a passive effect — Zeus rewards the
                aggressive, Poseidon the patient (read your Capital's card).
                Passives trigger on their own; your job is to build a deck and
                a board that feed them.

                KEYWORDS
                Cards also carry passive keywords (Flying, Siege, Guardian, and
                more). Siege doubles damage against Capitals and Structures —
                the premier Capital-killing keyword. Full definitions live under
                the Keywords tab.

                REACTIONS
                The battle is not strictly turn-locked. When your opponent casts
                a spell or launches an attack, you may get a reaction window: a
                short chance to answer with a spell of your own before their
                play resolves. Online, you will see "Opponent is reacting…"
                while you wait. Keep gold and an answer in hand — the player who
                reacts well wins the close games.""");
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
