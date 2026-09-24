package com.infiniteconquest.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundEffectsTest {
    @AfterEach
    void restoreSound() {
        SoundEffects.setMuted(false);
    }

    @Test
    void muteStateCanBeToggledWithoutStartingAudio() {
        assertFalse(SoundEffects.isMuted());
        SoundEffects.setMuted(true);
        assertTrue(SoundEffects.isMuted());
        assertDoesNotThrow(() -> SoundEffects.play(SoundEffects.Cue.MOVE));
    }
}
