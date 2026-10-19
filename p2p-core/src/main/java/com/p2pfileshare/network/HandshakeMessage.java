package com.p2pfileshare.network;

import java.util.Objects;

public class HandshakeMessage {
    private String peerId;
    private String version;
    private String[] capabilities;

    public HandshakeMessage() {
    }

    public HandshakeMessage(String peerId, String version, String[] capabilities) {
        this.peerId = peerId;
        this.version = version;
        this.capabilities = capabilities;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String[] getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String[] capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String toString() {
        return "HandshakeMessage{" +
                "peerId='" + peerId + '\'' +
                ", version='" + version + '\'' +
                ", capabilities=" + (capabilities == null ? null : String.join(",", capabilities)) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HandshakeMessage)) return false;
        HandshakeMessage that = (HandshakeMessage) o;
        return Objects.equals(peerId, that.peerId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, version);
    }
}
