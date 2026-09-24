package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckEditorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void expandedPoolProvidesMeaningfulChoicesAcrossPlayableTypes() {
        PrototypeCardPool pool = new PrototypeCardPool();

        assertEquals(152, pool.cards().size());
        assertTrue(pool.cards().stream().anyMatch(card -> card.id().equals("demo_burrower_mole")));
        assertTrue(pool.cards().stream().anyMatch(card -> card.id().equals("demo_skybridge")));
        assertTrue(pool.cards().stream().anyMatch(card -> card.id().equals("demo_shield_generator")));
    }

    @Test
    void editorStartsFromValidDemoAndSwapsWithoutBreakingSixtyCardRule() {
        DemoMatchFactory matches = new DemoMatchFactory();
        DeckEditor editor = new DeckEditor(matches.pool(), matches.demoDeck());

        assertTrue(editor.validationErrors().isEmpty());
        editor.swap("neo_proto_naiad_recon_droid", "demo_burrower_mole");

        assertEquals(60, editor.cards().size());
        assertEquals(3L, editor.counts().get("neo_proto_naiad_recon_droid"));
        assertEquals(1L, editor.counts().get("demo_burrower_mole"));
        assertTrue(editor.validationErrors().isEmpty());
    }

    @Test
    void copyLimitAndFortyCardMinimumAreEnforced() {
        DemoMatchFactory matches = new DemoMatchFactory();
        DeckEditor editor = new DeckEditor(matches.pool(), matches.demoDeck());

        assertThrows(IllegalArgumentException.class,
                () -> editor.swap("demo_land_a", "neo_proto_talus_defender"));
        while (editor.cards().size() > 39) editor.remove(editor.cards().get(0).id());
        assertTrue(editor.validationErrors().contains("Deck must contain at least 40 cards"));
        assertThrows(IllegalArgumentException.class,
                () -> new DeckFileStore().save(temporaryDirectory.resolve("invalid.json"), "Invalid", editor.cards()));
    }

    @Test
    void validCustomDeckRoundTripsAndCanStartAMatch() {
        DemoMatchFactory matches = new DemoMatchFactory();
        DeckEditor editor = new DeckEditor(matches.pool(), matches.demoDeck());
        editor.swap("neo_proto_naiad_recon_droid", "demo_burrower_mole");
        editor.swap("demo_land_a", "demo_skybridge");
        Path file = temporaryDirectory.resolve("custom.json");
        DeckFileStore store = new DeckFileStore();

        store.save(file, "Custom", editor.cards());
        List<CardDefinition> loaded = store.load(file, matches.pool());

        assertEquals(60, loaded.size());
        assertTrue(loaded.stream().anyMatch(card -> card.id().equals("demo_burrower_mole")));
        assertNotNull(matches.create(99L, loaded, matches.demoDeck()));
    }
}
