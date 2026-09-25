package com.infiniteconquest.gui;

import com.infiniteconquest.cli.DemoMatchFactory;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the 2026-09-25 bug where the Multiplayer screen showed
 * only its title and back button: the menu's JScrollPane is added to
 * SubScreen's centered GridBagLayout wrapper, and GridBagLayout falls back to
 * *minimum* sizes in both dimensions when the content's preferred size doesn't
 * fit the wrapper in either one. A JScrollPane's stock minimum size is a ~21x5
 * sliver, so once the menu grew taller than the window the whole panel
 * vanished instead of scrolling.
 *
 * <p>The guard: after layout, the menu's scroll pane must actually fill the
 * available space (not collapse), and every menu option button must be
 * present, visible, and laid out with a real size.
 */
class MultiplayerScreenTest {
    /** Every option button the multiplayer menu must offer. */
    private static final List<String> EXPECTED_OPTIONS = List.of(
            "Host Online Game", "Join by Link",
            "Refresh", "Join Selected", "Join by Code",
            "Find Match",
            "Host Game", "Join Game");

    @Test
    void menuShowsEveryMultiplayerOption() {
        MultiplayerScreen screen = newScreen();
        screen.onShow();
        screen.setSize(1500, 950);
        layoutAll(screen);

        JScrollPane menu = contentScrollPane(screen);
        assertTrue(menu.getWidth() > 500 && menu.getHeight() > 500,
                "the menu scroll pane must fill the available space, was "
                        + menu.getWidth() + "x" + menu.getHeight());

        Set<String> buttons = buttonTexts(screen);
        for (String option : EXPECTED_OPTIONS) {
            assertTrue(buttons.contains(option),
                    "multiplayer menu should offer '" + option + "'");
        }
        for (JButton button : allButtons(screen)) {
            if (EXPECTED_OPTIONS.contains(button.getText())) {
                assertTrue(button.isVisible(),
                        "'" + button.getText() + "' should be visible");
                assertTrue(button.getWidth() > 0 && button.getHeight() > 0,
                        "'" + button.getText() + "' should be laid out with a real size");
            }
        }
        screen.onHide();
    }

    @Test
    void menuSurvivesShortWindow() {
        // Shorter than the menu's preferred height: the pane must fill the
        // space and scroll, never collapse to a sliver.
        MultiplayerScreen screen = newScreen();
        screen.onShow();
        screen.setSize(1500, 700);
        layoutAll(screen);

        JScrollPane menu = contentScrollPane(screen);
        assertTrue(menu.getWidth() > 500 && menu.getHeight() > 400,
                "the menu must stay usable in a short window, was "
                        + menu.getWidth() + "x" + menu.getHeight());
        assertTrue(buttonTexts(screen).containsAll(EXPECTED_OPTIONS),
                "every option must still be built in a short window");
        screen.onHide();
    }

    /** The menu's scroll pane: the JScrollPane added to the screen's center wrapper. */
    private static JScrollPane contentScrollPane(MultiplayerScreen screen) {
        Component center = ((BorderLayout) screen.getLayout())
                .getLayoutComponent(screen, BorderLayout.CENTER);
        assertTrue(center instanceof Container, "SubScreen should keep its center wrapper");
        for (Component child : ((Container) center).getComponents()) {
            if (child instanceof JScrollPane scroll) return scroll;
        }
        fail("multiplayer content scroll pane not found in the center wrapper");
        return null;
    }

    private static Set<String> buttonTexts(Container container) {
        Set<String> texts = new HashSet<>();
        for (JButton button : allButtons(container)) texts.add(button.getText());
        return texts;
    }

    private static java.util.List<JButton> allButtons(Container container) {
        java.util.List<JButton> buttons = new java.util.ArrayList<>();
        collectButtons(container, buttons);
        return buttons;
    }

    private static void collectButtons(Container container, java.util.List<JButton> out) {
        if (container instanceof JButton button) out.add(button);
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) collectButtons(inner, out);
        }
    }

    /**
     * Recursive layout: headless {@code validate()} is a no-op without a
     * native peer, and one level of {@code doLayout()} leaves every child at
     * 0x0, so walk the whole tree like a real window validation would.
     */
    private static void layoutAll(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) layoutAll(inner);
        }
    }

    private static MultiplayerScreen newScreen() {
        GameSettings settings = GameSettings.load();
        GameShell shell = new GameShell(settings);
        try {
            Constructor<GameContext> ctor = GameContext.class.getDeclaredConstructor(
                    DemoMatchFactory.class, GameSettings.class, Path.class, java.util.Map.class);
            ctor.setAccessible(true);
            GameContext context = ctor.newInstance(new DemoMatchFactory(), settings,
                    Path.of(System.getProperty("java.io.tmpdir")), new HashMap<>());
            return new MultiplayerScreen(shell, settings, context);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not build a test GameContext", e);
        }
    }
}
