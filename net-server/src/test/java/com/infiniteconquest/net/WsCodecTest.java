package com.infiniteconquest.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the WebSocket codec. All stream-based: no sockets needed.
 */
class WsCodecTest {
    @Test
    void rfc6455AcceptVector() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                WsCodec.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void serverHandshakeRoundTrip() throws IOException {
        String request = "GET / HTTP/1.1\r\n"
                + "Host: 127.0.0.1:1234\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";
        ByteArrayInputStream in = new ByteArrayInputStream(request.getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> headers = WsCodec.serverHandshake(in, out);
        assertEquals("websocket", headers.get("upgrade"));
        String response = out.toString(StandardCharsets.US_ASCII);
        assertTrue(response.startsWith("HTTP/1.1 101"), "expected 101, got: " + response);
        assertTrue(response.contains("s3pPLMBiTxaQ9kYGzzhZRbK+xOo="), "accept key echoed");
    }

    @Test
    void handshakeRejectsNonUpgrade() {
        String request = "GET / HTTP/1.1\r\nHost: x\r\n\r\n";
        ByteArrayInputStream in = new ByteArrayInputStream(request.getBytes(StandardCharsets.US_ASCII));
        assertThrows(IOException.class,
                () -> WsCodec.serverHandshake(in, new ByteArrayOutputStream()));
    }

    @Test
    void textFrameRoundTrip() throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        WsCodec.writeTextFrame(wire, "hello tunnel");
        WsCodec.Frame frame = WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()));
        assertEquals(WsCodec.OP_TEXT, frame.opcode());
        assertTrue(frame.fin());
        assertEquals("hello tunnel", new String(frame.payload(), StandardCharsets.UTF_8));
    }

    @Test
    void maskedClientFrameDecodes() throws IOException {
        // Build a client-style masked frame by hand: "Hi" masked with 0x37fa213d.
        byte[] payload = "Hi".getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x37, (byte) 0xfa, 0x21, 0x3d};
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x81); // FIN + text
        wire.write(0x80 | payload.length); // masked, len 2
        wire.write(mask);
        for (int i = 0; i < payload.length; i++) wire.write(payload[i] ^ mask[i % 4]);
        WsCodec.Frame frame = WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()));
        assertEquals("Hi", new String(frame.payload(), StandardCharsets.UTF_8));
    }

    @Test
    void extendedLengthFrame() throws IOException {
        byte[] big = new byte[300];
        for (int i = 0; i < big.length; i++) big[i] = (byte) ('a' + (i % 26));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        WsCodec.writeTextFrame(wire, big);
        WsCodec.Frame frame = WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()));
        assertArrayEquals(big, frame.payload());
    }

    @Test
    void readTextMessageAnswersPingAndAssemblesFragments() throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        // Fragment 1: FIN=0, text, "hel"
        wire.write(0x01);
        writeMasked(wire, "hel".getBytes(StandardCharsets.UTF_8));
        // A ping in the middle (server must pong, not deliver)
        wire.write(0x89);
        writeMasked(wire, "ping".getBytes(StandardCharsets.UTF_8));
        // Fragment 2: FIN=1, continuation, "lo"
        wire.write(0x80);
        writeMasked(wire, "lo".getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream in = new ByteArrayInputStream(wire.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] message = WsCodec.readTextMessage(in, out);
        assertEquals("hello", new String(message, StandardCharsets.UTF_8));
        // The pong must be an unmasked server frame with the ping payload.
        WsCodec.Frame pong = WsCodec.readFrame(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(WsCodec.OP_PONG, pong.opcode());
        assertEquals("ping", new String(pong.payload(), StandardCharsets.UTF_8));
    }

    @Test
    void readTextMessageReturnsNullOnClose() throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x88);
        writeMasked(wire, new byte[0]);
        byte[] message = WsCodec.readTextMessage(
                new ByteArrayInputStream(wire.toByteArray()), new ByteArrayOutputStream());
        assertNull(message);
    }

    @Test
    void maskedClientFrameAcceptedWhenRequired() throws Exception {
        byte[] payload = "hi".getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x0A, 0x0B, 0x0C, 0x0D};
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x81);
        wire.write(0x80 | payload.length);
        wire.write(mask);
        for (int i = 0; i < payload.length; i++) wire.write(payload[i] ^ mask[i % 4]);
        WsCodec.Frame frame =
                WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()), true);
        assertArrayEquals(payload, frame.payload());
    }

    @Test
    void unmaskedClientFrameRejectedWhenRequired() {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x81);
        wire.write(2); // no mask bit
        wire.write('h');
        wire.write('i');
        assertThrows(IOException.class,
                () -> WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()), true));
    }

    @Test
    void unmaskedFrameAcceptedWhenNotRequired() throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x81);
        wire.write(2);
        wire.write('h');
        wire.write('i');
        WsCodec.Frame frame =
                WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray()), false);
        assertArrayEquals(new byte[]{'h', 'i'}, frame.payload());
    }

    @Test
    void readTextMessageRejectsUnmaskedWhenRequired() {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x81);
        wire.write(2);
        wire.write('h');
        wire.write('i');
        assertThrows(IOException.class, () -> WsCodec.readTextMessage(
                new ByteArrayInputStream(wire.toByteArray()), new ByteArrayOutputStream(), true));
    }

    @Test
    void oversizedFrameRejected() {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0x82); // FIN + binary
        wire.write(0xFF); // masked + 127: 8-byte length follows
        for (int i = 0; i < 8; i++) wire.write(0xFF);
        assertThrows(IOException.class,
                () -> WsCodec.readFrame(new ByteArrayInputStream(wire.toByteArray())));
    }

    private static void writeMasked(ByteArrayOutputStream wire, byte[] payload) throws IOException {
        byte[] mask = {0x12, 0x34, 0x56, 0x78};
        wire.write(0x80 | payload.length);
        wire.write(mask);
        for (int i = 0; i < payload.length; i++) wire.write(payload[i] ^ mask[i % 4]);
    }
}
