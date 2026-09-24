/**
 * Infinite Conquest — lobby rendezvous + Elo rating service.
 *
 * Runs on Cloudflare Workers (free tier) with a KV namespace bound as IC_KV.
 * This service is deliberately dumb: it never sees game traffic, hands, or
 * deck lists. It only stores lobby entries, quick-match tickets, and the
 * rating ledger. It never logs or stores IP addresses.
 *
 * HTTP API:
 *   POST /lobbies            {code?, hostUuid, hostName, hostRating, wssUrl, dataVersion} -> {code}
 *   GET  /lobbies            -> [{code, hostName, hostRating, wssUrl, dataVersion}]
 *   GET  /lobbies/:code      -> lobby entry | 404
 *   DELETE /lobbies/:code    {hostUuid} -> {ok:true} (only the registering UUID may delete)
 *   POST /queue              {uuid, name, rating} -> {queued:true}
 *   GET  /queue/poll?uuid=   -> {status:"waiting"}
 *                            | {status:"host", opponentUuid, opponentName, opponentRating}
 *                            | {status:"ready", wssUrl, code, opponentName, opponentRating}
 *   POST /pair               {hostUuid, forUuid, wssUrl, code, hostName, hostRating} -> {ok:true}
 *   DELETE /queue?uuid=      -> {ok:true}
 *   POST /report             {matchId, reporterUuid, winnerUuid, loserUuid, dataVersion}
 *                            -> {applied, rating, delta, reason?}
 *   GET  /leaderboard?limit= -> [{name, rating, wins, losses, uuidSuffix}]
 *   GET  /rating/:uuid       -> {rating, wins, losses, name}
 */

// --- Elo ---

export const ELO_START = 1000;
export const ELO_K = 32;

/** Standard 1v1 Elo update. Returns {winner, loser} new ratings (rounded ints). */
export function eloUpdate(winnerRating, loserRating, k = ELO_K) {
    const expectedWinner = 1 / (1 + Math.pow(10, (loserRating - winnerRating) / 400));
    const expectedLoser = 1 / (1 + Math.pow(10, (winnerRating - loserRating) / 400));
    return {
        winner: Math.round(winnerRating + k * (1 - expectedWinner)),
        loser: Math.round(loserRating + k * (0 - expectedLoser)),
    };
}

// --- Helpers ---

const CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // unambiguous

function makeCode(randomValues) {
    const bytes = randomValues || crypto.getRandomValues(new Uint8Array(6));
    let code = "";
    for (const b of bytes) code += CODE_CHARS[b % CODE_CHARS.length];
    return code;
}

/** Display names are public labels: strip control chars, cap at 24. */
export function sanitizeName(raw) {
    if (typeof raw !== "string") return "Player";
    const cleaned = raw.replace(/[\u0000-\u001F\u007F]/g, "").trim();
    if (!cleaned) return "Player";
    return cleaned.length > 24 ? cleaned.slice(0, 24) : cleaned;
}

function isUuid(value) {
    return typeof value === "string" && /^[0-9a-fA-F-]{1,64}$/.test(value);
}

function json(data, status = 200) {
    return new Response(JSON.stringify(data), {
        status,
        headers: { "content-type": "application/json" },
    });
}

function bad(message, status = 400) {
    return json({ error: message }, status);
}

async function readJson(request) {
    try {
        return await request.json();
    } catch {
        return null;
    }
}

// --- KV key helpers ---

const lobbyKey = (code) => `lobby:${code}`;
const queueKey = (uuid) => `queue:${uuid}`;
const pairKey = (uuid) => `pair:${uuid}`;
const reportsKey = (matchId) => `match:${matchId}:reports`;
const ratingKey = (uuid) => `rating:${uuid}`;
const flagKey = (matchId) => `flag:${matchId}`;

const LOBBY_TTL = 120; // seconds; hosts heartbeat ~every 90s
const QUEUE_TTL = 60; // seconds; clients re-POST to stay queued
const PAIR_TTL = 180; // seconds
const REPORTS_TTL = 24 * 3600; // one side's report waits a day for the other
const FLAG_TTL = 30 * 24 * 3600; // disagreements kept 30d for review

// --- Route handlers ---

async function registerLobby(request, env) {
    const body = await readJson(request);
    if (!body || !isUuid(body.hostUuid) || typeof body.wssUrl !== "string" || !body.wssUrl.startsWith("wss://")) {
        return bad("hostUuid and wssUrl are required");
    }
    let code = typeof body.code === "string" && /^[A-Z0-9]{6}$/.test(body.code) ? body.code : makeCode();
    const entry = {
        hostUuid: body.hostUuid,
        hostName: sanitizeName(body.hostName),
        hostRating: Number.isFinite(+body.hostRating) ? Math.round(+body.hostRating) : ELO_START,
        wssUrl: body.wssUrl.slice(0, 200),
        dataVersion: typeof body.dataVersion === "string" ? body.dataVersion.slice(0, 32) : "unknown",
        status: "open",
        updatedAt: Date.now(),
    };
    await env.IC_KV.put(lobbyKey(code), JSON.stringify(entry), { expirationTtl: LOBBY_TTL });
    return json({ code });
}

async function listLobbies(env) {
    const listed = await env.IC_KV.list({ prefix: "lobby:" });
    const entries = [];
    for (const key of listed.keys) {
        const raw = await env.IC_KV.get(key.name);
        if (!raw) continue;
        const entry = JSON.parse(raw);
        if (entry.status !== "open") continue;
        entries.push({
            code: key.name.slice("lobby:".length),
            hostName: entry.hostName,
            hostRating: entry.hostRating,
            wssUrl: entry.wssUrl,
            dataVersion: entry.dataVersion,
        });
    }
    return json(entries);
}

async function getLobby(code, env) {
    const raw = await env.IC_KV.get(lobbyKey(code));
    if (!raw) return bad("unknown or expired lobby", 404);
    const entry = JSON.parse(raw);
    return json({
        code,
        hostName: entry.hostName,
        hostRating: entry.hostRating,
        wssUrl: entry.wssUrl,
        dataVersion: entry.dataVersion,
    });
}

async function deleteLobby(code, request, env) {
    const body = await readJson(request);
    const raw = await env.IC_KV.get(lobbyKey(code));
    if (!raw) return bad("unknown or expired lobby", 404);
    const entry = JSON.parse(raw);
    if (!body || body.hostUuid !== entry.hostUuid) return bad("only the host may close this lobby", 403);
    await env.IC_KV.delete(lobbyKey(code));
    return json({ ok: true });
}

async function enqueue(request, env) {
    const body = await readJson(request);
    if (!body || !isUuid(body.uuid)) return bad("uuid is required");
    const ticket = {
        name: sanitizeName(body.name),
        rating: Number.isFinite(+body.rating) ? Math.round(+body.rating) : ELO_START,
        enqueuedAt: Date.now(),
    };
    await env.IC_KV.put(queueKey(body.uuid), JSON.stringify(ticket), { expirationTtl: QUEUE_TTL });
    return json({ queued: true });
}

async function pollQueue(url, env) {
    const uuid = url.searchParams.get("uuid");
    if (!isUuid(uuid)) return bad("uuid is required");

    // A host may already have published a pairing for us. Check this first:
    // publishing a pairing consumes our ticket, so the ticket may be gone.
    const pairRaw = await env.IC_KV.get(pairKey(uuid));
    if (pairRaw) {
        await env.IC_KV.delete(pairKey(uuid));
        const pair = JSON.parse(pairRaw);
        return json({
            status: "ready",
            wssUrl: pair.wssUrl,
            code: pair.code,
            opponentName: pair.hostName,
            opponentRating: pair.hostRating,
        });
    }

    const mineRaw = await env.IC_KV.get(queueKey(uuid));
    if (!mineRaw) return json({ status: "waiting", reason: "ticket expired; re-enqueue" });
    const mine = JSON.parse(mineRaw);

    // Find the earliest other ticket.
    const listed = await env.IC_KV.list({ prefix: "queue:" });
    let other = null;
    for (const key of listed.keys) {
        const otherUuid = key.name.slice("queue:".length);
        if (otherUuid === uuid) continue;
        const raw = await env.IC_KV.get(key.name);
        if (!raw) continue;
        const ticket = JSON.parse(raw);
        ticket.uuid = otherUuid;
        if (!other || ticket.enqueuedAt < other.enqueuedAt) other = ticket;
    }
    if (!other) return json({ status: "waiting" });

    // The earlier ticket hosts; the later one waits for the pairing.
    if (mine.enqueuedAt <= other.enqueuedAt) {
        return json({
            status: "host",
            opponentUuid: other.uuid,
            opponentName: other.name,
            opponentRating: other.rating,
        });
    }
    return json({ status: "waiting" });
}

async function publishPairing(request, env) {
    const body = await readJson(request);
    if (!body || !isUuid(body.hostUuid) || !isUuid(body.forUuid)
        || typeof body.wssUrl !== "string" || !body.wssUrl.startsWith("wss://")) {
        return bad("hostUuid, forUuid, and wssUrl are required");
    }
    await env.IC_KV.put(pairKey(body.forUuid), JSON.stringify({
        wssUrl: body.wssUrl.slice(0, 200),
        code: typeof body.code === "string" ? body.code.slice(0, 16) : "",
        hostName: sanitizeName(body.hostName),
        hostRating: Number.isFinite(+body.hostRating) ? Math.round(+body.hostRating) : ELO_START,
    }), { expirationTtl: PAIR_TTL });
    await env.IC_KV.delete(queueKey(body.hostUuid));
    await env.IC_KV.delete(queueKey(body.forUuid));
    return json({ ok: true });
}

async function leaveQueue(url, env) {
    const uuid = url.searchParams.get("uuid");
    if (isUuid(uuid)) await env.IC_KV.delete(queueKey(uuid));
    return json({ ok: true });
}

async function getRatingRecord(env, uuid) {
    const raw = await env.IC_KV.get(ratingKey(uuid));
    if (!raw) return { rating: ELO_START, wins: 0, losses: 0, name: "Player", updatedAt: Date.now() };
    return JSON.parse(raw);
}

async function reportResult(request, env) {
    const body = await readJson(request);
    if (!body || typeof body.matchId !== "string" || body.matchId.length > 64
        || !isUuid(body.reporterUuid) || !isUuid(body.winnerUuid) || !isUuid(body.loserUuid)) {
        return bad("matchId, reporterUuid, winnerUuid, and loserUuid are required");
    }
    if (body.winnerUuid === body.loserUuid) return bad("winner and loser must differ");

    const key = reportsKey(body.matchId);
    const raw = await env.IC_KV.get(key);
    const reports = raw ? JSON.parse(raw) : {};
    // Idempotent on (matchId, reporterUuid): retries overwrite.
    reports[body.reporterUuid] = {
        winnerUuid: body.winnerUuid,
        loserUuid: body.loserUuid,
        dataVersion: typeof body.dataVersion === "string" ? body.dataVersion.slice(0, 32) : "unknown",
        at: Date.now(),
    };
    const reporters = Object.keys(reports);

    if (reporters.length < 2) {
        await env.IC_KV.put(key, JSON.stringify(reports), { expirationTtl: REPORTS_TTL });
        return json({ applied: false, reason: "waiting for opponent's report" });
    }

    const [a, b] = reporters.map((r) => reports[r]);
    if (a.winnerUuid !== b.winnerUuid || a.loserUuid !== b.loserUuid) {
        await env.IC_KV.put(flagKey(body.matchId),
            JSON.stringify({ reason: "disagreement", reports }), { expirationTtl: FLAG_TTL });
        await env.IC_KV.delete(key);
        return json({ applied: false, reason: "reports disagree — flagged for review" });
    }

    // Both agree: apply Elo.
    const winnerUuid = a.winnerUuid;
    const loserUuid = a.loserUuid;
    const winnerRec = await getRatingRecord(env, winnerUuid);
    const loserRec = await getRatingRecord(env, loserUuid);
    const updated = eloUpdate(winnerRec.rating, loserRec.rating);
    const now = Date.now();
    await env.IC_KV.put(ratingKey(winnerUuid), JSON.stringify({
        rating: updated.winner, wins: winnerRec.wins + 1, losses: winnerRec.losses,
        name: winnerRec.name, updatedAt: now,
    }));
    await env.IC_KV.put(ratingKey(loserUuid), JSON.stringify({
        rating: updated.loser, wins: loserRec.wins, losses: loserRec.losses + 1,
        name: loserRec.name, updatedAt: now,
    }));
    await env.IC_KV.delete(key);

    const reporterIsWinner = body.reporterUuid === winnerUuid;
    const before = reporterIsWinner ? winnerRec.rating : loserRec.rating;
    const after = reporterIsWinner ? updated.winner : updated.loser;
    return json({ applied: true, rating: after, delta: after - before });
}

async function leaderboard(url, env) {
    const limit = Math.min(Math.max(parseInt(url.searchParams.get("limit") || "25", 10) || 25, 1), 100);
    const listed = await env.IC_KV.list({ prefix: "rating:", limit: 1000 });
    const entries = [];
    for (const key of listed.keys) {
        const raw = await env.IC_KV.get(key.name);
        if (!raw) continue;
        const rec = JSON.parse(raw);
        const uuid = key.name.slice("rating:".length);
        // Leaderboard exposes names and ratings only — never full UUIDs.
        entries.push({
            name: rec.name,
            rating: rec.rating,
            wins: rec.wins,
            losses: rec.losses,
            uuidSuffix: uuid.slice(-4),
        });
    }
    entries.sort((x, y) => y.rating - x.rating);
    return json(entries.slice(0, limit));
}

async function ratingOf(uuid, env) {
    if (!isUuid(uuid)) return bad("uuid is required");
    const rec = await getRatingRecord(env, uuid);
    return json({ rating: rec.rating, wins: rec.wins, losses: rec.losses, name: rec.name });
}

// --- Router ---

export default {
    async fetch(request, env) {
        const url = new URL(request.url);
        const path = url.pathname;
        const method = request.method.toUpperCase();

        if (method === "POST" && path === "/lobbies") return registerLobby(request, env);
        if (method === "GET" && path === "/lobbies") return listLobbies(env);
        if (method === "DELETE" && path.startsWith("/lobbies/"))
            return deleteLobby(decodeURIComponent(path.slice("/lobbies/".length)), request, env);
        if (method === "GET" && path.startsWith("/lobbies/"))
            return getLobby(decodeURIComponent(path.slice("/lobbies/".length)), env);
        if (method === "POST" && path === "/queue") return enqueue(request, env);
        if (method === "GET" && path === "/queue/poll") return pollQueue(url, env);
        if (method === "POST" && path === "/pair") return publishPairing(request, env);
        if (method === "DELETE" && path === "/queue") return leaveQueue(url, env);
        if (method === "POST" && path === "/report") return reportResult(request, env);
        if (method === "GET" && path === "/leaderboard") return leaderboard(url, env);
        if (method === "GET" && path.startsWith("/rating/"))
            return ratingOf(decodeURIComponent(path.slice("/rating/".length)), env);
        return bad("not found", 404);
    },
};
