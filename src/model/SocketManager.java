package model;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class SocketManager {
    private final DatagramSocket socket = new DatagramSocket();
    private final InetAddress inetAddress;
    private final int port;

    public SocketManager(InetAddress inetAddress, int port) throws SocketException {
        this.inetAddress = inetAddress;
        this.port = port;
    }

    public void send(RequestType req) throws IOException {
        final byte[] data = req.toByteArray();
        socket.send(new DatagramPacket(data, data.length, inetAddress, port));
    }

    public DatagramPacket receive(byte[] data) throws IOException {
        final DatagramPacket receivePacket = new DatagramPacket(data, data.length);
        socket.receive(receivePacket);
        return receivePacket;
    }
}
