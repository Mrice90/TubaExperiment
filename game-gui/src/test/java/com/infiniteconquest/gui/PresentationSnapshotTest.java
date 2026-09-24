package com.infiniteconquest.gui;

import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PresentationSnapshotTest {
    private static final UUID CARD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BoardPosition LEFT = new BoardPosition(0, 0);
    private static final BoardPosition RIGHT = new BoardPosition(1, 0);

    @Test
    void classifiesMovementFromImmutableFrames() {
        PresentationSnapshot.Frame before = frame(visual(LEFT, Zone.BATTLEFIELD, 0));
        PresentationSnapshot.Frame after = frame(visual(RIGHT, Zone.BATTLEFIELD, 0));

        PresentationSnapshot snapshot = PresentationSnapshot.between("move 0 0 1 0", before, after);

        assertEquals(PresentationSnapshot.Change.MOVED, snapshot.changes().get(0).change());
        assertEquals(LEFT, snapshot.changes().get(0).before().position());
        assertEquals(RIGHT, snapshot.changes().get(0).after().position());
    }

    @Test
    void distinguishesDamageDestructionAndZoneChanges() {
        PresentationSnapshot damaged = PresentationSnapshot.between("attack",
                frame(visual(LEFT, Zone.BATTLEFIELD, 0)),
                frame(visual(LEFT, Zone.BATTLEFIELD, 3)));
        assertEquals(PresentationSnapshot.Change.DAMAGED, damaged.changes().get(0).change());

        PresentationSnapshot destroyed = PresentationSnapshot.between("attack",
                frame(visual(LEFT, Zone.BATTLEFIELD, 0)),
                frame(visual(null, Zone.DISCARD, 3)));
        assertEquals(PresentationSnapshot.Change.DESTROYED, destroyed.changes().get(0).change());

        PresentationSnapshot returned = PresentationSnapshot.between("cast",
                frame(visual(LEFT, Zone.BATTLEFIELD, 0)),
                frame(visual(null, Zone.HAND, 0)));
        assertEquals(PresentationSnapshot.Change.ZONE_CHANGED, returned.changes().get(0).change());
    }

    @Test
    void detectsDeploymentFromHand() {
        PresentationSnapshot deployed = PresentationSnapshot.between("play",
                frame(visual(null, Zone.HAND, 0)),
                frame(visual(LEFT, Zone.BATTLEFIELD, 0)));

        assertEquals(PresentationSnapshot.Change.ENTERED_BATTLEFIELD,
                deployed.changes().get(0).change());
    }

    @Test
    void framesDefensivelyCopyTheirCardMap() {
        Map<UUID, PresentationSnapshot.CardVisual> source = new LinkedHashMap<>();
        source.put(CARD, visual(LEFT, Zone.BATTLEFIELD, 0));
        PresentationSnapshot.Frame frame = new PresentationSnapshot.Frame(source);
        source.clear();

        assertNotNull(frame.card(CARD));
        assertThrows(UnsupportedOperationException.class, () -> frame.cards().clear());
    }

    private PresentationSnapshot.Frame frame(PresentationSnapshot.CardVisual card) {
        return new PresentationSnapshot.Frame(Map.of(CARD, card));
    }

    private PresentationSnapshot.CardVisual visual(BoardPosition position, Zone zone, int damage) {
        return new PresentationSnapshot.CardVisual(CARD, 0, position, zone, damage,
                8, 5, "Snapshot Unit", CardType.CHARACTER, zone == Zone.BATTLEFIELD);
    }
}
