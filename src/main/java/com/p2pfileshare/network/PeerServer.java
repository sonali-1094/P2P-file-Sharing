package com.p2pfileshare.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.p2pfileshare.model.FileManifest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerServer {
    private final int port;
    private final Map<String, FileManifest> sharedManifests;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PeerServer(int port, Map<String, FileManifest> sharedManifests) {
        this.port = port;
        this.sharedManifests = sharedManifests;
    }

    public void start() {
        running = true;
        executor.submit(this::serveLoop);
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
    }

    private void serveLoop() {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            System.out.println("Peer server listening on port " + port);
            while (running) {
                Socket clientSocket = socket.accept();
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

            for (FileManifest manifest : sharedManifests.values()) {
                writer.write(objectMapper.writeValueAsString(NetworkFileManifest.from(manifest)));
                writer.newLine();
                writer.flush();
            }
            writer.write("END_MANIFESTS");
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
