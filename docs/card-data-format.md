# Card data format

Infinite Conquest card balance is externalized as versioned JSON. The current schema version is `1`.

## Design rules

- `id` is a permanent lowercase snake_case identity. Never change it when rebalancing a card.
- `contentStatus` is `PROTOTYPE`, `PLAYTEST`, or `APPROVED`.
- `keywords` contains typed engine identifiers. Schema v1 recognizes `MOLE`, `VANGUARD`, and `BLINK`.
- `rulesText` preserves human-readable ability wording. It does not execute code.
- `gpGeneration` optionally overrides the standard GP curve for a Land or Structure. When omitted, the engine derives income from its development turn.
- `developmentPassive` is a typed Land/Structure utility ability: `DRAW_ON_DEPLOY`, `HEAL_CAPITAL_ON_DEPLOY`, or `SELF_REPAIR`. Passive developments should normally use lower `gpGeneration` than the standard curve.
- `effects` contains typed Spell operations (`type`, positive `amount`, and `target`). JSON never contains executable code.
- `faction` may be `UNASSIGNED` while a prototype has no confirmed faction.
- Lands, Structures, and Capitals must have positive `hitPoints`; other card types use zero unless a later schema changes that rule.
- Catalog loading rejects unknown schema versions, duplicate IDs, malformed IDs, negative stats, and invalid enum values.

Executable keyword behavior is registered in Java through `KeywordEffectRegistry`. This keeps JSON data-only and prevents card files from executing arbitrary logic.

## Prototype source

`game-core/src/main/resources/cards/prototype-characters.json` contains the five current Character rows imported from the Drive worksheet. These are editable test cards, not locked production balance. The Capital, Structure, Spell, and Land worksheets contain headers only, so no placeholder cards were invented.
