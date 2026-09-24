# Balance pass 2 — faction spread and initiative

Historical experiment: the pressure and turn-deadline mechanics below were subsequently removed. These results do not describe current balance. See the [current rules](game-rules-digital-spec.md).

Results use 3,240 deterministic bot matches (seed 42), covering every ordered faction matchup and Capital pairing ten times.

| Metric | Baseline | Pass 1 | Pass 2 | Prototype goal |
|---|---:|---:|---:|---:|
| Average turns | 30.61 | 17.63 | 16.50 | approximately 15 |
| First-player win rate | 69.78% | 61.65% | 54.12% | 45–55% |
| Draws | 0 | 4 | 1 | low |

## Faction results

| Faction | Pass 1 | Pass 2 |
|---|---:|---:|
| Ares | 51.02% | 46.48% |
| Athena | 53.98% | 51.85% |
| Hades | 40.65% | 50.00% |
| Hephaestus | 57.78% | 54.07% |
| Poseidon | 53.24% | 53.24% |
| Zeus | 42.96% | 44.26% |

## Implemented changes

- The second player now receives 5 GP on each of their first two personal turns, plus the existing sixth opening card.
- Conquest Pressure begins on turn 9 and escalates on turn 15, giving both players seven equal pressure pulses by the turn-22 deadline.
- Hades' House of Hades and Styx Gate passives were expanded; Tartarus remains unchanged after reaching a healthy result.
- Zeus' Olympus Citadel and Cloud Throne bonuses were increased; Keraunos Spire remains in the healthy band.
- Hephaestus' Bronze Heart and Volcanic Foundry healing were reduced.

## Interpretation

The initiative target is met in the deterministic benchmark, match length is within roughly one and a half turns of the target, and five factions are inside the 45–55% target band. Zeus is the only remaining automatic faction flag at 44.26%, close to the lower bound. Further changes should use human playtest evidence and card-level telemetry rather than continuing to inflate its Capital bonuses.
