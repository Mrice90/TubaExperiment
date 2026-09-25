package com.infiniteconquest.gui;

import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.gui.InfiniteConquestGui.MulliganChoice;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the 2026-09-25 mulligan dialog bug: on small or
 * display-scaled screens the discard tray and the KEEP HAND / REDRAW SELECTED
 * action bar were pushed below the visible area of the fixed 1300x760 dialog
 * with no way to scroll to them, so the player could never confirm.
 *
 * <p>The layout contract under test: the action bar is pinned at
 * {@link BorderLayout#SOUTH} of the dialog content, outside any
 * {@link JScrollPane}, and the trays region scrolls vertically when space is
 * tight. Everything here constructs plain Swing components, so it runs
 * headless like the other GUI tests.
 */
class MulliganDialogViewTest {
    private static List<MulliganChoice> choices() {
        List<CardDefinition> deck = new DemoMatchFactory().demoDeck();
        return deck.stream().limit(8)
                .map(def -> new MulliganChoice(UUID.randomUUID(), def))
                .collect(Collectors.toList());
    }

    private static MulliganDialogView view(List<MulliganChoice> choices,
                                           Runnable onKeep, Runnable onRedraw) {
        return new MulliganDialogView(choices,
                choice -> new JButton(choice.card().name()),
                (text, action) -> {
                    JButton button = new JButton(text);
                    button.addActionListener(e -> action.run());
                    return button;
                },
                onKeep, onRedraw);
    }

    private static MulliganDialogView view(List<MulliganChoice> choices) {
        return view(choices, () -> {}, () -> {});
    }

    /** Walks up the containment hierarchy looking for a scroll pane. */
    private static boolean insideScrollPane(Component component, Container stopAt) {
        for (Container parent = component.getParent();
             parent != null && parent != stopAt;
             parent = parent.getParent()) {
            if (parent instanceof JScrollPane) return true;
        }
        return false;
    }

    @Test
    void actionBarIsPinnedOutsideAnyScrollPane() {
        List<MulliganChoice> choices = choices();
        MulliganDialogView dialog = view(choices);

        // The footer is docked at SOUTH of the dialog content itself...
        BorderLayout layout = (BorderLayout) dialog.getLayout();
        assertSame(dialog.footer(), layout.getLayoutComponent(BorderLayout.SOUTH),
                "the action bar must be pinned at the bottom of the dialog content");

        // ...and neither confirm control sits inside a scroll pane, so the
        // action bar can never be scrolled or pushed out of reach.
        assertFalse(insideScrollPane(dialog.keepButton(), dialog),
                "KEEP HAND must not live inside a scroll pane");
        assertFalse(insideScrollPane(dialog.redrawButton(), dialog),
                "REDRAW SELECTED must not live inside a scroll pane");
    }

    @Test
    void confirmControlIsVisibleAndEnabled() {
        MulliganDialogView dialog = view(choices());

        JButton keep = dialog.keepButton();
        assertEquals("KEEP HAND", keep.getText());
        assertTrue(keep.isVisible(), "KEEP HAND should be visible");
        assertTrue(keep.isEnabled(), "KEEP HAND should always be enabled");
    }

    @Test
    void traysScrollVerticallyWhenSpaceIsTight() {
        MulliganDialogView dialog = view(choices());

        JScrollPane traysScroll = dialog.traysScroll();
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                traysScroll.getVerticalScrollBarPolicy(),
                "the trays region must scroll vertically when the dialog is short");

        // Simulate Mathew's cramped screen: squeeze the dialog short and lay
        // it out. The pinned footer must stay inside the dialog bounds while
        // the trays region absorbs the squeeze via its scrollbar.
        dialog.setSize(900, 420);
        dialog.doLayout();
        Rectangle footerBounds = dialog.footer().getBounds();
        assertTrue(footerBounds.y + footerBounds.height <= dialog.getHeight(),
                "the action bar must stay inside the dialog, not pushed below the fold");
        assertTrue(traysScroll.getVerticalScrollBar().isVisible(),
                "the trays scrollbar should engage when the dialog is too short for both trays");
    }

    @Test
    void redrawEnablesOnlyWhenCardsAreSelected() {
        List<MulliganChoice> choices = choices();
        MulliganDialogView dialog = view(choices);
        UUID first = choices.get(0).id();

        assertFalse(dialog.redrawButton().isEnabled(),
                "REDRAW SELECTED starts disabled with nothing selected");

        dialog.toggle(first);
        assertTrue(dialog.redrawButton().isEnabled(),
                "REDRAW SELECTED enables once a card is selected");
        assertEquals("REDRAW SELECTED (1)", dialog.redrawButton().getText());
        assertTrue(dialog.discardedIds().contains(first));

        dialog.toggle(first);
        assertFalse(dialog.redrawButton().isEnabled(),
                "REDRAW SELECTED disables again when the selection is cleared");
        assertTrue(dialog.discardedIds().isEmpty());
    }

    @Test
    void keepClearsSelectionAndFiresItsCallback() {
        List<MulliganChoice> choices = choices();
        AtomicBoolean kept = new AtomicBoolean(false);
        MulliganDialogView dialog = view(choices, () -> kept.set(true), () -> {});

        dialog.toggle(choices.get(0).id());
        dialog.toggle(choices.get(1).id());
        assertEquals(2, dialog.discardedIds().size());

        dialog.keepButton().doClick();

        assertTrue(kept.get(), "the keep callback should fire");
        assertTrue(dialog.discardedIds().isEmpty(),
                "keeping the hand clears the discard selection");
    }

    @Test
    void redrawFiresItsCallback() {
        AtomicBoolean redrawn = new AtomicBoolean(false);
        List<MulliganChoice> choices = choices();
        MulliganDialogView dialog = view(choices, () -> {}, () -> redrawn.set(true));

        dialog.toggle(choices.get(0).id());
        dialog.redrawButton().doClick();

        assertTrue(redrawn.get(), "the redraw callback should fire");
    }
}
