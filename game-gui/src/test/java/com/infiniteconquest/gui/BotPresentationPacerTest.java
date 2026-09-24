package com.infiniteconquest.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotPresentationPacerTest {
    @Test void longMultiStageActionBlocksEveryBotTickAndLeavesAReadableBeat() {
        BotPresentationPacer pacer = new BotPresentationPacer();
        assertTrue(pacer.ready(0, false));
        pacer.acted(0);
        for (long now = 60_000_000; now <= 1_500_000_000L; now += 60_000_000)
            assertFalse(pacer.ready(now, true), "The bot must not advance a live presentation");
        assertFalse(pacer.ready(1_560_000_000L, false));
        assertFalse(pacer.ready(1_679_999_999L, false));
        assertTrue(pacer.ready(1_680_000_000L, false));
    }
    @Test void reactionRestartsTheWaitAndInstantActionsAreAlsoPaced() {
        BotPresentationPacer pacer = new BotPresentationPacer();
        pacer.acted(0);
        assertFalse(pacer.ready(179_999_999L, false));
        assertTrue(pacer.ready(180_000_000L, false));
        assertFalse(pacer.ready(180_000_000L, true));
        assertFalse(pacer.ready(300_000_000L, true));
        assertFalse(pacer.ready(420_000_000L, false));
        assertTrue(pacer.ready(480_000_000L, false));
    }
}
