package com.infiniteconquest.net;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RFC 6455 WebSocket codec for the tunnel bridge.
 *
 * <p>The host's game cannot speak raw TCP to the internet: cloudflared quick
 * tunnels forward HTTP(S) only, so the host exposes a WebSocket endpoint on
 * loopback and cloudflared forwards {@code wss://} guest connections to it.
 * This codec implements just enough of the protocol for that bridge: the
 * server-side opening handshake, masked client frames, unmasked server
 * frames, text messages (with fragmentation), ping/pong, and close.
 *
 * <p>All methods are pure stream operations with no sockets, so the whole
 * codec is unit-testable without networking.
 */
public final class WsCodec {
    public static final int OP_CONTINUATION = 0x0;
    public static final int OP_TEXT = 0x1;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    /** Largest single frame payload the bridge accepts (64 KB, matches the relay cap). */
    public static final int MAX_FRAME = 64 * 1024;

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WsCodec() {}

    /** One decoded frame. */
    public record Frame(int opcode, boolean fin, byte[] payload) {}

    /**
     * Computes the {@code Sec-WebSocket-Accept} value for a client key.
     * RFC 6455 test vector: key {@code "dGhlIHNhbXBsZSBub25jZQ=="} yields
     * {@code "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="}.
     */
    public static String acceptKey(String clientKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((clientKey.trim() + WS_MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /**
     * Performs the server side of the opening handshake: reads the HTTP
     * upgrade request from {@code in} and writes the {@code 101} response to
     * {@code out}. Returns the request headers. Throws {@link IOException}
     * when the request is not a WebSocket upgrade.
     */
    public static Map<String, String> serverHandshake(InputStream in, OutputStream out) throws IOException {
        String request = readHttpHeaders(in);
        String[] lines = request.split("\r\n");
        if (lines.length == 0 || !lines[0].toUpperCase(Locale.ROOT).contains("GET "))
            throw new IOException("Not a WebSocket upgrade request");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0)
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
        }
        String upgrade = headers.getOrDefault("upgrade", "");
        String key = headers.get("sec-websocket-key");
        if (!"websocket".equalsIgnoreCase(upgrade) || key == null || key.isEmpty())
            throw new IOException("Missing WebSocket upgrade headers");
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey(key) + "\r\n"
                + "\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return headers;
    }

    private static String readHttpHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int[] tail = new int[4];
        int read = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("Connection closed during handshake");
            buffer.write(b);
            tail[read % 4] = b;
            read++;
            if (read >= 4 && tail[(read - 4) % 4] == '\r' && tail[(read - 3) % 4] == '\n'
                    && tail[(read - 2) % 4] == '\r' && tail[(read - 1) % 4] == '\n')
                break;
            if (buffer.size() > 16 * 1024) throw new IOException("Handshake headers too large");
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    /**
     * Reads one frame. When {@code requireMasked} is true (server side,
     * per RFC 6455 section 5.1), unmasked frames are rejected. Control
     * frames are returned like any other frame so the caller can answer
     * ping/pong/close.
     */
    public static Frame readFrame(InputStream in, boolean requireMasked) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) throw new EOFException("Connection closed mid-frame");
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        if (requireMasked && !masked) throw new IOException("Unmasked client frame rejected");
        long length = b1 & 0x7F;
        if (length == 126) {
            length = ((in.read() & 0xFFL) << 8) | (in.read() & 0xFFL);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) length = (length << 8) | (in.read() & 0xFFL);
        }
        if (length < 0 || length > MAX_FRAME) throw new IOException("Frame too large: " + length);
        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask, 0, 4);
        }
        byte[] payload = new byte[(int) length];
        readFully(in, payload, 0, payload.length);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        return new Frame(opcode, fin, payload);
    }

    /** Reads one frame without a masking requirement (client-side decoding). */
    public static Frame readFrame(InputStream in) throws IOException {
        return readFrame(in, false);
    }

    /**
     * Reads frames until a complete text message is assembled (handling
     * fragmentation). Answers pings with pongs on {@code out}. Returns the
     * message bytes, or {@code null} on a close frame / end of stream.
     */
    public static byte[] readTextMessage(InputStream in, OutputStream out) throws IOException {
        return readTextMessage(in, out, false);
    }

    /**
     * Like {@link #readTextMessage(InputStream, OutputStream)}, but the bridge
     * passes {@code requireMasked=true} so unmasked client frames are
     * rejected per RFC 6455.
     */
    public static byte[] readTextMessage(InputStream in, OutputStream out, boolean requireMasked)
            throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        boolean inMessage = false;
        while (true) {
            Frame frame;
            try {
                frame = readFrame(in, requireMasked);
            } catch (EOFException e) {
                return null;
            }
            switch (frame.opcode()) {
                case OP_TEXT -> {
                    inMessage = true;
                    message.write(frame.payload());
                    if (frame.fin()) return message.toByteArray();
                }
                case OP_CONTINUATION -> {
                    if (!inMessage) throw new IOException("Stray continuation frame");
                    message.write(frame.payload());
                    if (frame.fin()) return message.toByteArray();
                }
                case OP_PING -> writePong(out, frame.payload());
                case OP_PONG -> { /* ignore */ }
                case OP_CLOSE -> {
                    writeClose(out);
                    return null;
                }
                default -> throw new IOException("Unsupported opcode: " + frame.opcode());
            }
            if (message.size() > MAX_FRAME) throw new IOException("Message too large");
        }
    }

    /** Writes one unmasked text frame (server to client). */
    public static void writeTextFrame(OutputStream out, byte[] utf8) throws IOException {
        writeFrame(out, OP_TEXT, utf8);
    }

    public static void writeTextFrame(OutputStream out, String text) throws IOException {
        writeTextFrame(out, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void writePong(OutputStream out, byte[] payload) throws IOException {
        writeFrame(out, OP_PONG, payload);
    }

    public static void writeClose(OutputStream out) throws IOException {
        writeFrame(out, OP_CLOSE, new byte[0]);
    }

    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        if (payload.length > MAX_FRAME) throw new IOException("Frame too large");
        out.write(0x80 | (opcode & 0x0F));
        if (payload.length < 126) {
            out.write(payload.length);
        } else {
            out.write(126);
            out.write((payload.length >>> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        }
        out.write(payload);
        out.flush();
    }

    private static void readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        while (length > 0) {
            int n = in.read(buffer, offset, length);
            if (n < 0) throw new EOFException("Connection closed mid-frame");
            offset += n;
            length -= n;
        }
    }
}
