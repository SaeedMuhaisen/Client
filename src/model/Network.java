package model;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Network {
    private final InetAddress IPAdress;
    private int port;

    public Network(String ip, int port) throws UnknownHostException {
        this.IPAdress = InetAddress.getByName(ip);
        this.port = port;
    }

    public InetAddress getIp() {
        return IPAdress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
