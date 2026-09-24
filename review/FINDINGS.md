# Infinite Conquest — Review Findings

Repo: `~/workspace/Desolate-Tuba` (remote: `Mrice90/Desolate-Tuba`, branch `main`, status clean)
Review date: 2026-09-24 · App version: `0.4.2-starters-alpha` ("Hex & Allies 0.4.2")
Goal of this review: strip to **Zeus + Poseidon only**, optimize for **desktop**, finish **menus + loading-screen transitions**, reach a **PowerShell-launchable build now** and a **Windows .exe later**.

---

## 1. Tech stack

- **Language:** Java 17 (toolchain-enforced), **Build:** Gradle 8 (CI pins 8.10.2, Temurin JDK 17). No Gradle wrapper in repo — CI installs Gradle; local devs need Gradle 8+ on PATH.
- **Group/artifact:** `com.infiniteconquest`, root project `infinite-conquest`.
- **Libraries:** Jackson Databind 2.18.2 (JSON card data), JUnit 5 (46 test files). GUI is **Swing** (no JavaFX). No game engine framework — all custom.
- **Determinism:** seeded RNG + action logs; headless bot-vs-bot simulation for balance.

### Modules

| Module | Role | Size | Entry point / run |
|---|---|---|---|
| `game-core` | Deterministic rules engine: `GameEngine`, `GameState`, `GameAction` (sealed), board geometry, combat, spells, keywords, terrain, capitals, deck validation, card-data loading | 62 Java files (39 main classes in `core` + `data` packages) | library (no main) |
| `game-cli` | Text client: interactive matches, deck editor, balance simulator, starter/balance runners | 32 Java files | `com.infiniteconquest.cli.InfiniteConquestCli` → `gradle :game-cli:run --args="..."` |
| `game-gui` | Swing desktop client: battlefield, dialogs, deck builder, animations, sound | 32 Java files + ~170 resource files | `com.infiniteconquest.gui.InfiniteConquestGui` → `gradle :game-gui:run` |

**Dependencies:** `game-cli → game-core`; `game-gui → game-core + game-cli` (GUI reuses CLI deck stores/validators). The engine is authoritative and shared — GUI and CLI run the same tested rules.

**Build/run commands:**
```bash
gradle :game-gui:run          # graphical client
gradle :game-cli:run --args="play human.json bot.json 42"   # CLI match
gradle test                  # all tests
gradle :game-gui:distZip     # extract-and-run desktop archive (CI artifact)
gradle :game-gui:captureGuiScreenshots   # deterministic GUI screenshots (CI/Xvfb)
gradle :game-cli:run --args="simulate"  # headless balance sim
```

---

## 2. Project structure & docs

```
Desolate-Tuba/
├── build.gradle / settings.gradle   # multi-module, Java 17 toolchain
├── README.md                        # thorough: play, build, deck-building, balance
├── THIRD_PARTY_ASSETS.md            # all CC0 (see §7)
├── docs/                            # ~20 design/spec docs (see below)
├── tools/balance_ledger.py          # stdlib post-processor for balance ledger
├── game-core/src/main/{java,resources/cards/*.json}
├── game-cli/src/main/{java,resources/cards/faction-starters.json}
└── game-gui/src/main/{java,resources/{art,audio,vfx}}
```

**docs/ coverage:** `architecture.md` (engine-authoritative design), `game-rules-digital-spec.md` (canonical hex rules), `card-data-format.md` (JSON schema v1), `card-art-system.md` (painted/deterministic hybrid art), `terrain-keywords-and-height.md`, `faction-card-set.md`, `faction-spells.md`, `faction-apex-expansion.md`, `faction-keyword-expansion.md`, `faction-tutor-expansion.md`, `capital-passives.md`, `balance-pass-1.md` / `balance-pass-2.md` (historical — mechanics since removed), `balance-simulator.md`, `desktop-release-checklist.md` (all items outstanding), `gui-polish-roadmap.md` (7 presentation slices), `roadmap.md` (M0–M4), `hex-allies-playable.md` (0.4.2 alpha notes), `hex-mobile-and-allies-proposal.md` (pricing/roster proposal — **explicitly undecided: "two factions in base + rest as DLC" vs "all six in base"**; user’s strip-down effectively picks option A), `readability-reactions-perspective.md`, `rules-questions.md` (4 open rulings), `starters/README.md` + before/after JSON, `balance/` (full 0.4 ledger: catalog, matches, decisions, methodology), `prototypes/hex-mobile/` (browser design study, non-playable).

---

## 3. Menu system

**Architecture:** There is no screen manager. The app is a single `JFrame` (`InfiniteConquestGui.java`, ~2,900 lines) that always shows the battlefield; all navigation is **modal `JDialog`s** over that frame. Zero TODO/FIXME markers in GUI sources — things are either complete or absent.

| Screen / dialog | State | Notes |
|---|---|---|
| Start / title menu | **MISSING** | App launches straight into `newMatch()` → setup dialog. No splash screen. |
| Match setup ("Prepare your conquest") | Complete | 2-page wizard (settings → Capital placement): faction pick, Capital pick w/ passive descriptions, human-vs-bot, coin-skin picker, Deck Builder entry |
| Game menu bar + header HUD | Complete, minimal | Deck Builder (Ctrl+D), New Match (Ctrl+N), F11 fullscreen, F2 actions, F3 history, F4 board view, mute, End Turn, GP meters |
| Settings / options | **MISSING** | No volume, graphics, keybinds, or persistence of mute/window size |
| Credits | **MISSING** | |
| Rules / how-to / tutorial | **MISSING** | |
| Mulligan dialog | Complete | visual keep/discard, bot auto-mulligan |
| Initiative coin dialog | Complete | 3.8 s animated toss, auto-dismiss |
| Reaction window | Complete | mini board + spell tray + outcome previews |
| Deck Builder | Complete | 4-step wizard (faction → ally → Capital → cards), search, share/import deck codes, starter reset, validation |
| Card / stack inspector | Complete | right-click full-card view |
| Result screen | Complete | animated `VictoryPanel`, winning Capital art, rematch / change settings / review battlefield |
| Loading screen | **MISSING** | deck/catalog load happens silently in constructor |

**"Finish the menus" is mostly *building* new screens** (start, settings, credits, loading), not completing stubs.

---

## 4. Loading screens & transitions

- **Loading screens:** none exist. Match generation is synchronous on the EDT through modal dialogs (setup → Capital placement → coin → mulligan). No progress indicators anywhere.
- **Transitions that exist (in-match only):** glass-pane `CombatOverlay` + FIFO `PresentationQueue` — 300 ms deploy arc, 180 ms invalid-drop snap-back, 360 ms melee lunge, 320 ms destroy fade, move/blink/ranged/spell interpolations; `BotPresentationPacer` adds a 180 ms beat so bots don't outrun animation; `InteractionState` state machine blocks input during `BOT_TURN`/`REACTION`/`PRESENTATION_LOCKED`.
- **Missing:** screen-level transitions (fades/slides between dialogs or into a match) and any loading-progress UI. This is the open item behind "finish the loading-screen transitions."

---

## 5. Faction system

**All six factions** (`FactionDecks.FACTIONS`):

| Faction | Identity |
|---|---|
| **ZEUS** | Storm tempo / aerial command. Blink + Sharp Shot; mobility, elevation, card draw. Primary type SPELL, secondary CHARACTER; primary keyword BLINK, secondary SHARP_SHOT |
| **POSEIDON** | Water control / resilient board. High permanent HP, Vanguard + Mole, flexible movement. Primary LAND, secondary CHARACTER; primary MOLE, secondary VANGUARD |
| HADES | Hidden deployment / attrition. Mole + Fast Strike; death triggers |
| ARES | Direct aggression. Fast Strike + Siege; temp Attack, permanent destruction |
| ATHENA | Formation / tactical control. Vanguard + Sharp Shot; defense, elevation |
| HEPHAESTUS | Machines / fortification. Vanguard + Siege; repair, heavy Structures |

**Crucially: nothing in `game-core` engine code is faction-specific — all faction logic is data-driven** (JSON cards + `CapitalPassive` enum entries). The strip-down is a data/filtering job, not an engine rewrite.

### ZEUS (keep)
- **Starter (60 cards):** 18 Lands / 14 Structures / 20 Characters / 8 Spells. Keyword mix: BLINK×8, SHARP_SHOT×7, HIGH_GROUND/WAYSTATION/BULWARK/WATCHTOWER ×2 each.
- **Capitals:** **Olympus Citadel** (OLYMPIAN_MUSTER: turn start, first Blink Character +2 Attack) · **Keraunos Spire** (STORM_TITHE: first Spell each turn refunds 1 GP) · **Cloud Throne** (CLOUDWARD: first Blinked Character +2 Defense until next turn). All Capitals 20 HP, pregame, engine-resolved.

### POSEIDON (keep)
- **Starter (60 cards):** 18 Lands / 14 Structures / 20 Characters / 8 Spells. Keyword mix: VANGUARD×8, MOLE×5, SANCTUARY/BULWARK/MEDIC_TENT ×2 each.
- **Capitals:** **Atlantis Nexus** (TIDAL_RENEWAL: turn start, heal 3 from most damaged Land) · **Trident Bastion** (TRIDENT_RESTORATION: first Land played heals Capital 2) · **Abyssal Court** (DEEP_RESERVES: first Mole burrowed refunds 1 GP).

**Balance note:** historical sims flagged Zeus as the strongest faction; the 0.4.2 starter doc explicitly calls for human Zeus-vs-Poseidon testing. Removing Hades (Poseidon's attrition mirror) and Ares (Zeus's aggro check) will shift the meta — re-baseline after the cut.

---

## 6. Card system

**Storage:** versioned JSON (schema v1), `game-core/src/main/resources/cards/` — 11 files, **366 unique IDs, all `contentStatus: PROTOTYPE`**. Loaded via `CardCatalog` (validates schema, unique snake_case IDs, enums) → `CardData` → `CardDefinition`. `PrototypeCardPool` (CLI) merges all files + runtime tutors, throws on duplicates.

**Per-faction pool = 64 cards:** 54 JSON rows + 10 runtime-generated tutor cards (Java `FactionTutorExpansion`: `zeus_tutor_land_1..5`, `zeus_tutor_structure_1..5`, etc. — Lands draw Structures, Structures draw Characters, unlock turns 2/4/6/8/10). ⚠️ Starter JSON references tutor IDs that exist **only at runtime** — the generator must be kept or the tutors moved into JSON.

**Counts per faction (×6 now, ×2 after cut):**

| File | Per faction | Keep (Zeus+Poseidon) |
|---|---|---|
| `faction-cards.json` (core: 12 Char/4 Land/4 Struct) | 20 | 40 |
| `faction-apex-cards.json` (late-game, 5–10 GP) | 10 | 20 |
| `faction-keyword-cards.json` | 5 | 10 |
| `faction-spells.json` | 5 | 10 |
| `faction-ability-cards.json` (1 Land/1 Struct/1 Char, triggered) | 3 | 6 |
| `tactical-keyword-cards.json` | 3 | 6 |
| `faction-development-expansion.json` + `-2.json` | 8 | 16 |
| `faction-capitals.json` | 3 | 6 |
| **Subtotal** | **57** | **114** |
| runtime tutors | 10 | 20 |
| `development-cards.json` (neutral DEMO, CLI-only) | — | keep or drop (not in faction decks) |
| `prototype-characters.json` (5 UNASSIGNED test) | — | keep (tests) |

### Zeus card list (keep)
- **Characters (24):** Skyline Seer, Aegis Airguard, Keraunos Seraph (apex), Olympian Storm Titan (apex), Skyfather Archon (apex), Arc Relay Scout, Boltwing Cavalier, Cloudline Courier, Cyclone Marksman, Eagle of the High Grid, Tempest Duelist, Hera Protocol Warden, Iris Signal Runner, Keraunos Prime, Aetherbolt Avatar (kw), Cloudline Raider (kw), Sparkstep Runner (kw), Stormgate Sentinel (kw), Thunderhead Guardian (kw), Aether Spotter, Thunder Ram, Stormgate Adept, Tempest Oracle, Thunderhead Skirmisher
- **Lands (10):** Stormfront Plateau (ability), Celestial Throne Grid (apex), Eagle's Perch Array, Ionized Skyway, Aurora Reach, Dawncloud Step, Empyrean Current, Thunderstep Plateau, Olympian Cloudbank, Throneward Conduit
- **Structures (10):** Oracle Spire (ability), Worldstorm Spire (apex), Cloudwall Bastion, Keraunos Charging Spire, Storm Relay Pylon, Aegis Conductor, Cloud Archive, Oracle of Storms, Stormglass Relay, Zeus Command Nexus
- **Spells (10):** Aegis of the Sky, Crownstorm Ascendance (apex), Divine Tailwind (apex), Imperial Sky Aegis (apex), Thunder God's Verdict (apex), Wrath of Olympus (apex), Chain Lightning, Skybreaker Bolt, Stormcharge, Windstep Protocol
- **Capitals (3):** Cloud Throne, Keraunos Spire, Olympus Citadel

### Poseidon card list (keep)
- **Characters (24):** Reefwarden (ability), Abyssal Molecrab, Abysswalker Nereid (apex), Atlantis Tide Sovereign (apex), Kraken Prime Avatar (apex), Delphic Sonar Adept, Razorfin Lancer, Abyssal Leviathan (kw), Breakwater Hoplite (kw), Maelstrom Bulwark (kw), Reef Tunneler (kw), Trench Stalker (kw), Kraken Tendril Drone, Leviathan Wakeborn, Naiad Flowshaper, Nereid Current-Rider, Oceanid Pressure Mage, Poseidon's Trident Core, Reefline Defender, Tidewall Harpooner, Kraken Sapper, Tidepool Surveyor, Triton Waveguard, Undertow Stalker
- **Lands (14):** Healing Shoal (ability), Abyssal Pressure Trench, Atlantis Crown Basin (apex), Leviathan Nursery Trench (apex), Oceanus Current Vault (apex), Trident Confluence (apex), Worldsea Platform (apex), Coral Data Reef, Coral Tributary, Leviathan Shelf, Pelagic Kingdom, Saltmarsh Harbor, Neon Tidelands, Palace of Tides Approach
- **Structures (10):** Tidewell Bastion (ability), Abyss Gate, Leviathan Gate (apex), Coral Bulwark, Sonar Beacon, Ambrosial Spring, Current Exchange, Pearl Infirmary, Tidevault, Tidal Pump Station
- **Spells (6):** Maelstrom Verdict (apex), Crushing Depths, Erode Foundation, Restorative Tide, Tidal Armor, Undertow Recall
- **Capitals (3):** Abyssal Court, Atlantis Nexus, Trident Bastion

**Card format (schema v1):** stable snake_case `id` (never changes on rebalance), name, type (CHARACTER/LAND/STRUCTURE/SPELL/CAPITAL), faction, cost/attack/defense/range/movement/HP, keywords (18 values incl. 12 terrain), typed spell effects, 4 ability timings (ENTERS_PLAY / DESTROYED / PASSIVE / ACTIVATED with GP costs), `developmentPassive`, `archetypes`, `contentStatus`. Stable IDs mean existing Zeus/Poseidon deck codes (ICD1) keep resolving after the cut.

---

## 7. Assets

**THIRD_PARTY_ASSETS.md: everything third-party is CC0 — zero licensing blockers for a commercial .exe.**
- **Kenney Particle Pack 1.1** (CC0-1.0): 9 PNGs used in battle animations, victory/defeat, card art. `LICENSE.txt` bundled at `game-gui/src/main/resources/vfx/kenney-particle-pack/`.
- **Kenney audio** (CC0): 10 WAV cues (move/deploy/melee/ranged/spell/damage/penalty/destroy/victory/defeat), full per-file mapping in `game-gui/src/main/resources/audio/ATTRIBUTION.md`.
- **All painted art is original** (generated for the project Sep 2026, no third-party material). Attribution files are kept voluntarily — keep them in the bundle, but nothing is legally required.

**Art inventory (`game-gui/src/main/resources/art/`, ~64 MB):**
- `capitals/`: 18 painted (3/faction) — **12 belong to cut factions (delete)**.
- `characters/`: 48 = 24 Zeus + 24 Poseidon ✅ (cut factions: 0 — already clean)
- `lands/`: 34 = 15 Zeus + 19 Poseidon ✅
- `spells/`: 16 = 10 Zeus + 6 Poseidon ✅
- `structures/`: 30 = 15 Zeus + 15 Poseidon ✅
- `faction-environments.png`: shared 3×2 atlas for the deterministic fallback renderer.

**Art gaps for a Zeus/Poseidon build: none.** Both factions have complete bespoke painted sets for all 64 pool cards + 3 Capitals. Missing art safely falls back to the seeded procedural renderer (faction palette + motifs, cached). Removing the other four factions *eliminates* the only art gaps in the roster.

**Sound:** complete for current scope; mute toggle exists but mute state isn't persisted.

---

## 8. Git context

- **Recent work (all 2026-09-23, yesterday):** starter-deck rebuild (`dde98f8`), readability/reaction fixes (`7f8e3e7`), 0.4 balance pass (`4d0f567`), height/terrain alpha 0.3 (`0be362e`), desktop polish 0.2.x (`016356b`, `b31264e`), Hex & Allies 0.2 (`824def0`). Trajectory: engine → desktop GUI → balance → readability → starters. The game is in late-alpha polish, not early prototyping.
- **Branches:** `main` is the only local branch; ~60 remote branches (`feature/*` for every subsystem, `codex/*` from an earlier AI-assisted prototype era, `release-readability`). Feature branches are merged; `main` is the source of truth.
- **CI (`.github/workflows/`):** `ci.yml` runs `gradle test :game-gui:distZip` and uploads the zip as `infinite-conquest-desktop-alpha`; `balance-run.yml` runs sims on PRs; `gui-screenshots.yml` does Xvfb screenshot verification.
- **Working tree:** clean.

---

## 9. Strip-down & finish plan

### 9a. What to KEEP vs REMOVE (Zeus/Poseidon-only desktop build)

**Remove — data (mechanical, filter by `faction` field):**
1. From each of the 8 faction card JSONs: delete all rows where `faction ∈ {HADES, ARES, ATHENA, HEPHAESTUS}` (228 rows; 114 kept).
2. `faction-capitals.json`: keep 6 (Cloud Throne, Keraunos Spire, Olympus Citadel, Abyssal Court, Atlantis Nexus, Trident Bastion).
3. `game-cli/.../cards/faction-starters.json`: keep 2 starters; drop 4.
4. `FactionTutorExpansion.java` (game-cli): trim to 20 tutor cards (10/faction).
5. `CapitalPassive` enum + `CapitalPassiveRules` mapping: keep 6 entries.
6. `FactionDecks.FACTIONS`: `["ZEUS", "POSEIDON"]`.
7. `art/capitals/`: delete 12 JPGs for cut factions (~⅔ of that folder).
8. Saved-deck migration: existing `~/.infinite-conquest/decks/*.json` for cut factions should be backed up/ignored (follow the `StarterDeckMigration` `.bak` pattern already in the codebase).

**Remove — code references (small, contained; from GUI review):**
1. `InfiniteConquestGui.java`: `chooseMatch()` faction lists (line ~702, human + bot), `defaultChoice()` Ares capital (~657), `botFaction = "ARES"` default (~59), `factionColor()` cases for cut factions (~2198), test-fixture card IDs (`athena_owlwatch_tower`, `ares_ballista_shrine`, `ares_redline_recruit` — fixtures break if those cards are deleted).
2. `DeckBuilderDialog.java`: faction combo box (line ~18); ally list derives from `DeckBuild.FACTIONS` (CLI-side, shrinks automatically).
3. `CardArtFactory.java`: faction color/sprite/environment-grid switches enumerate six factions — trim to two (no crash risk otherwise; missing IDs just render procedurally).
4. CLI: `capitals` command output, `simulate` matrix (shrinks automatically to 4 ordered matchups × 9 Capital pairs = 36 pairings), `DeckEditorCli` faction lists.
5. Docs: optional — balance/ledger docs reference 6 factions; regenerate or annotate.

**Keep untouched:** the entire `game-core` engine (no faction-specific logic), all keywords/terrain/abilities, the GUI framework, sound/VFX, deck-code format (stable IDs preserved), the deterministic fallback art renderer.

**Ally-deck decision needed:** currently each side picks primary + optional ally (36 ordered matchups). For a 2-faction build either (a) keep allies (Zeus↔Poseidon mixing — needs balance re-test of tutor ally-draw paths) or (b) disable allies for a pure 1v1 faction duel (simpler, recommended for the stripped build).

### 9b. Desktop-optimization notes
- Already desktop-shaped: maximized launch, 1500×980 default (min 1100×640), F11 exclusive fullscreen, full keyboard map (F2/F3/F4/Ctrl+D/Ctrl+N/Esc), custom drag-and-drop, hover hand tray that never shifts the board, bounded art cache.
- Gaps: no settings persistence (mute, window size), no exit confirmation, no shortcuts help overlay, no taskbar/dock icon, no resolution picker (1280×650 client area is asserted in tests, not user-selectable).
- Perf: art cache is bounded; per-card painted JPGs (~64 MB resources) dominate the distribution — consider compressing or lazy-loading for the .exe.
- `KeywordEffectRegistry` is dead code (keywords hardwired in rules classes) — harmless; leave or clean up opportunistically.

### 9c. Menu / loading-screen completion checklist
- [ ] **Start/title menu** (new): New Match · Deck Builder · Settings · Credits · Exit — replaces launch-straight-into-setup.
- [ ] **Settings screen** (new): volume/mute (persisted), graphics quality toggle, reset/wipe local decks, persisted in `~/.infinite-conquest/`.
- [ ] **Credits screen** (new): Kenney CC0 attribution, art provenance note.
- [ ] **Loading screen** (new): progress bar for catalog/deck/art warm-up on startup and match setup; currently all synchronous on EDT — move heavy init off EDT with `SwingWorker`.
- [ ] **Screen transitions** (new): fades/slides between menu ↔ setup ↔ match; dialog appearances are currently instant.
- [ ] **Exit confirmation** (new): Alt+F4 / window-close guard mid-match.
- [ ] **Help overlay** (new): shortcut list (F2/F3/F4/Ctrl+D/Esc/F11).
- [ ] Fix: window title/version consistency (says "Hex & Allies 0.4.2" — decide final product name).
- [ ] Existing complete screens need no rework: setup wizard, mulligan, coin flip, reaction window, deck builder, inspectors, result screen.

### 9d. Packaging path: PowerShell now → .exe later

**Interim (PowerShell-launchable, this week):**
1. `gradle :game-gui:distZip` → `game-gui/build/distributions/*.zip` (already CI-built). **Fix first:** the zip is still named `game-gui-0.1.0-alpha.zip` while the app is 0.4.2 — set the archive base name in `game-gui/build.gradle`.
2. Ship a `Start-InfiniteConquest.ps1` next to the extracted zip that: checks `java -version` (needs 17+), sets `JAVA_HOME` if found, and invokes `bin\game-gui.bat`. One double-clickable step for testers.
3. **Better interim:** `jlink` a trimmed runtime (`jlink --add-modules $(jdeps ...) --strip-debug --compress=2`) and bundle it → launcher uses the bundled JRE, no Java prerequisite, still launched from PowerShell.

**Final (.exe):**
1. On a **Windows** machine with **WiX Toolset v3+** installed: `jpackage` (ships with JDK 17) converts the app-image into an installer:
   - `jpackage --type app-image` → self-contained folder with launcher `.exe` (this alone satisfies "double-clickable exe").
   - `jpackage --type exe` → full Windows installer (needs WiX; add `--vendor`, `--app-version`, icon via `--icon`, and `--win-menu`/`--win-shortcut`).
2. Keep in the bundle: Kenney `LICENSE.txt` + audio `ATTRIBUTION.md` (voluntary, good hygiene).
3. Outstanding before calling it a release: code signing cert (unsigned .exe triggers SmartScreen), the desktop checklist's human gate (**3 uncoached testers finishing a match**, scaling tests at 100/125/150%, recorded outcomes — entirely unexecuted), and the 4 open rules questions in `docs/rules-questions.md`.
4. Optional later: `jpackage` can also emit `msi` if preferred.

**Recommended sequence:** fix zip naming → PowerShell launcher + tester loop (menus/loading built in parallel) → jlink-bundled zip → jpackage app-image .exe → signed installer.
