# Infinite Conquest: hex battlefield, phone release, and allied decks

Status: visual direction and deck-building flow approved for development, 23 September 2026. The user approved the current prototype direction, with a final readability correction for Capital passives. The playable desktop implementation is now tracked in [Hex & Allies](hex-allies-playable.md); desktop matches use hex geometry while legacy square fixtures remain compatible. The accompanying interactive prototype is in `prototypes/hex-mobile/index.html`.

## Confirmed direction

- Develop the approved hex battlefield direction, retaining 24 spaces and 12 starting spaces per player initially.
- Design for eventual phone release as well as desktop.
- Target a US $0.99 purchase for the full base game, with additional factions sold as DLC. Final storefront prices, base roster, DLC roster, and DLC prices are not yet selected.
- Deck creation order is **primary faction → optional one ally faction → Capital → cards**. Ally selection happens at the beginning of deck creation, not during a match.
- One ally is optional; two or more allies are never allowed.
- Retain both background choices, readable card inspection, and preview-before-replacement deck sharing. Capital rules sit below artwork in a separate panel with 17px text and generous line spacing; phone layouts use one Capital per row.

Working interpretation: the single Capital belongs to the primary faction. This was stated in the assistant's confirmation and is used throughout the prototype; the user's explicit order does not grant an additional allied Capital.

## Deliverables at this checkpoint

The browser prototype offers two visual directions, each with opening and crowded positions, desktop and portrait-phone views, card/stack inspection, and a four-step deck-builder concept. It reuses existing repository artwork. Stormfront places translucent hexes over painted scenery; Obsidian Table uses restrained stone surfaces and stronger outlines. Neither runs combat or replaces the desktop game. Geometry highlights show neighboring hexes, not legal engine moves.

The deck-builder uses 348 existing JSON-defined cards and all 18 Capitals. The 60 dynamically generated tutor cards are deliberately absent from this design sample. Sample decks validate composition but are not strategically balanced starters. Drafts save locally in the browser under a prototype-specific key; exported ICD1 codes now import into the desktop game. All six factions are visible for design evaluation; this is not an entitlement or base-roster decision.

## Visual direction and platform behavior

### Stormfront

Use existing painted environments as continuous scenery, restrained translucent hex outlines, cropped unit art, a distinct Capital seal, and small owner/stack/stat overlays. Environmental artwork is decoration: it must not imply difficult terrain, elevation, cover, or movement cost unless those mechanics are explicitly added later.

### Obsidian Table

Use the same cells and interactions on a subdued stone-like surface with clearer physical piece boundaries. This is the readability comparison, especially for small displays and crowded stacks. Both concepts must display exactly the same game information.

### Common behavior

- Current GP and recurring income have separate labels. Income is based on currently controlled cards, not a promise of future income after the opponent acts.
- Player ownership uses P1/P2 and different marker shapes as well as color; faction art is not an ownership signal.
- Hexes show only top-piece art, essential remaining stats, and a stack badge. A tap opens an inspector; full names and rules never depend on fitting inside a tiny hex.
- Capital identity remains visible even when covered by a stack. A later implementation must also show actual Capital status; prototype HP is illustrative.
- Portrait phone play is the initial test direction. Do not require dragging, hovering, or right-clicking. Tap a source, tap a destination, and confirm when an irreversible action is ambiguous. Drag can remain a desktop convenience.
- Inspecting a stack must expose only information that the rules permit; do not reveal concealed Mole cards, opponent hand identities, deck order, or secret Capital placement.
- Use a bottom sheet for phone inspection and reactions; preserve the target region while targeting. Avoid placing confirmation controls beneath system gesture areas.
- Aim for at least 44 logical pixels for ordinary touch controls and non-overlapping hex hit regions. Honor safe areas, font scaling, reduced motion, interruption, screen locking, and background/resume.
- Compact hand is a horizontal tray; expansion is a deliberate inspection mode. Returning must preserve selection and restore the whole board. The prototype demonstrates the compact tray only.
- Main gameplay should fit a 1280×650 desktop client area and a representative 390×844 phone viewport. Also evaluate 360×800 and 320×780, tablets, and landscape. Browser previews cannot certify physical-device usability or battery life.

## Proposed hex geometry

Start with four staggered columns and six rows of point-up hexes: exactly 24 spaces. The shape has 180-degree rotational symmetry. Each player starts with a contiguous 12-hex half. Mirroring a coordinate `(column, row)` as `(3-column, 5-row)` swaps territories and preserves distances. Validate degrees, distances, deployment frontage, and Capital-to-center routes before treating symmetry as evidence of strategic balance.

In the prototype, row 0 is at the top and P1 is at the bottom. Production must map display coordinates to the existing engine's player-side convention explicitly; UI orientation must never change ownership or legality. Rotating the phone must rotate/reframe the same cells, not replace the topology with a different board.

Use axial/cube coordinates internally. For odd-row offset display coordinates: `q = column - (row - (row & 1)) / 2`, `r = row`, `s = -q-r`. Distance is `max(abs(dq), abs(dr), abs(ds))`. Each interior hex has six neighbors. Board bounds must be checked separately from distance.

### Movement and deployment

- One adjacent hex costs one movement point. Preserve movement splitting and action limits unless separately changed.
- Adjacent deployment uses the same six-neighbor relation; eliminate square-only diagonal logic everywhere.
- Friendly stacking, top-card restrictions, development requirements, Capital placement secrecy, and GP costs remain the current rules. Hex conversion must not accidentally make legal friendly-stack movement illegal.
- Blink, Mole, Vanguard, and triggered abilities retain their current effects, with geometric predicates adapted and explicitly tested. Do not infer that a special movement action triggers an opportunity attack merely because normal movement does.
- Opportunity attacks must be based on the actual traversed route and each threatened hex, retaining the current per-enemy/per-move limits. Preview lethal threats before confirmation.

### Range, line of sight, and combat

- Range uses hex distance. Existing printed range and movement values are provisional starting values, not a claim of equivalent power.
- Preserve simultaneous combat, retaliation eligibility, Fast Strike thresholds, Siege, Sharp Shot, damage clearing, permanent HP, and exhaustion.
- Proposed line of sight: trace a center-to-center line through hexes, excluding source and target from intermediate blockers. For exact shared-edge ambiguity, evaluate the two infinitesimally offset traces; allow sight if either trace is unobstructed. Specify a deterministic symmetric implementation and verify reversal/rotation invariance. Do not let screen pixels decide engine line of sight.
- Existing blocker types remain the current rule. A dedicated test suite must cover edges, vertices, two adjacent blockers, stacks, and ranged retaliation before this proposal becomes final.

## Allied deck rules

1. Select one primary faction.
2. Select **None** or exactly one different ally faction.
3. Select exactly one Capital from the primary faction.
4. Build from primary-faction cards, the chosen ally's cards, and eligible Neutral cards.

Retain current construction limits: at least 40 cards, no more than four copies per card ID, and at least 10 distinct card IDs. Capitals stay outside the deck count. Existing 60-card starters are a starting aid, not a new minimum.

No allied-card quota has been requested. Start testing with no quota rather than quietly imposing a percentage. Track whether decks predominantly use allied cards while choosing a primary faction only for its Capital. If that erases faction identity or creates dominant combinations, compare a quota or primary-card requirement in a separate balance proposal.

Ownership and faction identity are different: allied cards are controlled by the same player, use that player's territory, and pay from the same GP pool. A card's printed faction does not change. Capital and card abilities must follow their wording: effects restricted to a named faction must not silently affect every allied unit; effects applying to controlled cards must not silently exclude allies. Audit each passive before enabling mixed decks.

Changing faction or ally after adding cards must show which cards become ineligible and request confirmation before removing them. The prototype uses a generic confirmation; production needs an exact card/count list and an undo path. Never silently delete a saved deck. Changing the ally does not change the primary Capital.

### Persistence and compatibility work

Implemented production deck schema v2: `schemaVersion`, `name`, `primaryFaction`, nullable `allyFaction`, `capitalId`, and `{id,copies}` entries. Keep store ownership separate from the deck definition. The prototype adds a `kind` marker to make accidental game import unambiguous.

Original baseline before 0.2: `DeckValidator` only checked size/copy/distinct limits. The production `DeckBuild` now adds explicit faction/ally/Capital validation; `DeckBuildStore` handles v2 files and ICD1 codes. The legacy `DeckFileStore` API and raw-list match factory remain for old demo fixtures. Playable desktop and file-based CLI matches use validated `DeckBuild` metadata; they do not accept arbitrary mixed decks.

Migration: infer a primary only when there is exactly one unambiguous non-neutral faction. For a legacy mixed or neutral-only deck, ask the player to assign primary and optional ally; preserve the original file. Validate Capital choice and every card before saving or starting a match. Loading, editing, importing, CLI play, bot setup, and GUI play must share the same validator.

Required tests: no ally; valid one ally; same-faction ally rejected; third faction rejected; wrong/second Capital rejected; Capitals excluded from the card pool; Neutral access; copy and distinct limits; malformed IDs; save/load round-trip; legacy migration; ally changes; faction-specific and controlled-card effects; and hidden-information handling.

## Base purchase and faction DLC

Treat $0.99 as the US base-price target, not a finalized worldwide storefront price. The base purchase should deliver a complete playable game: rules/tutorial, meaningful starter decks, deck building, local bot play, and save/resume. Online multiplayer, servers, cross-platform ownership, and ongoing content costs need separate scope decisions.

Proposed faction DLC is a permanent faction unlock: its full card pool, its Capitals, and primary/ally use are one entitlement. Do not charge again to use an owned faction as an ally. No random paid packs, energy gates, or pay-to-repair mechanics are proposed.

Two roster options remain open:

| Option | Benefit | Tradeoff |
| --- | --- | --- |
| Two complete factions in the base, remaining factions as DLC | Smaller initial art/onboarding burden; demonstrates optional alliances immediately | Base combinations are limited; existing prototype factions must not be presented as already-promised paid-release content |
| All six current factions in the base, future factions as DLC | Stronger base value and more alliance variety | Larger balance/content burden before launch and a longer path to new paid content |

Do not implement locks until the roster is approved. Prototype all six as available. For six factions, there are six no-ally identities and 30 ordered primary/ally identities before Capital and card choices. With three primary Capitals, that becomes 108 construction identities to test; it is not a promise that every identity is equally strong.

For eventual store integration, design faction unlocks as non-consumable/one-time entitlements. Apple documents restoring non-consumables and Google documents non-consumable one-time products. Add restore/reconciliation, pending-purchase handling, refunds/revocations, and offline use after verified ownership; do not build a simulated Buy button that claims to charge money. Store-specific implementation and regional requirements should be rechecked at submission time.

- [Apple: offering, completing, and restoring purchases](https://developer.apple.com/documentation/StoreKit/offering-completing-and-restoring-in-app-purchases)
- [Google Play: one-time products](https://developer.android.com/google/play/billing/one-time-products)
- [Google Play: one-time purchase lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time)

These sources support the purchase-type design, not a revenue forecast or approval guarantee. DLC should add strategic options with comparable power; test base-only decks against owned-DLC combinations. Exact DLC prices remain undecided.

## Implementation sequence after this design checkpoint

1. Visual direction and deck flow are approved. Retain both background choices during development. Base/DLC roster decisions remain open and do not block deck validation or geometry work.
2. Implement explicit deck metadata and shared primary/ally validation while keeping the square game playable. This feature can ship independently of hex conversion.
3. Add geometry interfaces and conformance fixtures for adjacency, routes, range, line of sight, deployment, and opportunity attacks. Keep square behavior intact behind a board-type flag.
4. Add a playable experimental hex renderer, matching bot geometry and animation endpoints. Reuse the existing event/snapshot flow; enrich missing structured fields incrementally.
5. Choose the phone runtime through a small performance/input/save-resume spike. The Swing UI needs a new phone presentation layer. Preserve deterministic rules with reusable data and executable fixtures; do not commit to an Android/iOS architecture solely because the mockup runs in a browser.
6. Balance alliances and hexes separately, then together. Compare no-ally baselines, every ordered faction pair, each primary Capital, mirror matches, first-player advantage, stalemates, and match duration. Bot results need human confirmation.
7. Integrate real store entitlements only after the roster and platform are selected; verify restore, interruptions, refunds, and offline behavior in store sandboxes. Finish physical-device, accessibility, and new-player testing before release.

The prototype is the approved presentation reference. Explicit deck metadata, shared validation, hex geometry and the desktop renderer are implemented in 0.2. Phone runtime work and broader balance validation follow. Payment integration remains a later phase after roster and platform decisions.
