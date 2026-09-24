# Infinite Conquest 0.4 — faction balance pass

This update reviews all **384 faction deck cards and 18 Capitals**, with **167 mechanical changes**, **32 archetype-only changes** and **203 faction entries retained**. The 24 DEMO/UNASSIGNED prototypes are separately audited and left unchanged because they are not legal in faction decks. Card IDs and deck-code format are unchanged; saved decks resolve to the updated rules.

## What changes in play

- **Free infrastructure stays free.** Every faction has free early Lands and Structures. Stronger terrain upgrades cost 1–3 gold in addition to their personal-turn requirement. Across the pool there are 102 free and 90 paid developments.
- **Terrain has faction roles.** Zeus uses height, reach and movement; Poseidon uses repair and protected positions; Hades recovers cards and supports replacement units; Ares supports advances, summons and siege; Athena builds protected firing positions; Hephaestus maintains machines and defensive installations. Ally cards can supply a secondary role, while the primary faction still determines the Capital.
- **34 existing developments gain terrain keywords.** Another 24 late tutor cards gain faction utility. These use the twelve already implemented keywords and their existing timing, height, protection and entry rules. They are functional effects, not labels.
- **Tutors pay for card selection.** Every typed-draw activation costs 2 GP. The five turn gates remain 2/4/6/8/10; gold to play is 0/0/1/2/2. Final-tier effects have strength 1. Typed draws take the next matching card from the shuffled deck, including an eligible ally card; they are not a fresh random search.
- **Characters pay for reach and mobility.** Several cheap ranged/Blink bodies move up a price tier or lose a stat. Slow siege units and fragile melee specialists retain offensive advantages. Some near-duplicate bodies now trade attack, defense or movement instead of strictly replacing an earlier card.
- **Support spells become affordable.** Healing and temporary buffs generally cost less. Divine Tailwind and Lethe Absolute no longer charge more for the exact same effect as their ordinary equivalents. No spell gets extra rules just because its name says “apex.” Strike is threshold-based destruction, not ordinary damage.
- **Four Capitals are tuned.** Olympus grants +2 Attack rather than +3; Cloud Throne grants +2 Defense rather than +4. House of Hades keeps its Character return but loses its additional 3-HP repair. Styx Gate refunds 2 GP on the first enemy Character returned by a spell each global turn, replacing its generic spell refund and repeatable bounce refunds. Other Capitals retain their conditional roles.
- **Archetypes are clearer.** Explicit families include Human, Automaton, Merfolk, Nymph, Spirit, Beast, terrain and building groupings, plus Recruitment and Muster Ground tutors. Archetypes remain searchable descriptive families and grant no automatic combat bonuses. Ambiguous identities can remain untagged.

## Review criteria

Every current faction card has an entry in the ledger, including retained choices. Prices are design judgments based on the implemented rules, not a fitted win-rate formula.

| Card group | Balance criteria |
| --- | --- |
| Characters | Attack/Defense versus gold; movement and effective reach on 24 hexes; Blink and burrow deployment flexibility; retaliation, Fast Strike and Siege; weakness relative to neighboring choices. |
| Lands and Structures | Turn availability separately from gold; HP and income; whether repeatable utility already trades away income or requires activation payment; paid premiums for newly added effects. |
| Spells | Exact typed effect, target restrictions and reaction timing; permanent damage versus threshold removal; temporary support versus unconditional bounce. |
| Capitals | Repeatable value, condition frequency, stacking with allies, recovery loops and once-per-global-turn limits. |
| Archetypes | An explicit card identity or broad functional family; no inferred runtime bonuses. |

## Reproducible comparison

Both versions used the same fixed manifest, seeds, deck order, bot policy, hex geometry and Capital positions. Each run contains **174 matches**: 108 full-roster coverage matches, 36 original starter matches, and 30 ordered primary/ally smoke matches. Every one of the **384 deck cards was actually played at least once in both runs**, and all **18 Capitals** were selected. This is broader coverage than starter-only testing, but it is not a tournament of optimized decks.

| Faction | Baseline wins / 58 appearances | Updated wins / 58 appearances |
| --- | ---: | ---: |
| Ares | 19 (32.8%) | 18 (31.0%) |
| Athena | 29 (50.0%) | 27 (46.6%) |
| Hades | 18 (31.0%) | 25 (43.1%) |
| Hephaestus | 23 (39.7%) | 22 (37.9%) |
| Poseidon | 19 (32.8%) | 21 (36.2%) |
| Zeus | 32 (55.2%) | 29 (50.0%) |

These percentages include draws, mirrors and ally scenarios. The largest faction spread narrows from 24.14 to 18.97 percentage points; that is a useful smoke-test signal, not proof of competitive parity. Ares remains a priority for human testing of melee routes and deployment support; Zeus remains strongest in this bot sample.

| Whole-run measure | Before | After |
| --- | ---: | ---: |
| Completed matches | 140 | 142 |
| Turn-limit draws | 34 | 32 |
| Average global turns | 70.0977 | 69.477 |
| Average unused gold | 159.7145 | 145.7756 |
| Matches involving exhaustion | 96 | 96 |

The current bot is a fixed action scorer. It spends poorly in long games and can waste healing or threshold-removal spells. The 120-turn limit, fixed Capital placement and coverage decks also affect results. `seatZeroWinRate` measures the first deck's seating, **not who won the coin flip**. Remaining draw rates, banked gold and faction spread require human playtests and stronger bots before release balance can be considered settled. This pass does not change the global gold-banking economy or claim to exhaustively test every allied build.

## Evidence and reproduction

- [Complete human-readable card ledger](card-ledger.md), [CSV for filtering](card-ledger.csv), [structured ledger](card-ledger.json).
- [Before catalog](before/catalog.json), [after catalog](after/catalog.json), [before matches](before/matches.json), [after matches](after/matches.json).
- [Exact scenario manifest](scenarios.json), [methodology and limitations](methodology.json), [coverage summary](summary.json).
- [Capital rules](../capital-passives.md), [tutor rules](../faction-tutor-expansion.md), [terrain rules](../terrain-keywords-and-height.md).

From the repository root:

```text
gradle :game-cli:balancePass -PbalanceOutput=docs/balance/verification -PbalanceManifest=docs/balance/scenarios.json
python tools/balance_ledger.py
gradle test :game-gui:captureGuiScreenshots :game-gui:distZip
```

The checked-in baseline was captured before changing game data or Capital effects, using the corrected hex harness. The baseline commit is recorded in methodology.json. To recreate it, use that commit's game data/Capital implementation with this pass's simulation harness. Running the current version reproduces the updated report. Export set ordering may differ while card rules and match results remain equivalent.

Validation includes the full regression suite, explicit tests for the changed Capital limits, free/paid opening options in every faction, preserved tutor tier counts, duplicate-copy report accounting, desktop captures and actual animated bot playback. The distributable version is **0.4.0-balance-alpha**.
