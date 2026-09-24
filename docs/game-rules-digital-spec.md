# Infinite Conquest — digital rules specification

This document records the playable hex rules. The legacy square demo retains its older geometry and binary sight. Prototype card balance remains editable. See [height and keyword details](terrain-keywords-and-height.md).

## Battlefield and setup

- Two players share a 4×6 battlefield.
- Player 0 controls rows 0–2; player 1 controls rows 3–5.
- The initiative winner draws five opening cards; the other player draws six.
- Capitals are committed secretly and revealed simultaneously.

## Turns and resources

- Before play, each player places their chosen Capital secretly on any cell of their own 4×3 plot.
- A visible coin flip determines the starting player. Player 1 begins with 0 GP and Player 2 with 1 GP. The initiative winner opens with five cards; the other player opens with six.
- The starting player skips the normal draw on the first global turn; all later turns draw normally.
- Before the first action, each player may discard and redraw up to three opening cards.
- GP is persistent and does not automatically refill or increase by turn number.
- At the start of a player's turn, each Land and Structure they control generates its printed GP income, then controlled cards refresh and the player draws one card.
- Lands and Structures have a turn requirement and a separate gold cost. Basic developments remain free; selected stronger utility cards also require gold. A turn-3 card becomes available from personal turn 3 onward, provided its gold cost can be paid.
- Every surviving Capital generates 1 GP at the start of its owner's turn, in addition to income from Lands and Structures.
- Standard development income follows a visible curve: turns 1–2 generate 1 GP, 3–4 generate 2 GP, 5–6 generate 3 GP, 7–8 generate 4 GP, and 9–10 generate 5 GP. Utility passives normally reduce that income by 1 or more.
- Characters and Spells retain their printed GP costs.
- Empty-deck draws deal one exhaustion damage to each controlled Permanent.
- There is no Conquest Pressure or forced turn deadline. Empty-deck exhaustion still applies.

## Deployment and stacks

- Lands enter empty spaces on their owner's plot.
- Structures enter on top of a controlled Land.
- Characters may enter on a friendly Permanent or an empty adjacent space, using hex adjacency.
- A Character with Mole may instead use the Burrow action to enter directly beneath a controlled Land.
- Only the top card normally moves, attacks or can be targeted. Buried Structures still block sight and provide elevation. Land/Structure keyword passives remain active under friendly occupants.
- Removing a covering Land reveals the Mole beneath it.

## Movement and Blink

- Normal hex movement uses six neighboring cells; each step costs one movement.
- Movement may be split across actions up to the Character's movement value.
- Occupied cells block normal movement.
- A top Character with Blink may move to any empty battlefield hex once per personal turn.
- Blink costs no normal movement points.

## Combat and line of sight

- Characters attack once per turn in any direction within range and sight.
- Range uses hex distance. Elevation improves sight, not printed range.
- Structures and Capitals add 1 elevation and block sight up to their top surface. High Ground adds elevation; ordinary land does not block sight. Top Vanguard Characters block one level above their support. Sight is checked between source and target eye levels; see the height guide for exact ray rules.
- The attack target does not block its own line of sight.
- Character damage accumulates against Defense during the active turn; damage equal to remaining Defense destroys the Character. Marked Character damage clears on turn change. In-range defenders retaliate simultaneously, except when Fast Strike prevents retaliation by strictly exceeding the defender's Defense.
- Permanents accumulate Attack as damage and are destroyed at their HP threshold.

## Victory

- Lands, Structures, and Capitals are Permanents.
- A player loses immediately after their final Permanent is destroyed.
- Play continues beyond turn 22 if neither player has won. The simulator's emergency cap is a testing safeguard, not a match rule.
