package com.infiniteconquest.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.infiniteconquest.gui.InfiniteConquestGui.MulliganChoice;
import static com.infiniteconquest.gui.UiTheme.*;

/**
 * The content of the mulligan dialog ("Mulligan — Choose Your Opening Hand"),
 * extracted from {@code InfiniteConquestGui.VisualMulliganDialog} so the layout
 * can be unit-tested without constructing a top-level window.
 *
 * <p>Layout contract (regression guard for the 2026-09-25 bug where the
 * discard tray and the KEEP HAND / REDRAW SELECTED action bar were pushed
 * below the visible area on small or display-scaled screens, with no way to
 * scroll to them):
 * <ul>
 *   <li>The action bar (status line + KEEP HAND / REDRAW SELECTED) is docked
 *       at {@link BorderLayout#SOUTH} of this panel, <em>outside</em> any
 *       scroll pane, so it can never be pushed off-screen.</li>
 *   <li>The trays region sits in a {@link JScrollPane} that scrolls vertically
 *       when the dialog is shorter than the cards, so the discard tray is
 *       always reachable.</li>
 * </ul>
 * The owning dialog caps its own size to the usable screen area; this panel
 * adapts to whatever space it is given.
 */
final class MulliganDialogView extends JPanel {
    private static final Color KEEP_GREEN = new Color(104, 211, 139);
    private static final Color REDRAW_RED = new Color(239, 106, 122);
    private static final Color REDRAW_AMBER = new Color(240, 191, 73);

    private final List<MulliganChoice> choices;
    private final Function<MulliganChoice, JButton> cardFactory;
    private final Set<UUID> discarded = new LinkedHashSet<>();
    private final JPanel handTray = new JPanel();
    private final JPanel discardTray = new JPanel();
    private final JLabel handTitle = new JLabel();
    private final JLabel discardTitle = new JLabel();
    private final JLabel statusLine = new JLabel();
    private final JButton keepButton;
    private final JButton redrawButton;
    private final JPanel footer;
    private final JScrollPane traysScroll;
    private UUID dragging;
    private Point pressPoint;

    /**
     * @param choices       the opening-hand cards to choose from
     * @param cardFactory   builds the visual card button for a choice
     *                      (styling comes from the caller)
     * @param buttonFactory builds the KEEP HAND / REDRAW SELECTED buttons
     *                      from (text, action)
     * @param onKeep        runs after the keep action clears the selection
     * @param onRedraw      runs when the redraw action fires
     */
    MulliganDialogView(List<MulliganChoice> choices,
                       Function<MulliganChoice, JButton> cardFactory,
                       BiFunction<String, Runnable, JButton> buttonFactory,
                       Runnable onKeep, Runnable onRedraw) {
        super(new BorderLayout(8, 8));
        this.choices = List.copyOf(choices);
        this.cardFactory = cardFactory;
        setBackground(PANEL);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        handTray.setLayout(new BoxLayout(handTray, BoxLayout.X_AXIS));
        discardTray.setLayout(new BoxLayout(discardTray, BoxLayout.X_AXIS));
        handTray.setBackground(new Color(24, 72, 58));
        discardTray.setBackground(new Color(78, 42, 50));

        JLabel title = new JLabel("MULLIGAN");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        title.setForeground(REDRAW_AMBER);
        JLabel directions = new JLabel("<html>Review your opening hand. <b>Select up to 3 cards</b> you don't want — "
                + "click a card (or press <b>Space</b> on it), or drag it between trays. "
                + "Each discarded card is <b>replaced with a fresh card from your deck</b>; "
                + "cards you don't select stay in your hand.</html>");
        directions.setForeground(Color.WHITE);
        directions.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(4, 4, 8, 4));
        header.add(title, BorderLayout.NORTH);
        header.add(directions, BorderLayout.CENTER);

        TraysPanel trays = new TraysPanel();
        trays.add(tray(handTitle, "OPENING HAND — KEEPING", handTray, KEEP_GREEN));
        trays.add(tray(discardTitle, "DISCARD & REDRAW", discardTray, REDRAW_RED));
        traysScroll = new JScrollPane(trays,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        traysScroll.setOpaque(false);
        traysScroll.getViewport().setOpaque(false);
        traysScroll.setBorder(null);
        traysScroll.getVerticalScrollBar().setUnitIncrement(48);

        keepButton = buttonFactory.apply("KEEP HAND", () -> {
            discarded.clear();
            rebuild();
            onKeep.run();
        });
        keepButton.setMnemonic(KeyEvent.VK_K);
        keepButton.setToolTipText("Keep your entire opening hand (Alt+K, or Enter)");
        keepButton.getAccessibleContext().setAccessibleDescription(
                "Keep your entire opening hand and start the game");
        redrawButton = buttonFactory.apply("REDRAW SELECTED", onRedraw);
        redrawButton.setMnemonic(KeyEvent.VK_R);
        redrawButton.setToolTipText("Discard the selected cards and draw replacements (Alt+R)");
        redrawButton.getAccessibleContext().setAccessibleDescription(
                "Discard the selected cards and draw one replacement for each");

        statusLine.setForeground(Color.WHITE);
        statusLine.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 4, 0, 4));
        footer.add(statusLine, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(redrawButton);
        buttons.add(keepButton);
        footer.add(buttons, BorderLayout.EAST);

        // The action bar is pinned at SOUTH, outside the scroll pane: it can
        // never be pushed below the fold, no matter how short the dialog is.
        add(header, BorderLayout.NORTH);
        add(traysScroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        rebuild();
    }

    /** Cards the player chose to discard and redraw (a snapshot copy). */
    Set<UUID> discardedIds() { return Set.copyOf(discarded); }

    JButton keepButton() { return keepButton; }

    JButton redrawButton() { return redrawButton; }

    /** The pinned action bar: status line plus KEEP HAND / REDRAW SELECTED. */
    JPanel footer() { return footer; }

    /** The vertically scrollable region holding the header's trays. */
    JScrollPane traysScroll() { return traysScroll; }

    /** Test seam: toggle a card's selected-for-redraw state (refreshes the trays). */
    void toggle(UUID id) {
        if (!discarded.remove(id)) moveToDiscard(id);
        rebuild();
    }

    private JPanel tray(JLabel titleLabel, String name, JPanel cards, Color color) {
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        titleLabel.setForeground(color);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        JPanel result = new JPanel(new BorderLayout(5, 5));
        result.setOpaque(false);
        result.add(header, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(cards, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new LineBorder(color, 2, true));
        result.add(scroll, BorderLayout.CENTER);
        result.getAccessibleContext().setAccessibleName(name);
        return result;
    }

    private void rebuild() {
        handTray.removeAll();
        discardTray.removeAll();
        for (MulliganChoice choice : choices) {
            boolean selected = discarded.contains(choice.id());
            JPanel destination = selected ? discardTray : handTray;
            JButton card = cardFactory.apply(choice);
            card.setFocusPainted(true); // keyboard users must see the focused card
            if (selected) {
                card.setBorder(new CompoundBorder(new LineBorder(REDRAW_RED, 4, true), card.getBorder()));
            }
            String state = selected ? "selected for redraw" : "in your opening hand";
            card.getAccessibleContext().setAccessibleDescription(choice.card().name() + ", "
                    + choice.card().goldCost() + " gold, currently " + state
                    + ". Press Space to " + (selected ? "keep it" : "select it for redraw") + ".");
            // Mouse: click toggles, drag moves between trays. Keyboard: Space/Enter
            // toggle via an explicit binding (no ActionListener, so a mouse click
            // can never double-toggle through button activation).
            card.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragging = choice.id();
                    pressPoint = e.getPoint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fromDiscard = discarded.contains(choice.id());
                    Point handPoint = SwingUtilities.convertPoint(card, e.getPoint(), handTray);
                    Point discardPoint = SwingUtilities.convertPoint(card, e.getPoint(), discardTray);
                    if (!fromDiscard && discardTray.contains(discardPoint)) moveToDiscard(choice.id());
                    else if (fromDiscard && handTray.contains(handPoint)) discarded.remove(choice.id());
                    else if (pressPoint != null && pressPoint.distance(e.getPoint()) < 8) toggle(choice.id());
                    dragging = null;
                    pressPoint = null;
                    rebuild();
                }
            });
            AbstractAction toggleSelection = new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    toggle(choice.id());
                }
            };
            card.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "mulligan-toggle");
            card.getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "mulligan-toggle");
            card.getActionMap().put("mulligan-toggle", toggleSelection);
            destination.add(card);
            destination.add(Box.createHorizontalStrut(8));
        }
        int keeping = choices.size() - discarded.size();
        handTitle.setText("🛡  OPENING HAND — KEEPING (" + keeping + ")");
        discardTitle.setText("↻  DISCARD & REDRAW (" + discarded.size() + "/3)");
        statusLine.setText("Discarding " + discarded.size() + " of up to 3  •  Keeping " + keeping);
        redrawButton.setText("REDRAW SELECTED" + (discarded.isEmpty() ? "" : " (" + discarded.size() + ")"));
        redrawButton.setEnabled(!discarded.isEmpty());
        handTray.revalidate();
        discardTray.revalidate();
        handTray.repaint();
        discardTray.repaint();
    }

    private void moveToDiscard(UUID id) {
        if (discarded.size() >= 3 && !discarded.contains(id)) {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (HeadlessException ignored) {
                // No audio device in headless test runs; the cap still applies.
            }
            return;
        }
        discarded.add(id);
    }

    /**
     * The two-tray stack. Tracks the viewport width so the outer scroll pane
     * only ever scrolls vertically; each tray scrolls its own cards
     * horizontally.
     */
    private static final class TraysPanel extends JPanel implements Scrollable {
        TraysPanel() {
            super(new GridLayout(2, 1, 0, 10));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 48;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 240;
        }

        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
