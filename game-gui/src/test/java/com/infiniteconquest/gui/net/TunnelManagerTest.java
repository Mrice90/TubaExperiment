package com.infiniteconquest.gui.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for tunnel URL parsing (pure functions; no cloudflared needed). */
class TunnelManagerTest {
    @Test
    void extractsUrlFromBoxedLogLine() {
        String line = "2026-09-24T12:00:00Z INF |  https://random-name-1234.trycloudflare.com"
                + "                                                 |";
        assertEquals("https://random-name-1234.trycloudflare.com",
                TunnelManager.extractTunnelUrl(line));
    }

    @Test
    void extractsUrlFromPlainLogLine() {
        assertEquals("https://abc-def-42.trycloudflare.com",
                TunnelManager.extractTunnelUrl(
                        "INF Your quick Tunnel has been created! Visit it at (lines): "
                                + "https://abc-def-42.trycloudflare.com"));
    }

    @Test
    void ignoresNonTunnelLines() {
        assertNull(TunnelManager.extractTunnelUrl(
                "2026-09-24T12:00:00Z INF Registered tunnel connection conn-1"));
        assertNull(TunnelManager.extractTunnelUrl(""));
        assertNull(TunnelManager.extractTunnelUrl(null));
        assertNull(TunnelManager.extractTunnelUrl("https://example.com/page"));
    }

    @Test
    void toWssUrlConvertsScheme() {
        assertEquals("wss://abc-123.trycloudflare.com",
                TunnelManager.toWssUrl("https://abc-123.trycloudflare.com"));
        assertEquals("wss://abc-123.trycloudflare.com/game",
                TunnelManager.toWssUrl("https://abc-123.trycloudflare.com/game"));
    }
}
