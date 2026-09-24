package com.infiniteconquest.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the WebSocket&lt;-&gt;TCP pump with piped streams: no sockets, no
 * networking. A fake "game server" speaks protocol lines on the pipes.
 */
class WsBridgeTest {
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pumpBridgesBothDirections() throws Exception {
        // WS side: test writes masked frames in, reads server frames out.
        PipedOutputStream wsWrite = new PipedOutputStream();
        PipedInputStream wsIn = new PipedInputStream(wsWrite);
        PipedOutputStream wsOutPipe = new PipedOutputStream();
        PipedInputStream wsOut = new PipedInputStream(wsOutPipe);
        // Game side: test feeds protocol lines in, reads them out.
        PipedOutputStream gameWrite = new PipedOutputStream();
        PipedInputStream gameIn = new PipedInputStream(gameWrite);
        ByteArrayOutputStream gameOut = new ByteArrayOutputStream();

        Thread pump = new Thread(
                () -> WsBridge.pump(wsIn, wsOutPipe, gameIn, gameOut), "test-pump");
        pump.setDaemon(true);
        pump.start();

        // Downstream: WS text message -> game line.
        writeMaskedText(wsWrite, "{\"type\":\"command\",\"text\":\"endturn\"}");
        assertTrue(waitFor(() -> gameOut.size() > 0, 5000), "game should receive the command");
        assertEquals("{\"type\":\"command\",\"text\":\"endturn\"}\n",
                gameOut.toString(StandardCharsets.UTF_8));

        // Upstream: game line -> WS text frame.
        gameWrite.write("{\"type\":\"lobby\",\"players\":[]}\n".getBytes(StandardCharsets.UTF_8));
        gameWrite.flush();
        WsCodec.Frame frame = WsCodec.readFrame(wsOut);
        assertEquals(WsCodec.OP_TEXT, frame.opcode());
        assertEquals("{\"type\":\"lobby\",\"players\":[]}",
                new String(frame.payload(), StandardCharsets.UTF_8));

        // Close frame ends the pump.
        writeClose(wsWrite);
        pump.join(5000);
        assertFalse(pump.isAlive(), "pump should stop on close frame");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void pumpHandlesSplitLines() throws Exception {
        PipedOutputStream wsWrite = new PipedOutputStream();
        PipedInputStream wsIn = new PipedInputStream(wsWrite);
        ByteArrayOutputStream wsOut = new ByteArrayOutputStream();
        PipedOutputStream gameWrite = new PipedOutputStream();
        PipedInputStream gameIn = new PipedInputStream(gameWrite);
        ByteArrayOutputStream gameOut = new ByteArrayOutputStream();

        Thread pump = new Thread(
                () -> WsBridge.pump(wsIn, wsOut, gameIn, gameOut), "test-pump-split");
        pump.setDaemon(true);
        pump.start();

        // A game line arriving in two TCP chunks becomes one WS frame.
        gameWrite.write("{\"type\":\"a\"".getBytes(StandardCharsets.UTF_8));
        gameWrite.flush();
        Thread.sleep(200);
        gameWrite.write(",\"b\":1}\n".getBytes(StandardCharsets.UTF_8));
        gameWrite.flush();
        long deadline = System.currentTimeMillis() + 5000;
        WsCodec.Frame frame = null;
        ByteArrayAccumulator acc = new ByteArrayAccumulator(wsOut);
        while (System.currentTimeMillis() < deadline && frame == null) {
            byte[] bytes = acc.drain();
            if (bytes.length > 0) {
                frame = WsCodec.readFrame(new java.io.ByteArrayInputStream(bytes));
            } else {
                Thread.sleep(50);
            }
        }
        assertNotNull(frame, "expected a WS frame for the split line");
        assertEquals("{\"type\":\"a\",\"b\":1}", new String(frame.payload(), StandardCharsets.UTF_8));

        writeClose(wsWrite);
        pump.join(5000);
        assertFalse(pump.isAlive());
    }

    private static void writeMaskedText(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x11, 0x22, 0x33, 0x44};
        out.write(0x81); // FIN + text
        if (payload.length < 126) {
            out.write(0x80 | payload.length);
        } else {
            out.write(0x80 | 126);
            out.write((payload.length >>> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        }
        out.write(mask);
        for (int i = 0; i < payload.length; i++) out.write(payload[i] ^ mask[i % 4]);
        out.flush();
    }

    private static void writeClose(OutputStream out) throws IOException {
        byte[] mask = {0x55, 0x66, 0x77, (byte) 0x88};
        out.write(0x88); // FIN + close
        out.write(0x80); // masked, empty
        out.write(mask);
        out.flush();
    }

    private static boolean waitFor(Check check, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (check.ok()) return true;
            Thread.sleep(25);
        }
        return check.ok();
    }

    private interface Check { boolean ok(); }

    /** Drains whatever a ByteArrayOutputStream has accumulated so far. */
    private static final class ByteArrayAccumulator {
        private final ByteArrayOutputStream out;
        private int consumed;

        ByteArrayAccumulator(ByteArrayOutputStream out) { this.out = out; }

        synchronized byte[] drain() {
            byte[] all = out.toByteArray();
            byte[] next = new byte[all.length - consumed];
            System.arraycopy(all, consumed, next, 0, next.length);
            consumed = all.length;
            return next;
        }
    }

    @Test
    void byteArrayCollectorTakesLines() {
        WsBridge.ByteArrayCollector collector = new WsBridge.ByteArrayCollector();
        collector.keep(new byte[]{'a', 'b'}, 0, 2);
        byte[] line = collector.take(new byte[]{'c', '\n', 'd'}, 0, 1);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, line);
    }
}
