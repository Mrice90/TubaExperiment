# Faction Themes — Infinite Conquest

Design contract for all six factions. Grounded in the established flavor
(origin/main 120-card directory) and the faction DNA already encoded in
`FactionDecks.java` (primary/secondary card types and keywords per faction).
Any future card — including the four returning factions' DLC — must be
checkable against this document.

## Zeus — STORM
Fantasy: the lightning grid and conductive sky-districts of Neo-Olympus.
Fast, precise, overwhelming.
- Primary type SPELL, secondary CHARACTER. Primary keyword BLINK, secondary SHARP_SHOT.
- Speed and mobility: high movement, Blink repositioning, first-strike pressure.
- Lightning: direct damage to the enemy Capital (DAMAGE_ENEMY_CAPITAL),
  delivered through ACTIVATED abilities and DESTROYED discharges — never
  through targeted unit removal (the engine has none, and Zeus doesn't need it).
- Chain reactions: ENTERS_PLAY card draw (the storm surge), energy bursts
  (GAIN_GP) when storm infrastructure arrives.
- Volatile structures: valuable but dangerous — a destroyed charging spire
  discharges its stored lightning into the enemy Capital.
- Weaknesses: almost no healing; if the storm stalls, Zeus runs out of tricks.
  Its best effects cost real GP every time they fire.

## Poseidon — TIDES
Fantasy: the flooded arcologies, coral archives, abyssal trenches. Patient,
inexorable, restorative.
- Primary type LAND, secondary CHARACTER. Primary keyword MOLE, secondary VANGUARD.
- Ebb and flow: PASSIVE (start-of-turn) triggers are the heartbeat — healing,
  slow compounding value.
- Sustain: HEAL_SELF on permanents and HEAL_CAPITAL. Poseidon holds a
  near-monopoly on repeatable Capital healing; no other faction gets it cheaply.
- The depths: card flow from ENTERS_PLAY and DESTROYED (dredging the archive);
  MOLE deployment as positioning from unexpected angles.
- Positioning control (reserved): true push/pull unit displacement is NOT in
  the engine yet. When it is added, it belongs to Poseidon first. Until then,
  MOLE + VANGUARD carry the positioning identity.
- Weaknesses: the slowest clock of the six; reactive rather than proactive;
  its damage output is almost entirely defensive.

## Ares — WAR
Fantasy: the proving grounds, war cults, endless conflict. (Flavor reference:
Crimson Training Yard, Arena of Burning Bronze, Ballistic Shrine.)
- Primary CHARACTER, secondary SPELL. FAST_STRIKE / SIEGE.
- Aggression: attack buffs, cheap expendable attackers, pressure from turn one.
- Self-sacrifice: pay HP or lose permanents to deal damage — DESTROYED
  triggers that hurt the enemy are Ares's signature land/structure space.
- Weaknesses: card-hungry, weak late-game sustain; falls apart if the first
  wave breaks.

## Athena — DISCIPLINE
Fantasy: academies, data courts, the Aegis. Measured, mutually supporting.
(Flavor reference: Academy Training Grid, Parthenon Data Court, Owlwatch Tower.)
- Primary CHARACTER, secondary STRUCTURE. VANGUARD / SHARP_SHOT.
- Formation: positional keywords (High Ground, Cover, Bulwark, Watchtower) and
  defense-first statlines.
- Card selection: tutoring and filtering (DRAW_CHARACTER / DRAW_STRUCTURE),
  information advantage.
- Weaknesses: slow and expensive; weak when broken out of formation.

## Hades — UNDERWORLD
Fantasy: the Styx conduits, memory towers, the sealed vault. Nothing stays
buried. (Flavor reference: Styx Transit Channel, Obol Archive, Lethe Reservoir.)
- Primary SPELL, secondary CHARACTER. MOLE / FAST_STRIKE.
- Death triggers: DESTROYED abilities that generate value — draw, GP, damage.
  Hades is the faction that most wants its own permanents to die.
- Recursion (reserved): returning cards from the discard pile needs engine
  work; the design space is Hades's and no one else's.
- Drain: effects that punish the opponent for Hades's losses.
- Weaknesses: fragile bodies; needs setup before the engine turns on.

## Hephaestus — FORGE
Fantasy: cinderworks, foundries, assembly lines. Build the machine, then turn
it on. (Flavor reference: Cyclops Foundry Floor, Automaton Assembly Line,
Talos Foundry Citadel.)
- Primary STRUCTURE, secondary LAND. VANGUARD / SIEGE.
- Upgrades: BUFF_SELF_ATTACK / BUFF_SELF_DEFENSE concentrated on structures;
  structures that get better the longer they run.
- Resource conversion: ACTIVATED abilities turning GP into cards, damage, or
  healing; WORKSHOP keyword synergies.
- Structure synergy: the faction that most wants a board full of structures.
- Weaknesses: the slowest start; vulnerable while still assembling.

## Land / structure philosophy
Every land and structure must have at least one decision point or trigger —
never vanilla cardboard — EXCEPT the free-play basics: each faction keeps
exactly one 0-cost vanilla land and one 0-cost vanilla structure as its
no-frills foundation (locked by `everyFactionKeepsAFreeVanillaLandAndStructure`).
- An ACTIVATED ability (GP-costed, once per turn): the repeatable decision.
- A triggered ability: ENTERS_PLAY (arrives with impact), DESTROYED (volatile
  or dying value), PASSIVE (start-of-turn engine).
- Or a meaningful keyword aura (Bulwark, Medic Tent, Turret, Waystation…).
- Lands lean economy + triggers (they are the board's foundation); structures
  lean activated abilities + board interaction (they are the board's machines).
- Power discipline: activated abilities carry real GP costs; direct damage is
  Capital-directed; healing is Poseidon's near-monopoly; card draw is
  strongest in Zeus (surge) and Athena (selection).
- Theme ownership for future engine vocabulary: displacement → Poseidon,
  recursion → Hades, formation auras → Athena, sacrifice → Ares,
  conversion → Hephaestus, chain lightning → Zeus.

## Cost / income / ability balance rubric
Gold income follows the engine curve (cost 0→+1/t, 1→+1/t, 2→+1/t, 3→+2/t,
4→+2/t, 5→+3/t, …): a permanent's price buys tempo AND its income tier.
Abilities must be weighed against BOTH, using these bands:
- 0 cost: vanilla ONLY (+1/t, 5–6 HP). The free-play floor; no abilities, no
  aura. Best gold ROI in the game, paid for with a card slot, a board slot,
  and removability.
- 1 cost (+1/t): cantrip on entry (draw 1), a fragile repeatable engine
  (2–3 GP per activation), or a small death trigger. HP 5–8.
- 2 cost (+1/t): the workhorse band — sturdy bodies (8–10 HP), real engines
  with meaningful GP costs, or keyword auras with self-sustain.
- 3 cost (+2/t): the income breakpoint. Power here must carry a fragility tax
  (HP 6–8) or volatility (value on destruction, not while alive).
- 4+ cost (+2/t and up): premium bodies (11+ HP) or game-shaping passives;
  deploy effects must not also be repeatable engines.
Watch list for playtesting (bots never use ACTIVATED abilities, so headless
sims cannot validate these): Zeus's repeatable Capital pingers under the
capital-win rule, and Tidal Pump Station's self-repair durability at 1 cost.
