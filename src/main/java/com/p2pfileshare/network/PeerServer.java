package com.p2pfileshare.network;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2pfileshare.model.FileManifest;

public class PeerServer {
    private final int port;
    private final Map<String, FileManifest> sharedManifests;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;
    // FIX: volatile so stop() always sees an up-to-date reference
    private volatile ServerSocket serverSocket;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PeerServer(int port, Map<String, FileManifest> sharedManifests) {
        this.port = port;
        this.sharedManifests = sharedManifests;
    }

    // FIX: open the socket synchronously in start() so stop() can never
    // observe a null serverSocket after start() has returned.
    public void start() throws IOException {
        if (running) {
            return;
        }
        running = true;
        serverSocket = new ServerSocket(port);
        System.out.println("Peer server listening on port " + port);
        executor.submit(this::serveLoop);
    }

    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close(); // unblocks accept() immediately
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
    }

    private void serveLoop() {
        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("PeerServer failure: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket; OutputStream os = socket.getOutputStream(); InputStream is = socket.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                return;
            }

            JsonNode request = objectMapper.readTree(firstLine);
            String type = request.path("type").asText("");
            if ("GET_MANIFESTS".equals(type)) {
                List<NetworkFileManifest> manifests = sharedManifests.values().stream()
                        .map(NetworkFileManifest::from)
                        .toList();
                writer.write(objectMapper.writeValueAsString(manifests));
                writer.newLine();
                writer.flush();
                return;
            }
            if ("GET_CHUNK".equals(type)) {
                String fileId = request.path("fileId").asText();
                int chunkIndex = request.path("chunkIndex").asInt();
                FileManifest manifest = sharedManifests.get(fileId);
                if (manifest != null) {
                    sendChunk(manifest, chunkIndex, os);
                }
                return;
            }

            HandshakeMessage handshake = objectMapper.readValue(firstLine, HandshakeMessage.class);
            System.out.println("Incoming handshake from " + handshake.getPeerId() + " " + socket.getRemoteSocketAddress());
            HandshakeMessage response = new HandshakeMessage("server-peer", "1.0", new String[]{"manifest", "chunk-transfer"});
            writer.write(objectMapper.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private void sendChunk(FileManifest manifest, int index, OutputStream outputStream) throws IOException {
        if (index < 0 || index >= manifest.getChunks().size()) {
            return;
        }
        byte[] buffer = new byte[8192];
        var chunk = manifest.getChunks().get(index);
        try (RandomAccessFile raf = new RandomAccessFile(manifest.getFilePath().toFile(), "r");
             BufferedOutputStream bos = new BufferedOutputStream(outputStream)) {
            raf.seek(chunk.getOffset());
            int remaining = chunk.getSize();
            while (remaining > 0) {
                int read = raf.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) break;
                bos.write(buffer, 0, read);
                remaining -= read;
            }
            bos.flush();
        }
    }
}