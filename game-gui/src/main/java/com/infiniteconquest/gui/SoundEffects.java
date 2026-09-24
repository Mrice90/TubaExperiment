package com.infiniteconquest.gui;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Plays curated CC0 cues bundled in game-gui resources. */
final class SoundEffects {
    enum Cue { MOVE, DEPLOY, MELEE, RANGED, SPELL, DAMAGE, PENALTY, DESTROY, VICTORY, DEFEAT }
    private static final Set<Clip> activeClips = ConcurrentHashMap.newKeySet();
    private static volatile boolean muted;
    private SoundEffects() { }

    static void play(Cue cue) {
        if (muted) return;
        Thread thread = new Thread(() -> playResource("/audio/" + cue.name().toLowerCase() + ".wav"),
                "infinite-conquest-sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playResource(String path) {
        if (muted) return;
        try (InputStream resource = SoundEffects.class.getResourceAsStream(path)) {
            if (resource == null) return;
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        activeClips.remove(clip);
                        clip.close();
                    }
                });
                clip.open(audio);
                if (muted) clip.close();
                else {
                    activeClips.add(clip);
                    clip.start();
                }
            }
        } catch (Exception ignored) {
            // Audio feedback is optional; unavailable output must never interrupt a match.
        }
    }

    static boolean isMuted() { return muted; }

    static void setMuted(boolean value) {
        muted = value;
        if (value) {
            activeClips.forEach(Clip::close);
            activeClips.clear();
        }
    }
}
