package com.p2pfileshare.transfer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.model.Peer;
import com.p2pfileshare.network.NetworkFileManifest;
import com.p2pfileshare.util.BackoffUtil;
import com.p2pfileshare.util.HashUtil;

public class TransferEngine {

    // ============================================================
    // PAUSE / RESUME STATE
    // ============================================================

    /*
     * volatile ensures that changes made by the JavaFX/UI thread
     * are immediately visible to the worker threads.
     */
    private volatile boolean paused = false;

    // ============================================================
    // EXECUTOR SERVICES
    // ============================================================

    // Orchestration tasks handle the overall download process.
    private final ExecutorService orchestrationExecutor =
            Executors.newCachedThreadPool(
                    namedThreads("transfer-orchestrator"));

    // Chunk workers perform the actual network/file I/O.
    private final ExecutorService chunkExecutor =
            Executors.newFixedThreadPool(
                    Math.max(4,
                            Runtime.getRuntime().availableProcessors() * 2),
                    namedThreads("chunk-worker"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // THREAD FACTORY
    // ============================================================

    private static ThreadFactory namedThreads(String prefix) {

        AtomicInteger counter = new AtomicInteger();

        return r -> {
            Thread t = new Thread(
                    r,
                    prefix + "-" + counter.incrementAndGet()
            );

            // Do not keep JVM alive after application closes.
            t.setDaemon(true);

            return t;
        };
    }

    // ============================================================
    // PAUSE
    // ============================================================

    /**
     * Pauses the download.
     *
     * Already running chunks may finish.
     * New chunks will wait until resume() is called.
     */
    public void pause() {
        paused = true;
    }

    // ============================================================
    // RESUME
    // ============================================================

    /**
     * Resumes the download.
     */
    public void resume() {
        paused = false;
    }

    // ============================================================
    // CHECK PAUSE STATE
    // ============================================================

    /**
     * Worker threads call this method before downloading a chunk.
     *
     * If the transfer is paused, the worker waits.
     * Once resume() changes paused to false, the worker continues.
     */
    private void waitIfPaused() {

        while (paused) {

            try {

                Thread.sleep(100);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(
                        "Transfer interrupted while paused",
                        e
                );
            }
        }
    }

    // ============================================================
    // SHUTDOWN
    // ============================================================

    /**
     * Stops all executor services.
     *
     * Should be called when the application closes.
     */
    public void shutdown() {

        orchestrationExecutor.shutdownNow();
        chunkExecutor.shutdownNow();
    }

    // ============================================================
    // LIST AVAILABLE FILES
    // ============================================================

    public CompletableFuture<List<NetworkFileManifest>> listAvailableFiles(
            Peer peer) {

        return CompletableFuture.supplyAsync(() -> {

            try (Socket socket =
                         new Socket(peer.getAddress(), peer.getPort())) {

                socket.setSoTimeout(10_000);

                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();

                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(os));

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(is));

                writer.write(
                        objectMapper.writeValueAsString(
                                Map.of("type", "GET_MANIFESTS")
                        )
                );

                writer.newLine();
                writer.flush();

                String response = reader.readLine();

                if (response == null || response.isBlank()) {
                    return List.of();
                }

                CollectionType type =
                        objectMapper.getTypeFactory()
                                .constructCollectionType(
                                        List.class,
                                        NetworkFileManifest.class
                                );

                return objectMapper
                        .<List<NetworkFileManifest>>readValue(
                                response,
                                type
                        );

            } catch (IOException e) {

                throw new UncheckedIOException(e);
            }

        }, orchestrationExecutor);
    }

    // ============================================================
    // DOWNLOAD FILE
    // ============================================================

    public CompletableFuture<Path> downloadFile(
            Peer peer,
            NetworkFileManifest manifest,
            Path targetDirectory) {

        return downloadFile(
                peer,
                manifest,
                targetDirectory,
                ignored -> {
                }
        );
    }

    // ============================================================
    // DOWNLOAD FILE WITH PROGRESS CALLBACK
    // ============================================================

    public CompletableFuture<Path> downloadFile(
            Peer peer,
            NetworkFileManifest manifest,
            Path targetDirectory,
            Consumer<Double> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {

            try {

                return downloadFromPeer(
                        peer,
                        manifest,
                        targetDirectory,
                        progressCallback
                );

            } catch (IOException e) {

                throw new UncheckedIOException(e);
            }

        }, orchestrationExecutor);
    }

    // ============================================================
    // DOWNLOAD FROM PEER
    // ============================================================

    private Path downloadFromPeer(
            Peer peer,
            NetworkFileManifest manifest,
            Path targetDirectory,
            Consumer<Double> progressCallback)
            throws IOException {

        // FIX 1: never trust the remote file name (path traversal).
        Path baseDir = targetDirectory.toAbsolutePath().normalize();

        Path fileNamePart;
        try {
            fileNamePart = Paths.get(manifest.fileName()).getFileName();
        } catch (java.nio.file.InvalidPathException e) {
            throw new IOException(
                    "Invalid file name: " + manifest.fileName(), e);
        }

        if (fileNamePart == null) {
            throw new IOException(
                    "Invalid file name: " + manifest.fileName());
        }

        Path destination =
                baseDir.resolve(fileNamePart).normalize();

        if (!destination.startsWith(baseDir)) {
            throw new IOException(
                    "Unsafe file name: " + manifest.fileName());
        }

        Files.createDirectories(baseDir);

        try (RandomAccessFile output =
                     new RandomAccessFile(
                             destination.toFile(),
                             "rw")) {

            output.setLength(manifest.fileSize());

            int totalChunks =
                    manifest.chunks().size();

            AtomicInteger completed =
                    new AtomicInteger();

            // ----------------------------------------------------
            // EMPTY FILE
            // ----------------------------------------------------

            if (totalChunks == 0) {

                progressCallback.accept(1.0);

                return destination;
            }

            // ----------------------------------------------------
            // CREATE CHUNK TASKS
            // ----------------------------------------------------

            List<CompletableFuture<Void>> futures =
                    new ArrayList<>();

            for (ChunkInfo chunk : manifest.chunks()) {

                CompletableFuture<Void> future =
                        CompletableFuture.runAsync(() -> {

                            /*
                             * Download the chunk. The pause check
                             * and the retry logic both live inside
                             * fetchChunkWithRetry().
                             */
                            fetchChunkWithRetry(
                                    peer,
                                    manifest.fileId(),
                                    chunk,
                                    output
                            );

                        }, chunkExecutor)

                        /*
                         * Update progress after the chunk
                         * has successfully completed.
                         */
                        .thenRun(() -> {

                            int done =
                                    completed.incrementAndGet();

                            progressCallback.accept(
                                    (double) done / totalChunks
                            );
                        });

                futures.add(future);
            }

            // ----------------------------------------------------
            // WAIT FOR ALL CHUNKS
            // ----------------------------------------------------

            try {

                CompletableFuture.allOf(
                        futures.toArray(
                                CompletableFuture[]::new
                        )
                ).join();

            } catch (CompletionException e) {

                // Normalize exception into IOException.

                Throwable nested = e.getCause();

                Throwable cause =
                        nested != null
                                ? nested
                                : e;

                if (cause instanceof UncheckedIOException unchecked
                        && unchecked.getCause() != null) {

                    throw unchecked.getCause();
                }

                if (cause instanceof IOException io) {

                    throw io;
                }

                throw new IOException(
                        "Chunk download failed",
                        cause
                );
            }

            return destination;
        }
    }

    // ============================================================
    // FETCH ONE CHUNK WITH RETRY (FIX 2)
    // ============================================================

    /**
     * Tries a chunk up to MAX_CHUNK_ATTEMPTS times with exponential
     * backoff. Safe to retry because fetchChunk() only writes to the
     * file after the SHA-256 check passes.
     */
    private static final int MAX_CHUNK_ATTEMPTS = 3;

    private void fetchChunkWithRetry(
            Peer peer,
            String fileId,
            ChunkInfo chunk,
            RandomAccessFile output) {

        for (int attempt = 0; ; attempt++) {

            // Respect pause before every attempt.
            waitIfPaused();

            try {

                fetchChunk(peer, fileId, chunk, output);
                return;

            } catch (UncheckedIOException e) {

                if (attempt + 1 >= MAX_CHUNK_ATTEMPTS) {
                    throw e;
                }

                try {

                    Thread.sleep(BackoffUtil.computeDelay(attempt));

                } catch (InterruptedException ie) {

                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // ============================================================
    // FETCH ONE CHUNK
    // ============================================================

    private void fetchChunk(
            Peer peer,
            String fileId,
            ChunkInfo chunk,
            RandomAccessFile output) {

        try (Socket socket =
                     new Socket(
                             peer.getAddress(),
                             peer.getPort()
                     )) {

            socket.setSoTimeout(15_000);

            OutputStream os =
                    socket.getOutputStream();

            InputStream is =
                    socket.getInputStream();

            BufferedWriter writer =
                    new BufferedWriter(
                            new OutputStreamWriter(os)
                    );

            // ----------------------------------------------------
            // REQUEST CHUNK
            // ----------------------------------------------------

            writer.write(
                    objectMapper.writeValueAsString(
                            new ChunkRequest(
                                    "GET_CHUNK",
                                    fileId,
                                    chunk.getIndex()
                            )
                    )
            );

            writer.newLine();
            writer.flush();

            // ----------------------------------------------------
            // RECEIVE CHUNK
            // ----------------------------------------------------

            byte[] readBuffer =
                    new byte[8192];

            int remaining =
                    chunk.getSize();

            byte[] chunkData =
                    new byte[remaining];

            int offset = 0;

            while (remaining > 0) {

                int read =
                        is.read(
                                readBuffer,
                                0,
                                Math.min(
                                        readBuffer.length,
                                        remaining
                                )
                        );

                if (read < 0) {

                    throw new EOFException(
                            "Unexpected EOF while reading chunk "
                                    + chunk.getIndex()
                    );
                }

                System.arraycopy(
                        readBuffer,
                        0,
                        chunkData,
                        offset,
                        read
                );

                offset += read;
                remaining -= read;
            }

            // ----------------------------------------------------
            // VERIFY SHA-256
            // ----------------------------------------------------

            String actualHash =
                    HashUtil.sha256(chunkData);

            if (!actualHash.equals(
                    chunk.getSha256())) {

                throw new IOException(
                        "Checksum mismatch for chunk "
                                + chunk.getIndex()
                );
            }

            // ----------------------------------------------------
            // WRITE CHUNK TO CORRECT POSITION
            // ----------------------------------------------------

            synchronized (output) {

                output.seek(
                        chunk.getOffset()
                );

                output.write(
                        chunkData
                );
            }

        } catch (IOException e) {

            throw new UncheckedIOException(e);
        }
    }

    // ============================================================
    // CHUNK REQUEST
    // ============================================================

    private record ChunkRequest(
            String type,
            String fileId,
            int chunkIndex) {
    }
}