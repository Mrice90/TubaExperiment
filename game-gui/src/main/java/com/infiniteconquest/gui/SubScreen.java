package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Shared chrome for the shell's secondary screens: painted storm backdrop,
 * a header with a back button, and a centered content column.
 */
abstract class SubScreen extends JPanel implements ShellScreen {
    private final GameShell shell;
    private final JPanel centerWrapper = new JPanel(new GridBagLayout());
    private BufferedImage backdrop;
    private boolean contentBuilt;

    SubScreen(GameShell shell, String title) {
        this.shell = shell;
        setLayout(new BorderLayout());
        setBackground(new Color(10, 15, 28));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(18, 26, 6, 26));
        ShellUi.MenuButton back = new ShellUi.MenuButton("‹ Back");
        back.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        back.addActionListener(e -> shell.showTitle());
        back.setPreferredSize(new Dimension(130, 44));
        header.add(back, BorderLayout.WEST);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SERIF, Font.BOLD, 34));
        titleLabel.setForeground(new Color(240, 224, 178));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(Box.createHorizontalStrut(130), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        centerWrapper.setOpaque(false);
        add(centerWrapper, BorderLayout.CENTER);
    }

    /**
     * The screen's main content, placed centered. Built lazily on first show so
     * subclass constructors have finished assigning their fields.
     */
    protected abstract JComponent buildContent();

    /**
     * GridBagConstraints used to place the content in the centered wrapper.
     * The default centers the content at its preferred size.
     *
     * <p>GridBagLayout is all-or-nothing about sizing: when the content's
     * preferred size doesn't fit the wrapper in <em>either</em> dimension, the
     * whole layout falls back to <em>minimum</em> sizes in both dimensions —
     * and a JScrollPane's stock minimum size is a ~21x5 sliver, so a menu that
     * grows taller than the window vanishes instead of scrolling. Screens
     * whose content is a scroll pane should override this to fill the
     * available space (with a minimum size that keeps the content usable).
     */
    protected GridBagConstraints contentConstraints() {
        return new GridBagConstraints();
    }

    @Override
    public void onShow() {
        if (backdrop == null) backdrop = CardArtFactory.worldBackdrop();
        if (!contentBuilt) {
            contentBuilt = true;
            centerWrapper.add(buildContent(), contentConstraints());
            revalidate();
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        int w = getWidth(), h = getHeight();
        ShellUi.paintBackdrop(g, w, h);
        ShellUi.paintBackdropImage(g, backdrop, w, h, 0.85f);
        ShellUi.paintVignette(g, w, h);
        g.dispose();
    }

    /** A labeled settings row: label on the left, control on the right. */
    static JPanel row(String labelText, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        label.setForeground(new Color(220, 226, 236));
        label.setPreferredSize(new Dimension(210, 40));
        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(560, 52));
        row.setAlignmentX(CENTER_ALIGNMENT);
        return row;
    }

    /** Dark card panel that holds a settings form. */
    static JPanel card() {
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(26, 40, 26, 40));
        return card;
    }
}
