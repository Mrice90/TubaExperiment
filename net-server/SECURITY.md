# net-server security notes

This module owns the online trust boundary: the host runs the authoritative
`GameState`, and the only match state that ever crosses the network is the
per-viewer redacted `GameSnapshot`. Read this before touching the protocol.

## Redaction contract

`Redactor.redact(GameState, viewingPlayer)` is the single choke point. Every
`state_update`, `snapshot`, and `game_over` envelope is built through it, once
per recipient. The full `GameState` is **never serialized or transmitted**.

A recipient may see:

- Their own hand with complete card information, in hand order.
- Both decks **counts only** — no card IDs, no order. No deck-zone card from
  the authoritative state may appear anywhere in the snapshot.
- Opponent hand **count only** — no card IDs. Opponent hidden-zone card IDs
  must not appear anywhere in the snapshot, including event details (the
  redactor drops any event whose detail names a currently-hidden opponent
  card).
- Full information for public zones: battlefield (both players) and both
  discard piles.
- Match metadata: seed, rules variant, turn/active/starting player, personal
  turn counters, phase, winner, per-player GP and per-turn development counts.

Event types that reference hidden-zone card IDs (`CARD_DRAWN`,
`MULLIGAN_COMPLETED`) are withheld from transmission entirely.

## What the host can see

Everything. The host process owns the authoritative `GameState`, including the
guest's deck list, which the guest submits at lobby time so the host can build
the match. The join UI must plainly state: **"The host will see your deck list."**

## What the guest can see

Only their own redacted snapshot, as described above. Guests never receive the
host's deck list or hand contents.

## What must never be serialized or transmitted

- Full `GameState` objects.
- Opponent hand card UUIDs or opponent deck order.
- System information, file paths, settings contents, or any other local data.
- Chat: there is no chat in alpha and no chat envelope exists.

The wire protocol may contain only: player UUIDs, display names, deck lists
(to the host only), game commands, and redacted state.

## Identity hygiene

- Player UUIDs are generated randomly and locally (`UUID.randomUUID()`), never
  derived from hardware or system data, and stored in
  `~/.infinite-conquest/settings.properties`.
- Display names are user-chosen, max 24 characters, control characters
  stripped, and marked public in the UI (lobby; future leaderboards).
- Future leaderboards may expose display name and rating only, never UUIDs.

## Command authorization

- The server accepts only the game-command allowlist
  (`play`, `burrow`, `move`, `blink`, `attack`, `activate`, `cast`, `end`).
  Read-only query commands (`board`, `hand`, `inspect`, `actions`, `help`) are
  rejected: `board`/`hand` render hidden-zone information that must not leak.
- A command is executed only if the sender is the active player. `CommandProcessor`
  derives the acting player from `state.activePlayer()`, so the check happens
  before execution.
- `react` is rejected: net alpha resolves spells immediately with no reaction
  window. Mulligans are auto-kept for the same reason.

## Deck disclosure

The guest deck list is sent to the host only. It is never forwarded to lobby or
matchmaking services, workers, or spectators.

## Internet transport (milestone c)

- The host spawns the bundled `cloudflared` binary
  (`cloudflared tunnel --url http://127.0.0.1:<bridge-port>`, free quick
  tunnel, no account). Guests connect to the `wss://*.trycloudflare.com` URL.
  Both players connect **outward** through Cloudflare's edge, so neither
  player's IP address is exposed to the other.
- `WsBridge` listens on 127.0.0.1 only and refuses non-loopback connections.
  It translates WebSocket text frames into the existing line-delimited TCP
  protocol, so the authoritative server and its redaction are untouched: the
  bridge is a byte-pipe, not a second protocol.
- The bridge enforces that client-sent frames are masked (RFC 6455); unmasked
  client frames are rejected. The guest client always masks its frames.
- The bridge never logs or persists traffic.

## Lobby/rating Worker (milestone c)

The Cloudflare Worker (`lobby-worker/`, code only — not deployed) is a
rendezvous and rating ledger. Data minimization is deliberate:

- It stores only: player UUIDs (random, never hardware-derived), public
  display names, Elo ratings, win/loss counts, public lobby entries
  (`code`, `hostName`, `hostRating`, tunnel URL), quick-match tickets, and
  match result reports. Lobby entries expire after ~2 minutes; match reports
  after 24 hours.
- It never receives, stores, or logs: game traffic, hands, deck lists,
  file paths, settings, system information, or IP addresses.
- The leaderboard exposes display name, rating, wins, losses — never UUIDs.
- Rating integrity: both clients independently report `(matchId, winnerUuid,
  loserUuid)`. Elo is applied only when both reports agree; disagreements
  are discarded and flagged for review. The `matchId` (random per match,
  carried in `snapshot`/`game_over` envelopes) binds reports to one match.
- Public lobby deletion requires the registering UUID, so one player cannot
  close another's lobby.
