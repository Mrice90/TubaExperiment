package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Who made the storm. */
final class CreditsScreen extends SubScreen {
    CreditsScreen(GameShell shell) {
        super(shell, "Credits");
    }

    @Override
    protected JComponent buildContent() {
        JTextArea text = new JTextArea("""
                INFINITE CONQUEST — HEX & ALLIES
                """ + GameVersion.displayName() + """

                Created by Mathew Rice

                Design, code, and systems — Mathew Rice
                Built with Java and Swing
                Card paintings generated for this project

                Zeus and Poseidon open the alpha.
                Four more pantheons wait beyond the horizon.

                Thanks for playing.""");
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        text.setForeground(new Color(220, 226, 236));
        text.setBorder(new EmptyBorder(30, 44, 30, 44));

        JPanel frame = new JPanel(new BorderLayout());
        frame.setOpaque(true);
        frame.setBackground(new Color(14, 20, 34, 225));
        frame.setBorder(BorderFactory.createLineBorder(new Color(240, 191, 73, 90), 1));
        frame.add(text, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(560, 420));
        return frame;
    }
}
