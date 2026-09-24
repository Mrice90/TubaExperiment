# Faction spell list and reaction rules

Spells are low-cost, one-use cards that may be cast during either player's Play Phase. A player may preserve unspent GP and use it for reaction Spells during the opponent's turn. Spells resolve immediately; the first digital implementation does not use a response stack.

This power has a deck-building cost: every Spell occupies a deck slot that could otherwise contain a Character, Land, or Structure. Custom decks require at least 40 cards; the faction starters contain 60.

## Timing and targeting

- The active player uses `cast <hand#> <targetX> <targetY> [destinationX destinationY]`.
- The inactive player uses `react <player#> <hand#> <targetX> <targetY> [destinationX destinationY]`.
- Reaction Spells spend GP left from that player's preceding turn.
- Spells target only the top card of a battlefield stack.
- Friendly and enemy targeting is enforced from typed card data.
- Teleport requires an empty destination.
- Temporary Attack and Defense buffs expire at the start of the affected card controller's next turn.
- Resolved Spells enter their owner's discard pile.

## Zeus

| Spell | GP | Effect |
|---|---:|---|
| Chain Lightning | 3 | Strike an enemy Character with power 4 |
| Skybreaker Bolt | 4 | Deal 4 damage to an enemy Permanent |
| Windstep Protocol | 2 | Teleport a friendly Character |
| Stormcharge | 2 | Friendly Character gets +2 Attack temporarily |
| Aegis of the Sky | 2 | Friendly Character gets +3 Defense temporarily |

## Poseidon

| Spell | GP | Effect |
|---|---:|---|
| Crushing Depths | 3 | Strike an enemy Character with power 4 |
| Erode Foundation | 3 | Deal 3 damage to an enemy Permanent |
| Restorative Tide | 2 | Heal 4 damage from a friendly Permanent |
| Undertow Recall | 4 | Return an enemy Character to its owner's hand |
| Tidal Armor | 2 | Friendly Character gets +3 Defense temporarily |

## Hades

| Spell | GP | Effect |
|---|---:|---|
| Soul Sever | 4 | Strike an enemy Character with power 5 |
| Grave Pressure | 4 | Deal 4 damage to an enemy Permanent |
| Lethe's Embrace | 4 | Return an enemy Character to its owner's hand |
| Deathly Vigor | 2 | Friendly Character gets +2 Attack temporarily |
| Shroud of Erebus | 2 | Friendly Character gets +3 Defense temporarily |

## Ares

| Spell | GP | Effect |
|---|---:|---|
| Spear Volley | 2 | Strike an enemy Character with power 3 |
| Siege Fury | 4 | Deal 5 damage to an enemy Permanent |
| Forced March | 2 | Teleport a friendly Character |
| Blood Frenzy | 2 | Friendly Character gets +3 Attack temporarily |
| Defiant Roar | 2 | Friendly Character gets +2 Defense temporarily |

## Athena

| Spell | GP | Effect |
|---|---:|---|
| Calculated Shot | 3 | Strike an enemy Character with power 4 |
| Expose Structural Weakness | 3 | Deal 3 damage to an enemy Permanent |
| Aegis Restoration | 2 | Heal 4 damage from a friendly Permanent |
| Tactical Edge | 2 | Friendly Character gets +2 Attack temporarily |
| Brace Formation | 3 | Friendly Character gets +4 Defense temporarily |

## Hephaestus

| Spell | GP | Effect |
|---|---:|---|
| Plasma Cut | 3 | Strike an enemy Character with power 4 |
| Core Meltdown | 4 | Deal 5 damage to an enemy Permanent |
| Field Repair | 2 | Heal 5 damage from a friendly Permanent |
| Overclock | 2 | Friendly Character gets +2 Attack temporarily |
| Reactive Plating | 2 | Friendly Character gets +3 Defense temporarily |

All values remain prototype balance.

Apex and all other current cards are included in the [0.4 card ledger](balance/card-ledger.md). A Strike destroys a Character only when its printed threshold meets that Character's effective Defense; it is not accumulated attack damage.
