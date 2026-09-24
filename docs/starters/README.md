# Faction starter reset — 0.4.2

All six faction starters are explicit, repeatable **60-card lists**: **18 Lands, 14 Structures, 20 Characters and 8 Spells**, with the Capital outside the draw pile and no ally selected. Every card has two to four copies. These replace the old catalog-order lists (18 Lands, 18 Structures, 24 mixed actions).

## Balance goals

- At least eight free turn-one Lands and twelve free Lands available by turn two per deck.
- At least six free Structures available by turn three, alongside paid utility developments.
- At least nine Characters costing three gold or less. No starter card requires more than six gold or a development turn later than six.
- Each faction retains both signature Character keywords, an activated tutor package, and answers to Characters and buildings.
- Fewer late developments and repeated core cards make it easier to learn a faction. Expensive apex cards remain available for custom decks.

This updates deck composition; the 0.4 card costs, rules and Capital passives are unchanged. Equal type counts are an onboarding baseline, not a restriction on custom decks.

## Reset and saved decks

Open the Deck Builder's card-selection step and choose **Reset faction starter**. Confirming replaces the draft with the current faction starter, clears its ally and selects the faction's default Capital. The saved deck changes only when you save; canceling the builder preserves it.

At startup, a saved single-faction deck whose card IDs and copy counts exactly match its 0.4.1 starter is upgraded automatically. Its name and selected Capital are preserved. The original file is backed up once as `<faction>.json.pre-starter-0.4.2.bak` beside the saved deck. Reordered copies still match; edited decks and decks with an ally are left alone. Older lists that differ from this exact snapshot can be reset manually. Existing share codes still recreate their original card lists.

## Opening-hand guidance

Look for a free early Land, an affordable Character, and a follow-up development. Avoid hands full of paid buildings or combat Spells without a unit. Use the existing mulligan to seek an opening, rather than keeping a hand only for its strongest late card. Keep some gold available for reactions.

## Opening curve

Opening probabilities below are exact draws without replacement from 60 cards, **before mulligans**. Five- and six-card probabilities are included rather than assuming both seats draw the same opening hand.

| Faction | Free turn-one Lands, old → new | New five-card chance | New six-card chance | Characters ≤3 gold | Mean Character cost, old → new |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ares | 7 → 8 | 52.4% | 59.3% | 12 | 4.24 → 3.30 |
| Athena | 7 → 12 | 68.6% | 75.5% | 9 | 4.78 → 3.35 |
| Hades | 5 → 8 | 52.4% | 59.3% | 14 | 3.79 → 3.25 |
| Hephaestus | 5 → 8 | 52.4% | 59.3% | 12 | 3.94 → 3.20 |
| Poseidon | 4 → 8 | 52.4% | 59.3% | 14 | 4.72 → 2.90 |
| Zeus | 4 → 8 | 52.4% | 59.3% | 12 | 4.14 → 3.20 |

## Validation and remaining balance questions

Run `gradle test :game-cli:starterBalance :game-gui:captureGuiScreenshots`. The simulation compares the exact previous starter snapshot with the new lists under the same current rules and bot: 108 matches per version, all 36 ordered faction pairs, each of the three matching roster-index Capital pairs, seeds 424200–424307, hex board, fixed Capital placements, and a 120-turn cap. This includes mirrors and both faction seat assignments, but does not cover every cross-index Capital pairing. Reports are diagnostics, not a competitive win-rate estimate.

| Diagnostic | Previous starters | New starters |
| --- | ---: | ---: |
| Completed matches | 86 | 81 |
| Turn-limit draws | 22 | 27 |
| Mean turns (draws capped at 120) | 72.39 | 65.01 |
| Mean unspent gold at turn end | 188.00 | 99.84 |
| Seat-zero wins among completed games | 69.8% | 70.4% |

| Faction | Previous wins / 36 games | New wins / 36 games |
| --- | ---: | ---: |
| Ares | 15 | 11 |
| Athena | 20 | 16 |
| Hades | 12 | 9 |
| Hephaestus | 14 | 17 |
| Poseidon | 12 | 8 |
| Zeus | 13 | 20 |

The new curves produce faster games and less unused gold, but this sample has **more draws and a wider faction win spread**. Zeus remains strongest here; Poseidon and Hades need focused human matchups. The bot does not plan combinations or use Mole and healing as well as a player, and the roughly 70% seat-zero result is a substantial confounder. These lists are an alpha reset, not a claim of tournament parity. Next human checks should swap seats and compare Zeus versus Poseidon/Hades, then test whether defensive games can finish without exhaustion. Do not raise card costs solely to fit this small bot sample.

The regression suite verifies starter legality, curves, faction keywords, exact-list migration, backup preservation and custom/ally protection. The desktop capture runner also checks rendering, reaction review and serialized bot animations; deployment fixtures now use a known playable card instead of depending on shuffled starter contents.

Machine-readable evidence: [previous lists](previous-lists.json), [before openings](before-decks.json), [after openings](after-decks.json), [before matches](before-matches.json), [after matches](after-matches.json). [Deck share codes](deck-codes.json) use the default Capital and no ally. The authoritative source is [faction-starters.json](../../game-cli/src/main/resources/cards/faction-starters.json).

## Complete starter lists

Costs for Lands and Structures show earliest personal turn and additional gold. Free means zero additional gold, not exemption from the turn requirement.

### Zeus — Olympus Citadel

Use Blink to reposition and Sharp Shot to cover lanes. Courier and Scout provide early bodies; Boltwing Cavalier gives a mobile closer. Hold Skybreaker Bolt for a building blocking your advance.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Olympian Cloudbank | Land | Turn 1 / free |
| 4 | Dawncloud Step | Land | Turn 1 / free |
| 4 | Throneward Conduit | Land | Turn 2 / free |
| 2 | Eagle's Perch Array | Land | Turn 1 / 1 gold |
| 2 | Stormwright's Approach | Land | Turn 2 / free |
| 2 | Aurora Reach | Land | Turn 5 / 1 gold |
| 4 | Storm Relay Pylon | Structure | Turn 1 / free |
| 2 | Keraunos Charging Spire | Structure | Turn 3 / free |
| 2 | Cloudwall Bastion | Structure | Turn 2 / 1 gold |
| 2 | Stormglass Relay | Structure | Turn 3 / 2 gold |
| 2 | Sparkstep Beacon | Structure | Turn 2 / free |
| 2 | Oracle Spire | Structure | Turn 4 / free |
| 3 | Cloudline Courier | Character | 1 gold |
| 3 | Arc Relay Scout | Character | 1 gold |
| 3 | Iris Signal Runner | Character | 3 gold |
| 3 | Stormgate Sentinel | Character | 3 gold |
| 2 | Skyline Seer | Character | 5 gold |
| 2 | Boltwing Cavalier | Character | 4 gold |
| 2 | Stormgate Adept | Character | 5 gold |
| 2 | Thunderhead Guardian | Character | 6 gold |
| 2 | Chain Lightning | Spell | 3 gold |
| 2 | Windstep Protocol | Spell | 2 gold |
| 2 | Aegis of the Sky | Spell | 2 gold |
| 2 | Skybreaker Bolt | Spell | 4 gold |

### Poseidon — Atlantis Nexus

Establish Lands, use Vanguard to protect your line and heal damaged defenders. Sonar Adept and Kraken Tendril Drone add reach and finishing pressure. Burrow selectively: a hidden Mole cannot replace your visible defensive line.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Neon Tidelands | Land | Turn 1 / free |
| 4 | Coral Data Reef | Land | Turn 1 / free |
| 4 | Palace of Tides Approach | Land | Turn 2 / free |
| 2 | Healing Shoal | Land | Turn 2 / 1 gold |
| 2 | Mole-Tide Channel | Land | Turn 2 / free |
| 2 | Worldsea Platform | Land | Turn 5 / free |
| 4 | Sonar Beacon | Structure | Turn 2 / free |
| 2 | Tidevault | Structure | Turn 3 / free |
| 2 | Coral Bulwark | Structure | Turn 2 / 1 gold |
| 2 | Tidal Pump Station | Structure | Turn 1 / 1 gold |
| 2 | Undertow Burrow Gate | Structure | Turn 2 / free |
| 2 | Tidewell Bastion | Structure | Turn 4 / free |
| 3 | Tidepool Surveyor | Character | 1 gold |
| 3 | Undertow Stalker | Character | 2 gold |
| 3 | Reefline Defender | Character | 2 gold |
| 3 | Breakwater Hoplite | Character | 3 gold |
| 2 | Delphic Sonar Adept | Character | 3 gold |
| 2 | Trench Stalker | Character | 5 gold |
| 2 | Reefwarden | Character | 5 gold |
| 2 | Kraken Tendril Drone | Character | 4 gold |
| 2 | Crushing Depths | Spell | 3 gold |
| 2 | Restorative Tide | Spell | 2 gold |
| 2 | Tidal Armor | Spell | 2 gold |
| 2 | Erode Foundation | Spell | 3 gold |

### Hades — House of Hades

Trade inexpensive Characters, recover them with your Capital, and pressure with Fast Strike. Erebus Sniper provides reach while Grave Pressure clears buildings. Keep at least one visible defender when using Mole.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Styx Transit Channel | Land | Turn 1 / free |
| 4 | Asphodel Server Field | Land | Turn 1 / free |
| 4 | Gate of the Dead Grid | Land | Turn 2 / free |
| 2 | Erebus Undercity | Land | Turn 1 / 1 gold |
| 2 | Shadeburrow Passage | Land | Turn 2 / free |
| 2 | Erebus Claim | Land | Turn 5 / 2 gold |
| 4 | Obol Archive | Structure | Turn 1 / free |
| 2 | Hades Throne Vault | Structure | Turn 4 / free |
| 2 | Cerberus Gate Node | Structure | Turn 2 / free |
| 2 | Silent Mortuary | Structure | Turn 6 / 1 gold |
| 2 | Shade Passage Bell | Structure | Turn 2 / free |
| 2 | Soul Furnace | Structure | Turn 4 / free |
| 3 | Styx Ferryman Drone | Character | 1 gold |
| 3 | Gravecode Collector | Character | 2 gold |
| 3 | Erebus Sniper | Character | 3 gold |
| 3 | Shade Ferryman | Character | 3 gold |
| 2 | Final Breath | Character | 3 gold |
| 2 | Graveflash Assassin | Character | 5 gold |
| 2 | Styx Burrower | Character | 5 gold |
| 2 | Erebus Reaper | Character | 6 gold |
| 2 | Soul Sever | Spell | 4 gold |
| 2 | Lethe's Embrace | Spell | 4 gold |
| 2 | Deathly Vigor | Spell | 2 gold |
| 2 | Grave Pressure | Spell | 4 gold |

### Ares — Red Citadel

Advance inexpensive attackers, then use Fast Strike and Siege to convert openings into building damage. Forced March helps commit to a lane; retain enough gold for a combat Spell.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Crimson Training Yard | Land | Turn 1 / free |
| 4 | Phobos Launch Strip | Land | Turn 1 / free |
| 4 | Warforge Front | Land | Turn 2 / free |
| 2 | Red Dust March | Land | Turn 2 / 1 gold |
| 2 | First-Blood Muster | Land | Turn 2 / free |
| 2 | Conqueror's Divide | Land | Turn 5 / 1 gold |
| 4 | Forward Barricade | Structure | Turn 1 / free |
| 2 | Spoil Depot | Structure | Turn 3 / free |
| 2 | Ballistic Shrine | Structure | Turn 2 / 2 gold |
| 2 | Chariot Deployment Rack | Structure | Turn 3 / 2 gold |
| 2 | Blood-Rush Barracks | Structure | Turn 2 / free |
| 2 | War-Drum Tower | Structure | Turn 4 / 1 gold |
| 3 | Redline Recruit | Character | 1 gold |
| 3 | Spearwall Breaker | Character | 2 gold |
| 3 | Bloodshield Recruit | Character | 2 gold |
| 3 | Flashblade Gatecrasher | Character | 3 gold |
| 2 | Warhound Assault Pack | Character | 4 gold |
| 2 | Phalanx Breaker | Character | 5 gold |
| 2 | Gate Ravager | Character | 6 gold |
| 2 | Warpath Ram | Character | 6 gold |
| 2 | Spear Volley | Spell | 2 gold |
| 2 | Blood Frenzy | Spell | 2 gold |
| 2 | Forced March | Spell | 2 gold |
| 2 | Siege Fury | Spell | 4 gold |

### Athena — Acropolis Command

Build a Vanguard screen with ranged support. Medusa-Pattern Analyst adds closing power; Expose Structural Weakness answers entrenched buildings. Use defensive Spells when they can preserve an attack or an important unit.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Academy Training Grid | Land | Turn 1 / free |
| 4 | Owlwatch Promenade | Land | Turn 1 / free |
| 4 | Parthenon Data Court | Land | Turn 1 / free |
| 2 | Aegis Defensive Quarter | Land | Turn 2 / 1 gold |
| 2 | Aegis Muster Court | Land | Turn 2 / free |
| 2 | Oracle's Province | Land | Turn 5 / 1 gold |
| 4 | Tactical Relay Post | Structure | Turn 1 / free |
| 2 | Logistics Stoa | Structure | Turn 3 / free |
| 2 | Owlwatch Tower | Structure | Turn 2 / 1 gold |
| 2 | Aegis Projection Wall | Structure | Turn 3 / 1 gold |
| 2 | Aegis Cadet Hall | Structure | Turn 2 / free |
| 2 | Council Tower | Structure | Turn 4 / free |
| 3 | Athenian Cadet | Character | 1 gold |
| 3 | Grid Tactician | Character | 2 gold |
| 3 | Aegis Cadet | Character | 2 gold |
| 3 | Pallas Counter-Sniper | Character | 4 gold |
| 2 | High-Flank Scholar | Character | 4 gold |
| 2 | Medusa Pattern Analyst | Character | 4 gold |
| 2 | Highwall Archon | Character | 6 gold |
| 2 | Labyrinth Marksman | Character | 6 gold |
| 2 | Calculated Shot | Spell | 3 gold |
| 2 | Brace Formation | Spell | 3 gold |
| 2 | Tactical Edge | Spell | 2 gold |
| 2 | Expose Structural Weakness | Spell | 3 gold |

### Hephaestus — Great Forge

Build an economical Structure network behind sturdy Characters. Repair preserves valuable units; Forge Ram and Foundry Siege Walker provide building pressure. Do not spend all your gold developing without a Character to protect the lane.

| Copies | Card | Type | Cost |
| ---: | --- | --- | --- |
| 4 | Cinderworks Lot | Land | Turn 1 / free |
| 4 | Magma Conduit Field | Land | Turn 1 / free |
| 4 | Hephaestus Core Forge | Land | Turn 2 / free |
| 2 | Caldera Works | Land | Turn 5 / 3 gold |
| 2 | Bronze Muster Yard | Land | Turn 2 / free |
| 2 | Copper Seam | Land | Turn 2 / free |
| 4 | Automaton Assembly Line | Structure | Turn 1 / free |
| 2 | Gear Mint | Structure | Turn 3 / free |
| 2 | Smart-Metal Bulwark | Structure | Turn 2 / 1 gold |
| 2 | Cyclops Plasma Furnace | Structure | Turn 3 / 3 gold |
| 2 | Bronze Vanguard Forge | Structure | Turn 2 / free |
| 2 | Repair Foundry | Structure | Turn 4 / 2 gold |
| 3 | Forge Spark Drone | Character | 1 gold |
| 3 | Bronze Assembly Bot | Character | 2 gold |
| 3 | Automaton Shieldsmith | Character | 2 gold |
| 3 | Subforge Sapper | Character | 3 gold |
| 2 | Cyclops Arc-Welder | Character | 4 gold |
| 2 | Foundry Siege Walker | Character | 5 gold |
| 2 | Forge Ram | Character | 6 gold |
| 2 | Foundry Bastion | Character | 5 gold |
| 2 | Plasma Cut | Spell | 3 gold |
| 2 | Field Repair | Spell | 2 gold |
| 2 | Overclock | Spell | 2 gold |
| 2 | Core Meltdown | Spell | 4 gold |
