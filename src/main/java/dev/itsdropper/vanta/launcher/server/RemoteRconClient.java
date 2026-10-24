/*
 * Copyright (c) 2026 ItsDropper
 * Licensed under the MIT License.
 */
package dev.itsdropper.vanta.launcher.server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal Minecraft Java RCON client. Passwords are kept only in memory. */
public final class RemoteRconClient implements AutoCloseable {
    private static final int MAX_PACKET = 4096;
    private final Socket socket = new Socket();
    private final DataInputStream input;
    private final DataOutputStream output;
    private final AtomicInteger nextId = new AtomicInteger(10);
    private volatile boolean authenticated;

    public RemoteRconClient(String host, int port, String password) throws IOException {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host is required.");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("RCON port must be between 1 and 65535.");
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("RCON password is required.");
        socket.connect(new InetSocketAddress(host.trim(), port), 6000);
        socket.setSoTimeout(6000);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        int id = nextId.getAndIncrement();
        writePacket(id, 3, password);
        Packet response = readPacket();
        if (response.id == -1) throw new IOException("RCON authentication failed. Check the password and ensure enable-rcon=true.");
        if (response.id != id) throw new IOException("Unexpected RCON authentication response.");
        authenticated = true;
    }

    public synchronized String command(String command) throws IOException {
        if (!authenticated || socket.isClosed()) throw new IOException("Remote RCON connection is closed.");
        if (command == null || command.isBlank()) return "";
        String value = command.trim();
        if (value.length() > 2048 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Enter one command of up to 2048 characters.");
        }
        int id = nextId.getAndIncrement();
        writePacket(id, 2, value);
        Packet response = readPacket();
        if (response.id != id) throw new IOException("RCON response did not match the command. Reconnect and try again.");
        return response.payload;
    }

    private void writePacket(int id, int type, String payload) throws IOException {
        byte[] text = payload.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + text.length + 2;
        output.writeInt(Integer.reverseBytes(length));
        output.writeInt(Integer.reverseBytes(id));
        output.writeInt(Integer.reverseBytes(type));
        output.write(text);
        output.writeByte(0);
        output.writeByte(0);
        output.flush();
    }

    private Packet readPacket() throws IOException {
        int length;
        try { length = Integer.reverseBytes(input.readInt()); }
        catch (EOFException e) { throw new IOException("Remote server closed the RCON connection.", e); }
        if (length < 10 || length > MAX_PACKET) throw new IOException("Invalid RCON packet size: " + length);
        byte[] packet = input.readNBytes(length);
        if (packet.length != length) throw new EOFException("Incomplete RCON response.");
        int id = Integer.reverseBytes(readLittleEndianInt(packet, 0));
        int type = Integer.reverseBytes(readLittleEndianInt(packet, 4));
        int payloadEnd = length - 2;
        String payload = new String(packet, 8, Math.max(0, payloadEnd - 8), StandardCharsets.UTF_8);
        return new Packet(id, type, payload);
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
    }

    private record Packet(int id, int type, String payload) {}

    @Override public void close() {
        authenticated = false;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
