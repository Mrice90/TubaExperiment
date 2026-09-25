package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ISSUE 2 regression: the settings form used to be one long column with no
 * scroll pane, so on Windows the "Save & Apply" / "Defaults" buttons could end
 * up half-hidden behind the taskbar with no way to scroll to them. The form
 * rows now live in a JScrollPane and the button bar is pinned outside it, so
 * the buttons stay reachable at 1280x720 and the default window size.
 */
class SettingsScreenLayoutTest {
    /**
     * Headless, no native peer drives validation, so drive layout manually:
     * doLayout() only lays out one level, hence the recursion.
     */
    private static void layoutAll(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container inner) layoutAll(inner);
    }

    private static SettingsScreen shownScreen(int width, int height) {
        SettingsScreen screen = new SettingsScreen(new GameShell(GameSettings.load()), GameSettings.load());
        screen.onShow();
        screen.setSize(width, height);
        layoutAll(screen);
        return screen;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container)
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        return null;
    }

    private static JButton findButton(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) return button;
        if (root instanceof Container container)
            for (Component child : container.getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        return null;
    }

    @Test
    void settingsFormIsWrappedInAScrollPane() {
        SettingsScreen screen = shownScreen(1280, 720);
        assertNotNull(find(screen, JScrollPane.class),
                "settings form rows must be wrapped in a scroll pane so overflow can be scrolled");
        screen.onHide();
    }

    @Test
    void buttonBarIsPinnedOutsideTheScrollableForm() {
        SettingsScreen screen = shownScreen(1280, 720);
        JScrollPane scroller = find(screen, JScrollPane.class);
        assertNotNull(scroller, "settings form rows must be wrapped in a scroll pane");
        JButton save = findButton(screen, "Save & Apply");
        assertNotNull(save, "Save & Apply button must exist");
        assertNotNull(findButton(screen, "Defaults"), "Defaults button must exist");
        // Pinned: the button bar must not live inside the scroll pane's viewport,
        // otherwise it scrolls away exactly like the old overflow bug.
        assertFalse(SwingUtilities.isDescendingFrom(save, scroller.getViewport()),
                "Save & Apply must be pinned outside the scrollable form");
        screen.onHide();
    }

    @Test
    void saveButtonFullyVisibleAt1280x720() {
        assertSaveButtonVisible(1280, 720);
    }

    @Test
    void saveButtonFullyVisibleAt1500x980() {
        assertSaveButtonVisible(1500, 980);
    }

    private static void assertSaveButtonVisible(int width, int height) {
        SettingsScreen screen = shownScreen(width, height);
        JButton save = findButton(screen, "Save & Apply");
        assertNotNull(save, "Save & Apply button must exist");
        assertTrue(save.getWidth() > 0 && save.getHeight() > 0,
                "Save & Apply must be laid out, was " + save.getBounds());
        Rectangle bounds = SwingUtilities.convertRectangle(save.getParent(), save.getBounds(), screen);
        Rectangle window = new Rectangle(0, 0, width, height);
        assertTrue(window.contains(bounds),
                "Save & Apply must be fully inside the " + width + "x" + height
                        + " window, was " + bounds);
        screen.onHide();
    }
}
