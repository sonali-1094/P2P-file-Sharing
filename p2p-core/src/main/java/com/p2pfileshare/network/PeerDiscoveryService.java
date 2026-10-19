package com.p2pfileshare.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pfileshare.model.Peer;

public class PeerDiscoveryService {
    public static final int DISCOVERY_PORT = 49001;
    public static final String BROADCAST_ADDRESS = "255.255.255.255"; // FIX: renamed, this is broadcast not multicast
    private static final int BUFFER_SIZE = 1024;
    private static final long PEER_TTL_MILLIS = 15_000; // FIX: expire peers not seen in 15s
    private final int announcedPort;
    private final String localPeerId;
    private final ExecutorService discoveryExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "peer-discovery");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;
    private final ConcurrentHashMap<Peer, Long> lastSeen = new ConcurrentHashMap<>(); // FIX: track freshness
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PeerDiscoveryService(int announcedPort, String localPeerId) {
        this.announcedPort = announcedPort;
        this.localPeerId = localPeerId;
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
                    return;
                }
            }
        });
        // FIX: periodic sweep to drop peers we haven't heard from recently
        discoveryExecutor.submit(() -> {
            while (running) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long now = System.currentTimeMillis();
                lastSeen.entrySet().removeIf(entry -> now - entry.getValue() > PEER_TTL_MILLIS);
            }
        });
    }

    public void stop() {
        running = false;
        discoveryExecutor.shutdownNow();
    }

    public Set<Peer> getDiscoveredPeers() {
        return Set.copyOf(lastSeen.keySet());
    }

    private void announce() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String payload = objectMapper.writeValueAsString(new DiscoveryPayload(localPeerId, announcedPort));
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(BROADCAST_ADDRESS), DISCOVERY_PORT);
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
                    if (payload.peerId() != null && !localPeerId.equals(payload.peerId())) {
                        Peer peer = new Peer(payload.peerId(), packet.getAddress(), payload.port(), "1.0", new String[]{"chunking", "manifest"});
                        lastSeen.put(peer, System.currentTimeMillis()); // FIX: refresh timestamp on every sighting
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