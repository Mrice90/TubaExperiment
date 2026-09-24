# Hex & Allies playable desktop alpha (0.4.2)

The approved browser concept now has a playable desktop implementation. Run `gradle :game-gui:run`, or extract the desktop distribution and run `bin/game-gui.bat` on Windows with Java 17 installed. The window title includes **Hex & Allies 0.4.2** so it can be distinguished from older builds.

## Starter reset in 0.4.2

All six starters now use explicit 60-card lists with stronger opening curves and fewer late developments. See [starter lists and automatic migration details](starters/README.md).

## Readability and perspective in 0.4.1

See [card inspection, reaction previews and the upright board](readability-reactions-perspective.md).

## Faction balance in 0.4

See the [balance pass and full card ledger](balance/README.md) for stat and cost changes, expanded faction terrain packages, tutor adjustments and Capital tuning. Saved deck IDs and codes remain valid and resolve to current card rules.

## Height and development expansion in 0.3

See [the complete terrain and keyword guide](terrain-keywords-and-height.md) for height-aware sight, six Land and six Structure keywords, paid utility developments, archetypes and coin skins. Existing basic developments remain free. Fourteen existing cards gain keyword effects and an additional gold cost.

## Animation and hex readability in 0.2.2

The initiative coin uses elapsed time at a 16 ms repaint cadence, with a slowing spin and a stable final face for either winner. Automated players wait for complete presentations, including reactions, and leave a short settling beat before making another decision. This prevents the live board from advancing ahead of its animation queue. Match results also wait for the final presentation.

Occupied hexes reserve consistent rows for owner/stack, name, combat stats and a short status. Text is measured against the sloped edges and ellipsized when necessary; hover tooltips retain the full name, stats and action outcome, and right-click inspection retains complete rules. The screenshot review captures both coin outcomes and verifies during ten seconds of real bot playback that the state remains unchanged throughout each active presentation.

## Desktop polish in 0.2.1

The compact header and floating hand give the battlefield more room. Hover to reveal cards or click/tap **Hand** to pin the tray; selecting a card tucks it away. The board retains its position while the tray opens. Match setup separates faction/Capital choices from a full-width placement page with all 12 friendly spaces accessible. Animation sprites follow hex footprints, particle scaling stays centered, and completed actions restore board art in the same frame.

## Playing

- Desktop matches use a 24-hex battlefield, with 12 starting spaces per player. The board is upright: your territory is at the bottom and the opponent is above. Capital placement, reactions, and teleport selection use the same orientation.
- Movement follows six neighbors; range uses hex distance. Deployment, retaliation and opportunity attacks use the same geometry as the bot's legal actions. Hex sight ignores endpoints and permits either of two nudged cube-coordinate traces for shared-edge ambiguity. Structures, Capitals and top Vanguard cards now block according to their height; occupied Structures remain obstacles.
- Click **Background** to switch Stormfront scenery and Obsidian Table. Full board view remains available through F4, and Escape restores the HUD and hand.
- Card ownership and stack counts are displayed on occupied hexes. Select cards to inspect them; right-click opens existing full-card/stack inspection. Game outcomes, resources, actions, animation, reactions and bot turns use the actual engine.

## Decks

**Deck Builder** now steps through primary faction, optional single different ally, primary-faction Capital, and cards. Choose any eligible card from the full 408-card pool; the 60 tutor cards omitted from the browser study are included. Search by name, faction, type or keyword. The inspection panel explains stats, keywords, effects, abilities and income. Capital passives appear as readable text beneath artwork.

Decks require at least 40 cards, at least 10 distinct IDs and at most four copies per ID. The Capital is separate. Cards must be primary, allied, or Neutral. Changing identity previews the cards/counts to remove before changing the draft. Cancel leaves the saved build untouched. There is no allied-card quota.

Save retains faction, ally and Capital in schema v2 under `~/.infinite-conquest/decks/<primary>.json`. There is one saved slot per primary faction. Start a new match using that faction to play the saved build; setup shows its ally and uses its saved Capital by default. A player may explicitly choose another Capital of the same faction in match setup.

Unambiguous single-faction v1 files load with no ally and the faction's first Capital. Their original bytes are backed up to `.legacy-v1.bak` before the first v2 save. Legacy mixed/Neutral-only files require an explicit identity; loading rejects them with an explanation and preserves the original. The old unrestricted CLI demo remains available, but file-based `play` requires a valid faction identity (v2 or unambiguous v1).

**Share deck** exports a self-contained ICD1 code. **Import deck code** validates and previews the complete build before replacing the open draft. Codes carry stable card IDs, counts, faction, ally and Capital, plus a copying-error checksum. They neither grant content ownership nor authenticate the sender. The browser prototype and desktop can exchange these codes; changes to rules/card balance can change how an old build plays.

CLI `reset <faction>` now selects a valid identity. `identity <primary> [ally]`, `capital <id>`, `share`, and `import <code>` support the same metadata and validation. File-based `play` uses hex geometry. Legacy demo and existing square-rule test fixtures retain square behavior through `MatchRules.current()`; `MatchRules.hex()` explicitly selects hex rules.

## Verification and remaining release work

Automated coverage includes legacy square regressions, six-neighbor movement/range, distance reversal and rotation, exhaustive single-blocker sight reversal/rotation, shared-edge double blockers, allied validation, damaged/malformed deck codes, a code generated by the actual browser codec, save/load, preservation of legacy files, and six seeded allied bot matches. The screenshot harness checks board visibility at four desktop sizes and captures all four deck-builder stages, opening setup and Capital placement. It also exercises every friendly Capital placement, verifies the floating hand does not move board cells, and checks card clicks cannot drop through the hand onto the board.

This is a playable desktop alpha. It is not an Android/iOS app or a storefront release. Phone runtime selection, physical-device UX, broad hex/alliance balance testing and faction DLC ownership/payment integration remain later milestones. The $0.99 base-price target and DLC roster decisions are unchanged. Follow the existing desktop release checklist before calling it a release candidate.
