package com.infiniteconquest.gui;

import com.infiniteconquest.cli.ActionHints;
import com.infiniteconquest.cli.CommandProcessor;
import com.infiniteconquest.cli.DemoMatchFactory;
import com.infiniteconquest.cli.FactionDecks;
import com.infiniteconquest.core.BoardGeometry;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.net.Redactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The network animation seam: confirmed server snapshots rebuild into
 * client states whose before/after diff drives the same
 * {@link PresentationSnapshot} pipeline as local play, and the reduced
 * animation setting collapses it exactly like it does locally.
 */
class NetPresentationTest {
    private final DemoMatchFactory factory = new DemoMatchFactory();

    private DeckBuild deck(String faction) {
        List<CardDefinition> cards = new FactionDecks(factory.pool()).starter(faction);
        return new DeckBuild(faction + " Test", faction, null,
                factory.capitals().forFaction(faction).get(0), cards);
    }

    private Function<String, CardDefinition> definitions() {
        return id -> {
            try { return factory.pool().require(id); }
            catch (RuntimeException e) {
                try { return factory.capitals().require(id); }
                catch (RuntimeException e2) { return null; }
            }
        };
    }

    @Test void confirmedSnapshotsDrivePresentationChanges() {
        GameState authoritative = factory.create(1234L, deck("ZEUS"), deck("POSEIDON"),
                new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        authoritative.mulligan(0, List.of());
        authoritative.mulligan(1, List.of());

        // The client is the active player: only then does the redacted hand
        // contain the real cards the hints (and the command) refer to.
        // Redacting for the idle player fills their hand with hidden
        // placeholders, so a hint-derived command may name a different real
        // card and silently fail to play.
        int viewer = authoritative.activePlayer();
        GameState before = GameState.fromSnapshot(Redactor.redact(authoritative, viewer), definitions());
        String command = new ActionHints().forActivePlayer(before, new GameEngine()).stream()
                .filter(h -> h.startsWith("play ") || h.startsWith("burrow "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no playable card in opening hand"));
        new CommandProcessor(authoritative).execute(command);
        GameState after = GameState.fromSnapshot(Redactor.redact(authoritative, viewer), definitions());

        PresentationSnapshot.Frame frame = PresentationSnapshot.capture(before);
        PresentationSnapshot resolution = PresentationSnapshot.between(command, frame, after);
        assertFalse(resolution.changes().isEmpty());
        assertTrue(resolution.changes().stream()
                .anyMatch(change -> change.change() == PresentationSnapshot.Change.ENTERED_BATTLEFIELD),
                "playing a card must animate as entering the battlefield, got: " + resolution.changes());
    }

    @Test void reducedModeCollapsesNetPresentations() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ic-net-fx");
        System.setProperty("user.home", fakeHome.toString());
        try {
            GameSettings full = GameSettings.load();
            assertFalse(Fx.reduced(full));
            assertTrue(Fx.durationNanos(1200, full) > 1);

            GameSettings reduced = GameSettings.load();
            reduced.animationMode = GameSettings.AnimationMode.REDUCED;
            assertTrue(Fx.reduced(reduced));
            assertEquals(1L, Fx.durationNanos(1200, reduced),
                    "reduced mode must collapse motion to a single frame");
            assertFalse(Fx.particles(reduced));
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test void snapshotRebuildIsStableAcrossFrames() {
        GameState authoritative = factory.create(99L, deck("ZEUS"), deck("POSEIDON"),
                new BoardPosition(1, 0), new BoardPosition(2, 5), BoardGeometry.HEX);
        authoritative.mulligan(0, List.of());
        authoritative.mulligan(1, List.of());
        GameSnapshot snapshot = Redactor.redact(authoritative, 1);
        GameState first = GameState.fromSnapshot(snapshot, definitions());
        GameState second = GameState.fromSnapshot(snapshot, definitions());
        PresentationSnapshot.Frame frame = PresentationSnapshot.capture(first);
        PresentationSnapshot resolution = PresentationSnapshot.between("end", frame, second);
        assertTrue(resolution.changes().stream()
                .allMatch(change -> change.change() == PresentationSnapshot.Change.UNCHANGED),
                "rebuilding the same snapshot must not phantom-animate, got: " + resolution.changes());
    }
}
