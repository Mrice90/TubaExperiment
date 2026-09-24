# Balance pass 1 — tempo and initiative

Historical experiment: the pressure and turn-deadline mechanics below were subsequently removed. These results do not describe current balance. See the [current rules](game-rules-digital-spec.md).

All comparisons use 3,240 deterministic bot matches (seed 42), covering every ordered faction matchup and Capital pairing ten times.

| Metric | Baseline | Pass 1 | Prototype goal |
|---|---:|---:|---:|
| Average turns | 30.61 | 17.63 | approximately 15 |
| First-player win rate | 69.78% | 61.65% | approach 50% |
| Draws | 0 | 4 | low |
| Average unused GP | 1.84 | 1.58 | observational |
| Average ending hand | 2.41 | 2.44 | observational |

## Implemented changes

- Normal GP progression is now 1, 3, 5, 7, 9, 10.
- The second player begins with six cards instead of five and receives 4 GP on their first personal turn.
- Conquest Pressure deals 3 damage to the active player's Permanents from turn 11 and 4 damage from turn 17.
- Each player receives six pressure pulses by the end of turn 22.
- A match still alive after turn 22 is resolved by surviving Permanent count, then remaining Permanent HP; an exact tie is a draw.
- Capital passives received an initial telemetry-guided tuning pass.

## Faction results after pass 1

| Faction | Win rate |
|---|---:|
| Ares | 51.02% |
| Athena | 53.98% |
| Hades | 40.65% |
| Hephaestus | 57.78% |
| Poseidon | 53.24% |
| Zeus | 42.96% |

## Next balance priorities

The first pass substantially shortened games and reduced initiative bias, but the goals are not yet fully met. The next pass should strengthen Hades and Zeus, trim Hephaestus, and investigate bot/card interactions that preserve the remaining first-player advantage. Human playtest results should be compared with simulator results before increasing pressure further.
