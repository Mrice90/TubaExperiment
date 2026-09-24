# Infinite Conquest lobby Worker

Rendezvous + Elo rating service for Infinite Conquest online play. Code only —
**this Worker is not deployed yet**. It costs nothing to deploy (Cloudflare
Workers free tier: 100,000 requests/day; KV free tier: 100,000 reads/day and
1,000 writes/day — verified against Cloudflare's published limits, September
2026). This design uses a handful of KV operations per match, so it fits
comfortably.

## What it stores

- Public lobby entries: `{code, hostName, hostRating, wssUrl, dataVersion}` — 2-minute TTL.
- Quick-match tickets: `{name, rating}` — 60-second TTL, refreshed by clients.
- Match result reports: `{winnerUuid, loserUuid}` per reporter — kept up to 24h.
- Rating ledger: `{rating, wins, losses, name}` per player UUID.
- Disagreement flags for review — 30 days.

## What it never sees

Game traffic, hands, deck lists, file paths, settings, system info — none of
that ever touches this service. It stores player UUIDs (random, not
hardware-derived), public display names, tunnel URLs, and rating data only.
It never reads, logs, or stores IP addresses.

## Deploy

Prerequisites: a free Cloudflare account, `npm install -g wrangler`.

```sh
cd lobby-worker
wrangler login
wrangler kv namespace create IC_KV        # paste the id into wrangler.toml
wrangler deploy
```

The deployed base URL (e.g. `https://infinite-conquest-lobby.<you>.workers.dev`)
goes into the game's Settings → Multiplayer → Lobby server URL. Without it,
the lobby browser, quick match, and Elo ratings stay unavailable; direct
tunnel hosting/joining still works.

## Test

```sh
node --test worker.test.js
```
