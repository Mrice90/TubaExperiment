package com.infiniteconquest.gui;

import com.infiniteconquest.cli.DeckBuildStore;
import com.infiniteconquest.core.DeckBuild;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The desktop front end: loading screen → title menu → battle.
 * Owns the window, the settings, and the one shared {@link GameContext}.
 */
public final class GameShell {
    private final GameSettings settings;
    private JFrame frame;
    private ScreenManager screens;
    private GameContext context;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameShell(GameSettings.load()).start());
    }

    GameShell(GameSettings settings) {
        this.settings = settings;
    }

    void start() {
        frame = new JFrame("Infinite Conquest");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { requestExit(); }
        });
        frame.setMinimumSize(new Dimension(1100, 640));
        applyWindowSettings();
        frame.setLocationRelativeTo(null);
        screens = new ScreenManager(frame, settings);

        LoadingScreen loading = new LoadingScreen();
        screens.show(loading, false);
        frame.setVisible(true);
        loading.run(initTasks(), settings, () -> screens.show(new TitleScreen(this, settings)));
    }

    private List<LoadingScreen.InitTask> initTasks() {
        List<LoadingScreen.InitTask> tasks = new ArrayList<>();
        tasks.add(new LoadingScreen.InitTask("Reading the card catalog", () -> {
            context = GameContext.load(settings, label -> { });
        }));
        return tasks;
    }

    /** Applies display + sound settings to the shell window. Call after load/save. */
    void applySettings() {
        SoundEffects.setMuted(!settings.soundEnabled);
        SoundEffects.setVolume(settings.volume / 100.0);
        if (frame == null) return;
        boolean wasVisible = frame.isVisible();
        frame.dispose();
        frame.setUndecorated(false);
        applyWindowSettings();
        frame.setLocationRelativeTo(null);
        if (wasVisible) frame.setVisible(true);
    }

    private void applyWindowSettings() {
        if (settings.fullscreen) {
            frame.setUndecorated(true);
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (device.isFullScreenSupported()) {
                frame.setVisible(true);
                device.setFullScreenWindow(frame);
                return;
            }
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            frame.setSize(settings.windowSize());
            frame.setExtendedState(JFrame.NORMAL);
        }
    }

    // ---- Navigation -------------------------------------------------------

    void showTitle() {
        frame.setVisible(true);
        screens.show(new TitleScreen(this, settings));
    }

    void showSettings() {
        screens.show(new SettingsScreen(this, settings));
    }

    void showCredits() {
        screens.show(new CreditsScreen(this));
    }

    void showHowToPlay() {
        screens.show(new HowToPlayScreen(this, context.pool));
    }

    /** Play: a beat of loading theatre, then the battle window takes the stage. */
    void play() {
        LoadingScreen launching = new LoadingScreen();
        screens.show(launching, true);
        List<LoadingScreen.InitTask> tasks = List.of(
                new LoadingScreen.InitTask("Mustering your army", () -> {}),
                new LoadingScreen.InitTask("Waking the rival god", () -> Thread.sleep(300)));
        launching.run(tasks, settings, () -> {
            InfiniteConquestGui battle = new InfiniteConquestGui(context, this::showTitle);
            battle.setVisible(true);
            frame.setVisible(false);
        });
    }

    void openDeckBuilder() {
        DeckBuild edited = new DeckBuilderDialog(frame, context.pool, context.capitals,
                context.buildForFaction("ZEUS")).choose();
        if (edited == null) return;
        try {
            Path path = context.deckDirectory.resolve(
                    edited.primaryFaction().toLowerCase(Locale.ROOT) + ".json");
            context.buildStore.save(path, edited);
            context.savedDecks.put(edited.primaryFaction(), edited);
            JOptionPane.showMessageDialog(frame,
                    "Deck saved. Start a match with " + edited.primaryFaction() + " to play it.\n" + path,
                    "Deck Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException failure) {
            JOptionPane.showMessageDialog(frame, failure.getMessage(),
                    "Deck Not Saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Confirm, then quit. Called from menus, the Esc key, and the window chrome. */
    void requestExit() {
        int answer = JOptionPane.showConfirmDialog(frame,
                "Leave Infinite Conquest?", "Exit",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            frame.dispose();
            System.exit(0);
        }
    }
}
