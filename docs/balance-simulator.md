# Automated balance simulator

The headless simulator lets the deterministic bot control both players and rotates through every ordered faction matchup and every Capital pairing. With six factions and three Capitals per faction, each repetition runs 324 matches. The default is two repetitions (648 matches).

## Run

```bash
gradle :game-cli:run --args="simulate [matches-per-capital-pair] [seed] [report.json]"
```

Examples:

```bash
gradle :game-cli:run --args="simulate"
gradle :game-cli:run --args="simulate 10 42 reports/balance.json"
```

The engine has no forced turn deadline. The simulator retains a 120-turn emergency cap and a 200-action-per-turn safety cap; a match that reaches the emergency cap is recorded as a draw rather than hanging. These caps are simulation safeguards, not game rules. Seeds are derived deterministically from the supplied base seed, so the same content and arguments reproduce the same results.

## Report fields

- completed matches and draws;
- average turn count;
- first-player win rate;
- average unspent GP at turn end;
- average ending hand size;
- matches that reached exhaustion;
- faction games, wins, and win rates;
- Capital games, wins, win rates, and passive triggers per game;
- card deck appearances, plays, play rate, and wins when played;
- automatic flags for faction win rates outside 45–55%, Capital win rates outside 40–60%, rarely triggered passives, and draw rates above 10%.

The simulator also resolves lethal exhaustion damage. This closes matches whose decks are empty instead of allowing Permanents to remain on the battlefield beyond their HP.

## Interpretation

The current bot is a deterministic heuristic opponent, not a perfect player. Results identify likely balance and usability problems, but human playtests remain necessary. A card with a low play rate may be underpowered, too expensive, too situational, or simply undervalued by the bot's present strategy.

Current prototype targets are approximately 15 total turns for an average match and 22 turns for a long match. Player 1 begins with 0 GP and Player 2 with 1 GP, while the player acting second receives a sixth opening card. Capitals generate 1 GP per owner turn and development cards provide the remaining economy. There is no Conquest Pressure or forced turn deadline.
