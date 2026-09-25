/**
 * Tests for the Infinite Conquest lobby Worker.
 *
 * Run: node --test worker.test.js
 *
 * Uses a fake KV store; no network, no Cloudflare account needed.
 */

import test from "node:test";
import assert from "node:assert/strict";
import worker, { eloUpdate, sanitizeName, wssUrlHasIpHost } from "./worker.js";

function fakeKv() {
    const store = new Map();
    return {
        async get(k) { return store.has(k) ? store.get(k) : null; },
        async put(k, v) { store.set(k, v); },
        async delete(k) { store.delete(k); },
        async list({ prefix, limit = 1000 }) {
            const keys = [...store.keys()]
                .filter((k) => k.startsWith(prefix))
                .slice(0, limit)
                .map((name) => ({ name }));
            return { keys, list_complete: true };
        },
    };
}

const env = () => ({ IC_KV: fakeKv() });

function post(path, body) {
    return new Request(`https://lobby${path}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
    });
}

function get(path, method = "GET", body) {
    return new Request(`https://lobby${path}`, {
        method,
        headers: { "content-type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
    });
}

async function call(e, req) {
    const res = await worker.fetch(req, e);
    const json = await res.json();
    return { status: res.status, json };
}

// --- Elo ---

test("elo: even match moves 16 each way", () => {
    const r = eloUpdate(1000, 1000);
    assert.equal(r.winner, 1016);
    assert.equal(r.loser, 984);
});

test("elo: underdog gains more", () => {
    const r = eloUpdate(900, 1100);
    assert.ok(r.winner - 900 > 16, `expected upset bonus, got ${r.winner - 900}`);
});

// --- Names ---

test("sanitizeName strips control chars and caps at 24", () => {
    assert.equal(sanitizeName("Mathew"), "Mathew");
    assert.equal(sanitizeName("  \n Evil \t"), "Evil");
    assert.equal(sanitizeName("x".repeat(100)).length, 24);
    assert.equal(sanitizeName(""), "Player");
});

// --- Lobbies ---

test("lobby register/list/get/delete with owner check", async () => {
    const e = env();
    const reg = await call(e, post("/lobbies", {
        hostUuid: "aaaa", hostName: "Host One", hostRating: 1042,
        wssUrl: "wss://abc.trycloudflare.com", dataVersion: "ic-net-1",
    }));
    assert.equal(reg.status, 200);
    const code = reg.json.code;
    assert.match(code, /^[A-Z0-9]{6}$/);

    const list = await call(e, get("/lobbies"));
    assert.equal(list.json.length, 1);
    assert.equal(list.json[0].hostName, "Host One");
    assert.equal(list.json[0].hostRating, 1042);
    assert.equal(list.json[0].code, code);

    const one = await call(e, get(`/lobbies/${code}`));
    assert.equal(one.status, 200);
    assert.equal(one.json.wssUrl, "wss://abc.trycloudflare.com");

    // Wrong UUID cannot delete.
    const no = await call(e, get(`/lobbies/${code}`, "DELETE", { hostUuid: "bbbb" }));
    assert.equal(no.status, 403);
    const yes = await call(e, get(`/lobbies/${code}`, "DELETE", { hostUuid: "aaaa" }));
    assert.equal(yes.status, 200);
    const gone = await call(e, get(`/lobbies/${code}`));
    assert.equal(gone.status, 404);
});

// --- Quick match ---

test("quick match: earlier ticket hosts, later joins via pairing", async () => {
    const e = env();
    await call(e, post("/queue", { uuid: "11111111-1111-4111-8111-111111111111", name: "Alice", rating: 1000 }));
    await call(e, post("/queue", { uuid: "22222222-2222-4222-8222-222222222222", name: "Bob", rating: 1050 }));

    const alice = await call(e, get("/queue/poll?uuid=11111111-1111-4111-8111-111111111111"));
    assert.equal(alice.json.status, "host");
    assert.equal(alice.json.opponentUuid, "22222222-2222-4222-8222-222222222222");

    const bob = await call(e, get("/queue/poll?uuid=22222222-2222-4222-8222-222222222222"));
    assert.equal(bob.json.status, "waiting", "later ticket must not self-pair");

    const pair = await call(e, post("/pair", {
        hostUuid: "11111111-1111-4111-8111-111111111111", forUuid: "22222222-2222-4222-8222-222222222222",
        wssUrl: "wss://alice.trycloudflare.com", code: "XYZ",
        hostName: "Alice", hostRating: 1000,
    }));
    assert.equal(pair.json.ok, true);

    const bobReady = await call(e, get("/queue/poll?uuid=22222222-2222-4222-8222-222222222222"));
    assert.equal(bobReady.json.status, "ready");
    assert.equal(bobReady.json.wssUrl, "wss://alice.trycloudflare.com");
    assert.equal(bobReady.json.opponentName, "Alice");
});

// --- Ratings ---

test("dual report: first pending, second applies elo to both players", async () => {
    const e = env();
    const first = await call(e, post("/report", {
        matchId: "m1", reporterUuid: "33333333-3333-4333-8333-333333333333", winnerUuid: "33333333-3333-4333-8333-333333333333", loserUuid: "44444444-4444-4433-8444-444444444444",
        dataVersion: "ic-net-1",
    }));
    assert.equal(first.json.applied, false);

    const second = await call(e, post("/report", {
        matchId: "m1", reporterUuid: "44444444-4444-4433-8444-444444444444", winnerUuid: "33333333-3333-4333-8333-333333333333", loserUuid: "44444444-4444-4433-8444-444444444444",
        dataVersion: "ic-net-1",
    }));
    assert.equal(second.json.applied, true);
    assert.equal(second.json.rating, 984);
    assert.equal(second.json.delta, -16);

    const winner = await call(e, get("/rating/33333333-3333-4333-8333-333333333333"));
    assert.equal(winner.json.rating, 1016);
    assert.equal(winner.json.wins, 1);
    const loser = await call(e, get("/rating/44444444-4444-4433-8444-444444444444"));
    assert.equal(loser.json.rating, 984);
    assert.equal(loser.json.losses, 1);

    const board = await call(e, get("/leaderboard"));
    assert.equal(board.json.length, 2);
    assert.ok(!board.json.some((r) => "uuid" in r), "leaderboard must not expose UUIDs");
    assert.equal(board.json[0].rating, 1016);
});

test("dual report: disagreement is discarded and flagged", async () => {
    const e = env();
    await call(e, post("/report", {
        matchId: "m2", reporterUuid: "55555555-5555-4555-8555-555555555555", winnerUuid: "55555555-5555-4555-8555-555555555555", loserUuid: "66666666-6666-4666-8666-666666666666",
        dataVersion: "ic-net-1",
    }));
    const clash = await call(e, post("/report", {
        matchId: "m2", reporterUuid: "66666666-6666-4666-8666-666666666666", winnerUuid: "66666666-6666-4666-8666-666666666666", loserUuid: "55555555-5555-4555-8555-555555555555",
        dataVersion: "ic-net-1",
    }));
    assert.equal(clash.json.applied, false);
    assert.match(clash.json.reason, /disagree/);

    const x = await call(e, get("/rating/55555555-5555-4555-8555-555555555555"));
    assert.equal(x.json.rating, 1000, "no rating change on disagreement");
    const y = await call(e, get("/rating/66666666-6666-4666-8666-666666666666"));
    assert.equal(y.json.rating, 1000);

    const flag = await e.IC_KV.get("flag:m2");
    assert.ok(flag, "disagreement must be kept for review");
});

// --- IP privacy (tunnel mode must keep both players' direct IPs private) ---

test("wssUrlHasIpHost: hostnames pass, IP literals fail", () => {
    assert.equal(wssUrlHasIpHost("wss://abc.trycloudflare.com"), false);
    assert.equal(wssUrlHasIpHost("wss://abc.trycloudflare.com/game"), false);
    assert.equal(wssUrlHasIpHost("wss://203.0.113.7"), true);
    assert.equal(wssUrlHasIpHost("wss://203.0.113.7:8443/x"), true);
    assert.equal(wssUrlHasIpHost("wss://user@203.0.113.7/x"), true);
    assert.equal(wssUrlHasIpHost("wss://[2001:db8::1]/"), true);
    assert.equal(wssUrlHasIpHost("wss://[2001:db8::1]:8443/x"), true);
    assert.equal(wssUrlHasIpHost("http://abc.trycloudflare.com"), true, "non-wss rejected");
    assert.equal(wssUrlHasIpHost("garbage"), true);
    assert.equal(wssUrlHasIpHost(null), true);
});

test("lobby register rejects IP-literal tunnel URLs", async () => {
    const e = env();
    for (const bad of [
        "wss://203.0.113.7/",
        "wss://203.0.113.7:8443/game",
        "wss://user@198.51.100.23/x",
        "wss://[2001:db8::1]/",
        "wss://[2001:db8::1]:8443/x",
    ]) {
        const res = await call(e, post("/lobbies", {
            hostUuid: "aaaa", hostName: "Sneaky", hostRating: 1000, wssUrl: bad,
        }));
        assert.equal(res.status, 400, `expected 400 for ${bad}`);
        assert.match(res.json.error, /IP/i);
    }
    // A genuine tunnel hostname still registers.
    const ok = await call(e, post("/lobbies", {
        hostUuid: "aaaa", hostName: "Honest", hostRating: 1000,
        wssUrl: "wss://good-42.trycloudflare.com",
    }));
    assert.equal(ok.status, 200);
});

test("quick-match pairing rejects IP-literal tunnel URLs", async () => {
    const e = env();
    const res = await call(e, post("/pair", {
        hostUuid: "11111111-1111-4111-8111-111111111111",
        forUuid: "22222222-2222-4222-8222-222222222222",
        wssUrl: "wss://203.0.113.7:8443",
        code: "XYZ", hostName: "Sneaky", hostRating: 1000,
    }));
    assert.equal(res.status, 400);
    assert.match(res.json.error, /IP/i);
});

// Matches dotted quads (IPv4) and bracketed or bare hex-colon groups (IPv6).
const IPV4 = /(?:\d{1,3}\.){3}\d{1,3}/;
const IPV6 = /\[[0-9a-fA-F:]{2,}\]|(?:^|[^0-9a-fA-F])(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F:]*:[0-9a-fA-F]{0,4}(?:[^0-9a-fA-F:]|$)/;

function assertNoIp(payload, where) {
    const text = JSON.stringify(payload);
    assert.ok(!IPV4.test(text), `${where} must not contain an IPv4 address: ${text}`);
    assert.ok(!IPV6.test(text), `${where} must not contain an IPv6 address: ${text}`);
}

test("no worker payload ever contains an IP address", async () => {
    const e = env();
    const uuidA = "aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa";
    const uuidB = "bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb";

    // Full public flow: register, list, get, queue, poll, pair, dual report.
    const reg = await call(e, post("/lobbies", {
        hostUuid: uuidA, hostName: "Tunnel Host", hostRating: 1042,
        wssUrl: "wss://epic-name-99.trycloudflare.com", dataVersion: "ic-net-2",
    }));
    assert.equal(reg.status, 200);
    const code = reg.json.code;
    assertNoIp(reg.json, "lobby register response");

    const list = await call(e, get("/lobbies"));
    assertNoIp(list.json, "lobby listing");
    assert.equal(list.json.length, 1);

    const one = await call(e, get(`/lobbies/${code}`));
    assertNoIp(one.json, "lobby get");

    await call(e, post("/queue", { uuid: uuidA, name: "Alice", rating: 1000 }));
    await call(e, post("/queue", { uuid: uuidB, name: "Bob", rating: 1050 }));
    const alice = await call(e, get(`/queue/poll?uuid=${uuidA}`));
    assertNoIp(alice.json, "queue poll (host)");
    const pair = await call(e, post("/pair", {
        hostUuid: uuidA, forUuid: uuidB, wssUrl: "wss://epic-name-99.trycloudflare.com",
        code: "QM1", hostName: "Alice", hostRating: 1000,
    }));
    assert.equal(pair.json.ok, true);
    const bobReady = await call(e, get(`/queue/poll?uuid=${uuidB}`));
    assertNoIp(bobReady.json, "queue poll (ready)");

    const reportBody = (reporter, winner, loser) => ({
        matchId: "ip-m1", reporterUuid: reporter, winnerUuid: winner, loserUuid: loser,
        dataVersion: "ic-net-2",
    });
    const r1 = await call(e, post("/report", reportBody(uuidA, uuidA, uuidB)));
    assertNoIp(r1.json, "first report response");
    const r2 = await call(e, post("/report", reportBody(uuidB, uuidA, uuidB)));
    assertNoIp(r2.json, "second report response");
    assert.equal(r2.json.applied, true);

    const rating = await call(e, get(`/rating/${uuidA}`));
    assertNoIp(rating.json, "rating record");
    const board = await call(e, get("/leaderboard?limit=25"));
    assertNoIp(board.json, "leaderboard");

    // Even a hostile register attempt that slips a raw IP into the *name*
    // field must not surface as an IP-looking payload entry.
    const evil = await call(e, post("/lobbies", {
        hostUuid: uuidB, hostName: "call me 203.0.113.7", hostRating: 1000,
        wssUrl: "wss://other-1.trycloudflare.com",
    }));
    assert.equal(evil.status, 200);
    const list2 = await call(e, get("/lobbies"));
    const evilEntry = list2.json.find((x) => x.code === evil.json.code);
    assert.ok(evilEntry, "evil lobby should be listed");
    // Names are public labels; the tunnel URL fields must stay clean.
    assertNoIp({ wssUrl: evilEntry.wssUrl }, "lobby entry wssUrl after hostile name");
    assert.equal(evilEntry.wssUrl, "wss://other-1.trycloudflare.com");
});
