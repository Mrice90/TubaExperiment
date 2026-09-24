# Desktop GUI polish roadmap

This roadmap keeps `game-core` deterministic and confines presentation work to `game-gui`.
Every slice lands independently on `main`, passes the complete Gradle suite, and produces
deterministic GUI screenshots before the next slice begins.

## Completed foundation

- Deterministic F10 screenshots and headless Xvfb artifact workflow.
- Stable human-turn screenshot fixtures.
- Shared `UiTheme` tokens, dark battlefield stage, and strong selection/target feedback.
- Visual review exposed and fixed selected-hand legal-action filtering.

## Delivery order

### 1. Explicit interaction state

Extract selection and drag ownership from `InfiniteConquestGui`. Follow with resolution,
bot-turn, reaction, and presentation-lock modes. Every mode defines accepted input,
visual feedback, and its legal transition back to idle.

**Gate:** controller unit tests, Gradle CI, and selected-card screenshot.

### 2. Information hierarchy

Replace the unfiltered legal-command wall with contextual actions, keep the action log
readable, and prevent compact hand-card text from clipping. Preserve keyboard, click,
drag, and right-click access.

**Gate:** opening, selected-card, and expanded-hand screenshots at the pinned CI viewport.

### 3. Presentation snapshots

Formalize immutable pre/post board snapshots for moves, attacks, deployment, destruction,
and reactions. Authoritative engine mutation remains immediate; presentation consumes
the snapshots afterward.

**Gate:** snapshot unit tests; no changes to `game-core` outcomes or simulations.

### 4. Serialized presentation queue

Use one FIFO queue for every visual sequence. Bot actions and reactions may enqueue while
another animation is active, but only the queue advances presentation. Conflicting human
input remains locked; events are never dropped or layered unpredictably.

**Gate:** queue ordering, rapid-bot, and reaction-window tests.

### 5. Card motion slices

1. Hand to board: 240 ms ease-out arc with scale transition.
2. Invalid drop: approximately 150 ms overshoot and settle.
3. Attack: source lunge, impact, return.
4. Destroy: scale/fade plus the existing particle pipeline.
5. Move and blink: interpolated sprites while final static cells are masked.

**Gate:** deterministic captured frames for each family plus the full Gradle suite.

### 6. Visual regression and maintainability

Pin Java 17, Linux/Xvfb, and Metal look-and-feel. Compare stable structural regions with
a perceptual threshold, not exact pixels. Continue extracting rendering, dialogs, and
presentation control one concern at a time.

### 7. Faction art language

Move faction palette ratios, material language, and recurring technology motifs into a
versioned data file consumed by the existing deterministic art generator. Avoid a second
handwritten source of truth.
