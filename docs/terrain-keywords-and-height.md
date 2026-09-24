# Height, development keywords and archetypes — playable alpha 0.4

## Economy

A development has two independent requirements: its personal-turn unlock and its gold cost. A basic Land or Structure is still free at its printed turn. Selected developments with stronger utility require additional gold. For example, Ballistic Shrine requires personal turn 2 and 2 gold. Both conditions must be met; rejected plays neither spend gold nor use the once-per-turn development allowance. Each player may still play one Land and one Structure per turn.

The 14 initial upgrades below were introduced in 0.3. The [0.4 balance pass](balance/README.md) expands assignments and costs across the full faction pool. Neither pass claims final competitive balance. IDs are unchanged, so saved decks and share codes remain usable and pick up the revised card rules.

## Height and sight

- The playable desktop and file-based hex matches use height-aware sight. The legacy square demo keeps its original binary sight rule.
- Empty hexes and ordinary Lands add zero height and never block sight. High Ground adds its printed height, normally 1, without becoming an obstacle itself.
- Each Structure or Capital adds 1 height. Characters do not add height merely by stacking. A Character's eye level is the height supporting it plus 1.
- A Structure/Capital remains a sight obstacle while covered. A top Vanguard blocks one level above its support. A building that is itself the top card uses an eye level at its top surface; an occupying Character sees from one level above it.
- Trace between the source and target eye levels. An intermediate building or Vanguard blocks if its top reaches or exceeds that ray. Thus a Character on a Structure can see over a lower obstruction, while a taller obstruction still blocks. Reversing the ray gives the same result.
- Source and target never block their own ray. Shared-edge hex rays retain the two-trace rule: either clear trace permits sight.
- Elevation does not automatically add range or attack. Printed range, Sharp Shot and Watchtower still determine reach. Height-based sight applies to attacks, retaliation, opportunity attacks and the visible-area entry effects below. Spells retain their existing targeting rules.
- `H` on occupied hexes is the stack's terrain/building height. Hover shows height, stack count and full stats. Full-card inspection and the deck editor explain each keyword.

## Six Land keywords

| Keyword | Default effect |
| --- | --- |
| High Ground | Add 1 elevation level to the stack; the Land remains transparent. |
| Cover | Friendly Characters on this stack take 1 less ranged attack or Turret damage. |
| Waystation | A friendly Character entering the hex recovers 1 spent movement, once per entrant per global turn. It cannot increase movement above the printed allowance. |
| Fertile | Generate 1 extra gold each owner turn, already included in displayed income. |
| Sanctuary | At owner turn start, repair 1 damage on the most damaged friendly Permanent in this hex. |
| Archive | When destroyed, draw 1 card. Normal empty-deck exhaustion still applies. |

## Six Structure keywords

| Keyword | Default effect |
| --- | --- |
| Turret | An enemy Character entering visible range 2 takes 1 marked combat damage. |
| Medic Tent | A friendly Character entering visible range 1 heals 2 marked combat damage. |
| Watchtower | Friendly Characters on this stack gain 1 Range. |
| Bulwark | Reduce attack or Turret damage to the friendly occupant by 1, including the Structure itself when exposed. |
| Workshop | At owner turn start, repair 2 damage on the most damaged friendly Structure within 1 space, including itself. |
| Beacon | A friendly Character summoned within visible range 1 gains 1 Attack until its next turn. |

### Trigger and stacking details

- Printed `range` and `amount` may override a keyword's defaults. Turret and Medic Tent values are independent, so cards can specify a different radius and damage/healing strength.
- Ordinary movement checks entry at each step. Summoning, Blink and teleport check the arrival hex. Burrowed Characters do not trigger area effects while hidden.
- Turret and Medic Tent require an outside-to-inside range crossing (or a fresh deployment), not every step within the area. Each source affects each entrant at most once per global turn. The allowance resets when turns change. Constructing a source around an existing Character is not entry.
- Friendly means the same player, including Characters from that deck's ally faction. Archetypes do not change ownership.
- Land and Structure passives remain active under friendly occupants. Multiple sources resolve in board/stack order. If entry damage destroys the entrant, later entry effects and remaining movement stop.
- Character damage remains marked until the next global turn; Medic Tent heals that marked damage and cannot overheal. Sanctuary and Workshop instead repair lasting Permanent HP damage.
- Cover applies only to ranged damage (distance greater than 1). Bulwark also applies in melee. Use the strongest applicable protection, rather than adding every reduction together; damage cannot go below zero. Spells are unchanged.
- Waystation also recognizes relocation by Blink/teleport; it restores already-spent movement, never a new action budget. Watchtower uses the strongest tower on a stack and combines with Sharp Shot.
- The action history records source, keyword, strength and target; turret and support triggers have presentation effects and obey the bot's animation wait.

## Initial card upgrades

| Existing card | Keyword | Unlock turn | Added gold cost |
| --- | --- | ---: | ---: |
| Ionized Skyway | Waystation | 1 | 1 |
| Eagle's Perch Array | High Ground | 1 | 1 |
| Cloudwall Bastion | Bulwark | 2 | 1 |
| Tidal Pump Station | Medic Tent | 1 | 1 |
| Erebus Undercity | Archive | 1 | 1 |
| Ballistic Shrine | Turret | 2 | 2 |
| Aegis Defensive Quarter | Cover | 2 | 1 |
| Owlwatch Tower | Watchtower | 2 | 1 |
| Cyclops Plasma Furnace | Turret | 3 | 3 |
| Healing Shoal | Sanctuary | 2 | 1 |
| War-Drum Tower | Beacon | 4 | 1 |
| Academy Gardens | Fertile | 2 | 2 |
| Repair Foundry | Workshop | 4 | 2 |
| Pearl Infirmary | Medic Tent | 6 | 2 |

## Archetypes

Archetypes are descriptive, searchable card-family tags, separate from functional keywords. They currently grant no automatic bonuses. This permits later effects such as “heal a Merfolk” or “repair an Automaton” without confusing species with abilities.

The first curated assignments include Human, Automaton, Merfolk, Nymph, Spirit and Beast Characters; Highland, Coast, Industrial and Urban Lands; Fortress, Workshop, Medical and Emplacement Structures; and Storm, Tidal, Necromancy, Warfare, Tactics and Artifice Spells. Ambiguous cards remain untagged. Assignments are a draft lore taxonomy stored explicitly in the catalogs, not a runtime guess from a card's name.

Data uses optional `archetypes`, `keywordValues` and `developmentGoldCost` fields. Old catalogs default to no archetype, standard keyword parameters and zero development gold cost. Unsupported keyword/type combinations and invalid numerical parameters are rejected during catalog loading.

## Coin and board presentation

The coin follows a 3.8-second upward toss, horizontal-axis flip, gravity-shaped descent and smaller landing bounces. Its disk, rim, trajectory and face texture are separate. Choose Olympian Gold, Moon Silver or Obsidian in match setup; all use the same animation geometry. This is a skin foundation, not yet a custom-image upload or cosmetic storefront.

The battlefield uses an ornamental serif title, italic supporting text and a compact height explanation. Font fallbacks are used on systems without the preferred desktop fonts.
