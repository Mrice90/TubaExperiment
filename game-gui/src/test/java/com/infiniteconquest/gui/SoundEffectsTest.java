package com.infiniteconquest.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;

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

    @Test
    void everyCueHasALoadableAudioFile() {
        // Fails the build on a missing/undecodable file instead of silently
        // skipping the cue at runtime on a player's machine.
        for (SoundEffects.Cue cue : SoundEffects.Cue.values()) {
            String path = "/audio/" + cue.name().toLowerCase() + ".wav";
            try (InputStream resource = SoundEffects.class.getResourceAsStream(path)) {
                assertNotNull(resource, "missing audio resource: " + path);
                try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                    assertTrue(audio.getFrameLength() > 0, "empty audio file: " + path);
                }
            } catch (AssertionError e) {
                throw e;
            } catch (Exception e) {
                fail("could not decode audio resource " + path + ": " + e.getMessage());
            }
        }
    }
}
