package com.p2pfileshare.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.network.NetworkFileManifest;
import com.p2pfileshare.model.Peer;
import com.p2pfileshare.util.HashUtil;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TransferEngine {
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<List<NetworkFileManifest>> listAvailableFiles(Peer peer) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket(peer.getAddress(), peer.getPort());
                 OutputStream os = socket.getOutputStream();
                 InputStream is = socket.getInputStream()) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                writer.write(objectMapper.writeValueAsString(Map.of("type", "GET_MANIFESTS")));
                writer.newLine();
                writer.flush();

                String response = reader.readLine();
                if (response == null || response.isBlank()) {
                    return List.of();
                }
                CollectionType type = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, NetworkFileManifest.class);
                return objectMapper.readValue(response, type);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    public CompletableFuture<Path> downloadFile(Peer peer, NetworkFileManifest manifest, Path targetDirectory) {
        return downloadFile(peer, manifest, targetDirectory, ignored -> {
        });
    }

    public CompletableFuture<Path> downloadFile(Peer peer, NetworkFileManifest manifest, Path targetDirectory, Consumer<Double> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadFromPeer(peer, manifest, targetDirectory, progressCallback);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }

    private Path downloadFromPeer(Peer peer, NetworkFileManifest manifest, Path targetDirectory, Consumer<Double> progressCallback) throws IOException {
        Path destination = targetDirectory.resolve(manifest.fileName());
        Files.createDirectories(targetDirectory);
        try (RandomAccessFile output = new RandomAccessFile(destination.toFile(), "rw")) {
            output.setLength(manifest.fileSize());
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int totalChunks = manifest.chunks().size();
            java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger();
            for (ChunkInfo chunk : manifest.chunks()) {
                futures.add(CompletableFuture.runAsync(() -> fetchChunk(peer, manifest.fileId(), chunk, output), executor)
                        .thenRun(() -> {
                            int done = completed.incrementAndGet();
                            progressCallback.accept(totalChunks == 0 ? 1.0 : (double) done / totalChunks);
                        }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            if (completed.get() != totalChunks) {
                throw new IOException("Some chunks failed to download");
            }
            return destination;
        }
    }

    private void fetchChunk(Peer peer, String fileId, ChunkInfo chunk, RandomAccessFile output) {
        try (Socket socket = new Socket(peer.getAddress(), peer.getPort());
             OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream()) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(objectMapper.writeValueAsString(new ChunkRequest("GET_CHUNK", fileId, chunk.getIndex())));
            writer.newLine();
            writer.flush();

            byte[] readBuffer = new byte[8192];
            int remaining = chunk.getSize();
            byte[] chunkData = new byte[remaining];
            int offset = 0;
            while (remaining > 0) {
                int read = is.read(readBuffer, 0, Math.min(readBuffer.length, remaining));
                if (read < 0) throw new EOFException("Unexpected EOF while reading chunk");
                System.arraycopy(readBuffer, 0, chunkData, offset, read);
                offset += read;
                remaining -= read;
            }
            String actualHash = HashUtil.sha256(chunkData);
            if (!actualHash.equals(chunk.getSha256())) {
                throw new IOException("Checksum mismatch for chunk " + chunk.getIndex());
            }
            synchronized (output) {
                output.seek(chunk.getOffset());
                output.write(chunkData);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ChunkRequest(String type, String fileId, int chunkIndex) {
    }
}
