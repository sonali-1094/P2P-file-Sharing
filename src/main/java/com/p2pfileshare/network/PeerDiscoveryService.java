package com.p2pfileshare.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pfileshare.model.Peer;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerDiscoveryService {
    public static final int DISCOVERY_PORT = 49001;
    public static final String DISCOVERY_GROUP = "255.255.255.255";
    private static final int BUFFER_SIZE = 1024;
    private final int announcedPort;
    private final String localPeerId = "peer-" + UUID.randomUUID();
    private final ExecutorService discoveryExecutor = Executors.newFixedThreadPool(2, r -> new Thread(r, "peer-discovery"));
    private volatile boolean running;
    private final Set<Peer> discoveredPeers = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PeerDiscoveryService(int announcedPort) {
        this.announcedPort = announcedPort;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        discoveryExecutor.submit(this::listen);
        discoveryExecutor.submit(() -> {
            while (running) {
                announce();
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void stop() {
        running = false;
        discoveryExecutor.shutdownNow();
    }

    public Set<Peer> getDiscoveredPeers() {
        return Set.copyOf(discoveredPeers);
    }

    private void announce() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String payload = objectMapper.writeValueAsString(new DiscoveryPayload(localPeerId, announcedPort));
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(DISCOVERY_GROUP), DISCOVERY_PORT);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Failed to broadcast discovery: " + e.getMessage());
        }
    }

    private void listen() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setSoTimeout(2000);
            while (running) {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    DiscoveryPayload payload = objectMapper.readValue(json, DiscoveryPayload.class);
                    if (!localPeerId.equals(payload.peerId())) {
                        Peer peer = new Peer(payload.peerId(), packet.getAddress(), payload.port(), "1.0", new String[]{"chunking", "manifest"});
                        discoveredPeers.add(peer);
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    System.err.println("Discovery listener error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Cannot open discovery socket: " + e.getMessage());
        }
    }

    private record DiscoveryPayload(String peerId, int port) {
    }
}
