package com.p2pfileshare.model;

import java.net.InetAddress;
import java.util.Objects;

public class Peer {
    private final String peerId;
    private final InetAddress address;
    private final int port;
    private final String version;
    private final String[] capabilities;
    private volatile PeerState state = PeerState.DISCONNECTED;

    public Peer(String peerId, InetAddress address, int port, String version, String[] capabilities) {
        this.peerId = peerId;
        this.address = address;
        this.port = port;
        this.version = version;
        this.capabilities = capabilities;
    }

    public String getPeerId() {
        return peerId;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getVersion() {
        return version;
    }

    public String[] getCapabilities() {
        return capabilities;
    }

    public PeerState getState() {
        return state;
    }

    public void setState(PeerState state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return port == peer.port && Objects.equals(peerId, peer.peerId) && Objects.equals(address, peer.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, address, port);
    }

    @Override
    public String toString() {
        return peerId + "@" + address.getHostAddress() + ":" + port + " [" + state + "]";
    }
}
