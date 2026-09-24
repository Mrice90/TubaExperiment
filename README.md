# Infinite Conquest

Infinite Conquest is a tactical card game combining deck construction, a shared 24-hex battlefield, spatial combat, stacking, and faction-driven strategies.

This repository was rebuilt from the former Medieval Duel prototype. The original project remains recoverable through Git history. The separate `Mrice90/Creepy-Tomatoe` Ninja vs Zombies repository is not touched by this work.

## Play the graphical game (Hex & Allies 0.4.2)

Requires JDK 17, Gradle 8+, and a desktop environment.

Version 0.4.2 rebuilds all six faction starters with fixed, repeatable lists and safer opening curves. Exact old saved starters update with a backup; edited decks are preserved. See [starter lists, strategies and reset details](docs/starters/README.md).

Version 0.4.1 adds complete readable card inspection, spell explanations before reaction selection, target outcome previews, and an upright board with your territory at the bottom. See [readability, reactions and perspective](docs/readability-reactions-perspective.md).

The [0.4 balance pass](docs/balance/README.md) reviews all 402 faction cards and Capitals, retains free basic developments, prices stronger upgrades, and includes a complete card ledger and reproducible hex-match reports.

```bash
gradle :game-gui:run
```

The graphical client now runs height-aware hex matches with allied decks, twelve functional development keywords, archetype tags and shareable deck codes. See [terrain, height and card upgrades](docs/terrain-keywords-and-height.md). See [Hex & Allies](docs/hex-allies-playable.md) for migration, controls and compatibility. The graphical client uses the same tested engine as the CLI. Choose both factions and one of three Capitals per side before the match, with strategy and passive summaries shown in setup.

- Drag a hand card or battlefield unit onto a gold-highlighted legal destination. Click selection plus the **Legal Moves** tab remains available as a keyboard-friendly fallback.
- Hover over the tucked hand to reveal cards, or click/tap **Hand** to pin it open. Selecting a card tucks the tray away; Escape closes it. The hand floats over the battlefield without moving or shrinking the hexes.
- Right-click a card in either hand view and choose **View full card** to open its complete card-format inspection view.
- Right-click an occupied battlefield cell to inspect every card in its stack, shown top-first. Right-click any card row in that inspector to open a large card-format view with art, current stats, keywords, and abilities.
- The compact overview shows all 24 battlefield cells at a 1280×650 client area, accommodating window borders and the taskbar on a 1280×720 screen. **Game → Toggle Board View** (`F4`) gives the battlefield more space; `Esc` restores the interface.
- Both players’ faction, GP held, and recurring income appear in the compact top HUD alongside End Turn. Income is based on currently controlled cards, not guaranteed future income.
- **Actions** (`F2`) opens the legal-action window; use arrow keys and Enter to execute. **History** (`F3`) opens the chronological log. The history includes income, spending, and reactions.
- Destination labels and colors distinguish movement, ranged or melee attacks, spells, top-of-stack deployment, and Mole burrowing. Ambiguous stack drops ask you to choose the exact action.
- Opening mulligans let you visually select up to three cards to discard and redraw, and enemy-turn reactions use a matching visual spell tray plus battlefield targeting instead of a text menu.
- Choose **Deck Builder** for primary faction → optional ally → Capital → cards. Inspect complete card rules and use **Share deck / Import deck code** to exchange builds. Save one local deck per primary faction; decks require at least 40 cards, starter decks contain 60, and saved decks load automatically for new matches.
- The Deck Builder is always available from **Game → Deck Builder** (`Ctrl+D`), even when compact window sizing hides header controls.
- Choose **Bot (watch match)** for Player 1 during setup to run a bot-versus-bot match.
- Battlefield callouts, directional source-to-target animations, and distinct CC0 sound cues identify movement, melee, ranged attacks, spells, destruction, and rule damage. The header’s **Game → Mute** option toggles those cues. Damaged permanents display both remaining HP and accumulated damage. Audio provenance is documented in `game-gui/src/main/resources/audio/ATTRIBUTION.md`.

The client includes the full 4×6 battlefield, responsive hex board tiles with scrolling fallback, pregame Capital placement, an animated graphical initiative coin, unique generated prototype art for every card, real faction starter decks, persistent GP and deck meters, automatic bot turns, reaction windows, and match results.

Player 1 begins with 0 GP and Player 2 begins with 1 GP. The initiative winner opens with five cards; the other player opens with six. Every surviving Capital generates 1 GP at the start of its owner's turn. Each player may discard and redraw up to three opening cards. Lands and Structures are free once their printed development turn has been reached and show their GP-per-turn output directly. Standard income rises from 1 GP on early development cards to 5 GP on turn-9/10 cards; cards with utility passives generally generate less. There is no automatic GP beyond controlled permanents, and no late-game pressure or turn deadline.

Character combat damage accumulates against Defense during the active turn, allowing several attackers or opportunity attacks to bring down one defender; all marked Character damage clears when the turn changes. Friendly Characters may share a stack, and only its top card can move, attack, or be targeted. Select a card or top unit and click any highlighted destination; drag-and-drop remains available. The desktop client starts maximized, and F11 toggles full screen.

Combat is simultaneous: attack equal to defense destroys a Character, and an in-range defending Character retaliates at the same time. A defender outside its own range cannot retaliate. Moving through an enemy Character's attack range grants that enemy one free opportunity attack per move; human players receive a route warning showing each threat and whether its attack is lethal.

Characters may move onto a friendly Land, Structure, or Capital stack and become its top card. Only the top card of any stack may attack or be attacked. **Fast Strike** prevents retaliation when the attacker strictly exceeds the defender's Defense, **Siege** doubles Character damage to permanents, and **Sharp Shot** grants +1 Attack and +1 Range while its Character is on top of a friendly Structure or Capital.

## Play the command-line prototype

Requires JDK 17 and Gradle 8+.

```bash
gradle :game-cli:run
```

Use an optional deterministic seed:

```bash
gradle :game-cli:run --args="42"
```

## Build a custom deck

The graphical **Deck Builder** supports 384 faction cards across regular, apex, keyword, tactical, development, tutor, and triggered-ability tiers. The CLI additionally retains 24 legacy DEMO/UNASSIGNED prototypes; those are not eligible for faction decks. Each faction has 64 choices, including five Lands that draw the next Structure from the deck and five Structures that draw the next Character from the deck. Its 60-card starter uses 18 Lands, 14 Structures, 20 Characters, and 8 Spells centered on a unique two-keyword identity. Custom decks require at least 40 cards and allow no more than four copies of one card. The 18 Capitals are selected separately and never count toward the deck.

Lands, Structures, and Characters can now carry data-driven abilities with four timing windows: **When this enters play**, **When this is destroyed**, **Start of your turn** passive effects, and once-per-turn **Activated** effects with a printed GP cost. The current effect set supports card draw, GP gain, self-repair, Capital repair, temporary self Attack/Defense bonuses, and direct enemy-Capital damage. Select the top card of a stack and use its highlighted **Activate** legal action to pay for an activated ability.

All 18 Capitals have unique painterly environment illustrations built for their name, faction, and strategic identity. Every playable Zeus card now has a unique painted illustration across Characters, Spells, Lands, and Structures. Every playable Poseidon Character now has unique painted art across the complete 24-card roster; cards still awaiting bespoke art use deterministic illustrations combining faction environments, card type, name-derived symbols, mechanics, and stable seeded composition. Missing painted resources safely fall back to that renderer. See [the card-art system](docs/card-art-system.md) for its visual vocabulary and asset provenance.
The renderer keeps board thumbnails fast by caching each completed image for reuse during the match.

Board actions use distinct animated effects for movement, Blink, melee, ranged projectiles, Spells, deployment, and rules damage. Completed matches open an animated result screen with the winning Capital, match summary, and direct choices to rematch with the same settings, change match settings, or review the final battlefield.

Professional transparent VFX sprites from Kenney's CC0 Particle Pack are composited into animations and card art with dynamic faction tinting. See [third-party asset notices](THIRD_PARTY_ASSETS.md) for source and license records.

```bash
gradle :game-cli:run --args="deck"
```

Use `factions`, `pool <faction>`, or `reset <faction>` to explore a 60-card starter. Use `identity ZEUS POSEIDON` to add one optional ally after resetting to that primary faction; `capital <id>` chooses its Capital. Use `share` or `import <code>` to exchange builds. Use `swap <remove-id> <add-id>`, then `save my-deck.json`. A deck saves when it contains at least 40 cards and no card has more than four copies.

Play using a saved human deck against a saved bot deck:

```bash
gradle :game-cli:run --args="play human.json bot.json 42"
```

List the three Capital choices for every faction, then optionally select one for each player:

```bash
gradle :game-cli:run --args="capitals"
gradle :game-cli:run --args="play human.json bot.json 42 zeus_capital_keraunos_spire ares_capital_red_citadel"
```

When a deck contains cards from exactly one faction, an omitted Capital defaults to that faction's first choice. A supplied Capital must match a single-faction deck.

All imported and development cards remain editable prototype content rather than locked production balance.

## Build and test

```bash
gradle test
```

Build an extract-and-run desktop archive with `gradle :game-gui:distZip`. It includes dependencies and requires Java 17 on the target machine. See the [desktop release checklist](docs/desktop-release-checklist.md) for first-run instructions, screenshot sizes, and outstanding human release checks.

## Run automated balance simulations

Run deterministic bot-versus-bot matches across all 36 ordered faction matchups and all nine Capital pairings. The default two repetitions per pairing produce 648 matches:

```bash
gradle :game-cli:run --args="simulate"
```

Choose repetitions, seed, and JSON report path:

```bash
gradle :game-cli:run --args="simulate 10 42 reports/balance.json"
```

Ten repetitions produce 3,240 matches. Reports include faction and Capital win rates, first-player advantage, match length, unused GP, ending hand size, exhaustion frequency, passive activations, card play rates, and automatic balance flags.

## Current capabilities

- desktop graphical client with selectable cards, battlefield cells, legal-action filtering, bot animation, and reaction prompts
- deterministic Player 1 vs Bot matches with automated bot turns and reactions
- deterministic headless bot-versus-bot balance simulations and JSON telemetry
- deterministic setup, hands, draws, GP, phases, and events
- 4×6 battlefield with two 4×3 player plots and ordered stacks
- movement, range, Capitals, deployment, combat, HP, destruction, and victory
- Mole, Blink, Vanguard, Fast Strike, Siege, Sharp Shot, line of sight, and typed Spell effects
- private local-player handoff, inspection, and legal-action hints
- six 64-card faction pools with development-heavy 60-card starters, tutor developments, typed draw abilities, and primary/secondary identities, plus 24 neutral/development prototypes
- three separately selectable Capitals per faction, each with a unique implemented passive ability
- executable active-turn and enemy-turn reaction Spells
- validated JSON deck files and interactive deck editor
- automated JUnit rules and interface tests
