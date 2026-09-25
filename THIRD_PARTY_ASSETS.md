# Third-party asset notices

Infinite Conquest prefers CC0 and public-domain visual assets so the game can be distributed commercially and modified without downstream licensing surprises.

## Kenney Particle Pack 1.1

- Creator: Kenney Vleugels (Kenney.nl)
- Original asset page: https://kenney.nl/assets/particle-pack
- Repository mirror used for the imported files: https://github.com/Calinou/kenney-particle-pack
- License: Creative Commons Zero 1.0 Universal (CC0-1.0)
- License text: `game-gui/src/main/resources/vfx/kenney-particle-pack/LICENSE.txt`
- Imported files: `magic_01.png`, `magic_04.png`, `smoke_03.png`, `flame_04.png`, `spark_07.png`, `slash_02.png`, `circle_03.png`, `trace_06.png`, and `light_03.png`

These transparent sprites are dynamically scaled, tinted, rotated, and composited into battle animations, victory/defeat presentation, and card illustrations. Credit is not required by CC0, but is retained here in appreciation and to preserve provenance.

## Free VFX Asset Pack (additional particle pack, 2026-09-25)

- Pack: "Free VFX Asset Pack" — 22 pixel-art effects made with SpriteMancer
- Creator: CodeManu (CodeManuPro)
- Original asset pages: https://codemanu.itch.io/vfx-free-pack and https://opengameart.org/content/free-vfx-asset-pack
- License: CC0 1.0 Universal / public domain dedication ("This is a public domain asset, you can use it for both personal and comercial purposes.")
- License/provenance text: `game-gui/src/main/resources/vfx/free-vfx-pack/LICENSE.txt`
- Imported files (single frames extracted from the pack's own animated GIF previews, no pixel edits):
  - `vortex_swirl.png` — frame 19 (0-based) of the 48-frame `Effect_TheVortex.gif`; wired as the summon-arrival swirl
  - `ember_debris_a.png` — frame 26 of the 41-frame `Effect_Explosion.gif`; destroy-debris variant
  - `ember_debris_b.png` — frame 32 of the 44-frame `Effect_Explosion2.gif`; destroy-debris variant
  - `ember_debris_c.png` — frame 27 of the 44-frame `Effect_Explosion2.gif`; destroy-debris variant
- Total: ~51 KB (well under the few-hundred-KB budget)

The vortex is spun up at the deploy destination as a unit lands; the ember frames replace the previous flat destroy particles as textured, spinning debris chunks. Credit is not required by CC0, but is retained here in appreciation and to preserve provenance.

## Kenney audio packs (CC0)

- Creator: Kenney Vleugels (Kenney.nl)
- License: Creative Commons Zero 1.0 Universal (CC0-1.0)
- Packs used, with official pages:
  - RPG Audio — https://kenney.nl/assets/rpg-audio (footsteps, melee)
  - Interface Sounds — https://kenney.nl/assets/interface-sounds (ranged, victory/defeat originals)
  - Sci-Fi Sounds — https://kenney.nl/assets/sci-fi-sounds (spell, destroy)
  - Impact Sounds — https://kenney.nl/assets/impact-sounds (damage, penalty)
  - UI Audio — https://kenney.nl/assets/ui-audio (button clicks, hover ticks)
  - Digital Audio — https://kenney.nl/assets/digital-audio (turn banners, reaction prompt, notifications)
  - Casino Audio — https://kenney.nl/assets/casino-audio (card placement, shuffling, coin chips for GP)
  - Music Jingles — https://kenney.nl/assets/music-jingles (victory/defeat stingers, capital-hit impact)
- Source OGGs were converted to mono 44.1kHz 16-bit WAV, silence-trimmed, and loudness-normalized. Full cue-by-cue provenance: `game-gui/src/main/resources/audio/ATTRIBUTION.md`.

## Original project artwork

The six-faction environment atlas, 18 painterly Capital illustrations, painted Character illustrations, and Java-drawn card subjects are original Infinite Conquest project assets. The painted art was generated specifically for the project with OpenAI image generation and contains no downloaded third-party material. See `docs/card-art-system.md` for details.

## Sources evaluated but not imported

- The Metropolitan Museum of Art Open Access collection offers marked public-domain images under CC0.
- The National Gallery of Art offers marked Open Access images for commercial and non-commercial use.
- Game-icons.net offers a large library under CC BY 3.0, but it was not imported because Kenney's CC0 assets avoid mandatory attribution requirements and better match the current VFX pipeline.

Museum images were not bulk-imported during this pass. Mixing photographs of historical objects and paintings directly into the existing science-fantasy card set would make the factions visually inconsistent. They remain strong candidates for future relic, historical variant, or codex artwork when each selected object can be individually recorded.
