# Readability, reactions and local perspective — 0.4.1

## Full card inspection

The card window is resizable and constrained to the usable desktop. Artwork sits alongside a scrollable dark rules pane with explicit foreground, background and viewport colors. It no longer relies on transparent text over the platform's default white scroll viewport.

Deck building, inspection and reaction descriptions share `CardRulesText`. Cards show their printed values, separate development turn/gold requirements, income, archetypes, every implemented keyword explanation, triggered and activated abilities, development passives, spell effects and Capital passive. Inspection adds current Attack, remaining/effective Defense, effective Range, movement spent and attack usage, or current/max HP. Rules remain scrollable rather than being cut off by artwork.

## Opponent-turn spells

The reaction window lists each currently legal spell with its gold cost and plain-language effect before selection. Choose a spell, then a highlighted target. Hovering or focusing a target previews the result. Strike spells explicitly explain their effective-Defense threshold: marked combat damage does not make a high-Defense target eligible for destruction. Healing previews show the actual HP recoverable, including zero at full health. Teleport still chooses a Character first and an empty destination second; entry effects apply.

Reaction spells spend saved gold and resolve immediately between opponent actions. This does not introduce a response stack or retroactively cancel an attack that already resolved. Passing or inspecting does not spend a card or gold. One reaction is offered per window; subsequent windows use fresh authoritative legal actions.

The bot's Swing timer is stopped throughout the modal reaction dialog, including a nested teleport picker, then resumed when the window closes. A modal dialog pumps the Swing event queue, so modality alone is insufficient to protect hand indices and targets. The screenshot harness now holds a real reaction window open for more than ten bot timer intervals and fails if match state changes. It also checks that selecting a spell and previewing a target do not mutate the game.

## Upright board and online foundation

The main board, Capital placement, reaction board and teleport picker now share an upright, pointed-top layout. Movement toward the enemy reads upward. Hexes retain generous width for card labels. Text clipping uses the new sloping edges, and moving card silhouettes use the same orientation.

`BoardPerspective` maps logical positions to display centers. Either authoritative seat can be the local bottom side; the other is shown above. Opposite views are exact half-turns. Hex hit testing retains the original `BoardPosition`, so rotations do not rewrite commands, ownership, saved matches, or rules. Occupant labels are relative to the board viewer: P1 is local and P2 is the opponent.

The current desktop match still assigns the human to authoritative seat 0. This change supplies the board/view transform and tests for either seat; it does not implement online matchmaking, transport, reconnects, or a full network session adapter. A future online client must set its local seat from the session and use that seat consistently for hand, HUD and command routing as well as the board.

## Validation

Regression coverage includes all seven spell effects cast by the inactive player, gold/card consumption, unchanged active player, rejected allegiance/affordability, readable shared descriptions, threshold previews, both board perspectives, and logical-position hit testing. Desktop captures include the six inspected card examples, the reaction window before selection, and a Strike preview against an over-threshold defender. The existing small-window board/hand checks and animated bot playback remain enabled.
