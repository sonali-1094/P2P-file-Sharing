package com.p2pfileshare.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.model.Peer;
import com.p2pfileshare.network.NetworkFileManifest;
import com.p2pfileshare.network.PeerServer;
import com.p2pfileshare.util.HashUtil;

/**
 * End-to-end tests: a real PeerServer and a real TransferEngine talking over
 * localhost sockets.
 */
class TransferEngineTest {

    private static final int CHUNK = FileChunker.DEFAULT_CHUNK_SIZE;

    @TempDir
    Path tempDir;

    private final Map<String, FileManifest> shared = new ConcurrentHashMap<>();
    private TransferEngine engine;
    private PeerServer server;
    private Peer peer;

    @BeforeEach
    void setUp() throws Exception {
        engine = new TransferEngine();
        int port = freePort();
        // If you finish "Fix 3" (shared peer ID), change this line to:
        //   server = new PeerServer(port, shared, "test-peer");
        server = new PeerServer(port, shared);
        server.start();
        peer = peerOn(port);
    }

    @AfterEach
    void tearDown() {
        engine.resume();
        engine.shutdown();
        server.stop();
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void downloadedFileIsIdenticalToTheOriginal() throws Exception {
        Path source = createFile("source.bin", CHUNK * 2 + 1234);
        NetworkFileManifest manifest = share(source);
        Path downloads = tempDir.resolve("downloads");
        List<Double> progress = new CopyOnWriteArrayList<>();

        Path result = engine.downloadFile(peer, manifest, downloads, progress::add)
                .get(30, TimeUnit.SECONDS);

        assertEquals(downloads.toAbsolutePath().normalize().resolve("source.bin"), result);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(result));
        assertEquals(3, progress.size());
        double highest = Collections.max(progress);
        assertEquals(1.0, highest);
    }

    @Test
    void emptyFileDownloadsAsEmptyFile() throws Exception {
        NetworkFileManifest manifest = share(createFile("empty.bin", 0));

        Path result = engine.downloadFile(peer, manifest, tempDir.resolve("downloads"))
                .get(10, TimeUnit.SECONDS);

        assertTrue(Files.exists(result));
        assertEquals(0L, Files.size(result));
    }

    @Test
    void listAvailableFilesReturnsWhatThePeerShares() throws Exception {
        NetworkFileManifest manifest = share(createFile("a.bin", 5000));

        List<NetworkFileManifest> listed = engine.listAvailableFiles(peer).get(10, TimeUnit.SECONDS);

        assertEquals(1, listed.size());
        assertEquals(manifest.fileId(), listed.get(0).fileId());
        assertEquals("a.bin", listed.get(0).fileName());
        assertEquals(5000L, listed.get(0).fileSize());
    }

    // ------------------------------------------------------------------
    // Integrity
    // ------------------------------------------------------------------

    @Test
    void corruptedChunkIsDetectedByChecksum() throws Exception {
        NetworkFileManifest good = share(createFile("c.bin", CHUNK + 100));
        List<ChunkInfo> chunks = new ArrayList<>(good.chunks());
        ChunkInfo original = chunks.get(1);
        // Pretend the sender announced a wrong hash for chunk 1.
        chunks.set(1, new ChunkInfo(original.getIndex(), original.getOffset(),
                original.getSize(), "0".repeat(64)));
        NetworkFileManifest tampered = new NetworkFileManifest(
                good.fileId(), good.fileName(), good.fileSize(), good.chunkSize(), chunks);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> engine.downloadFile(peer, tampered, tempDir.resolve("downloads"))
                        .get(30, TimeUnit.SECONDS));

        assertTrue(rootCause(ex).getMessage().contains("Checksum mismatch"));
    }

    // ------------------------------------------------------------------
    // Retry with backoff
    // ------------------------------------------------------------------

    @Test
    void chunkIsRetriedAndSucceedsAfterTwoTransientFailures() throws Exception {
        byte[] data = randomBytes(4000);
        try (FlakyServer flaky = new FlakyServer(data, 2)) {
            Path result = engine.downloadFile(peerOn(flaky.port()), singleChunkManifest("flaky", data),
                    tempDir.resolve("downloads")).get(30, TimeUnit.SECONDS);

            assertArrayEquals(data, Files.readAllBytes(result));
            assertEquals(3, flaky.chunkRequests.get());
        }
    }

    @Test
    void downloadFailsAfterExactlyThreeAttempts() throws Exception {
        byte[] data = randomBytes(4000);
        try (FlakyServer flaky = new FlakyServer(data, 99)) {
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> engine.downloadFile(peerOn(flaky.port()), singleChunkManifest("dead", data),
                            tempDir.resolve("downloads")).get(30, TimeUnit.SECONDS));

            assertTrue(rootCause(ex).getMessage().contains("Unexpected EOF"));
            assertEquals(3, flaky.chunkRequests.get());
        }
    }

    // ------------------------------------------------------------------
    // Path traversal
    // ------------------------------------------------------------------

    @Test
    void maliciousFileNamesStayInsideTheDownloadFolder() throws Exception {
        String[] names = {"../evil.txt", "../../evil.txt", "sub/../../evil.txt", "/abs/path/evil.txt"};

        for (int i = 0; i < names.length; i++) {
            Path downloads = tempDir.resolve("case" + i).resolve("downloads");
            NetworkFileManifest manifest =
                    new NetworkFileManifest("id" + i, names[i], 0L, 1024, List.of());

            Path result = engine.downloadFile(peer, manifest, downloads).get(10, TimeUnit.SECONDS);

            Path expected = downloads.toAbsolutePath().normalize().resolve("evil.txt");
            assertEquals(expected, result);
            assertTrue(Files.exists(expected));
            // Nothing may escape to the parent folder or the test root.
            assertFalse(Files.exists(downloads.getParent().resolve("evil.txt")));
            assertFalse(Files.exists(tempDir.resolve("evil.txt")));
        }
    }

    @Test
    void dotDotAsFileNameIsRejected() {
        NetworkFileManifest manifest = new NetworkFileManifest("id", "..", 0L, 1024, List.of());

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> engine.downloadFile(peer, manifest, tempDir.resolve("downloads"))
                        .get(10, TimeUnit.SECONDS));

        assertTrue(rootCause(ex).getMessage().contains("Unsafe file name"));
    }

    // ------------------------------------------------------------------
    // Pause / resume
    // ------------------------------------------------------------------

    @Test
    void pausedTransferWaitsUntilResumed() throws Exception {
        Path source = createFile("p.bin", CHUNK * 2);
        NetworkFileManifest manifest = share(source);

        engine.pause();
        CompletableFuture<Path> future = engine.downloadFile(peer, manifest, tempDir.resolve("downloads"));
        Thread.sleep(700);
        assertFalse(future.isDone());

        engine.resume();
        Path result = future.get(30, TimeUnit.SECONDS);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(result));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Peer peerOn(int port) {
        return new Peer("test-peer", InetAddress.getLoopbackAddress(), port, "1.0", new String[0]);
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(7).nextBytes(data);
        return data;
    }

    private Path createFile(String name, int size) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, randomBytes(size));
        return file;
    }

    private NetworkFileManifest share(Path file) throws IOException {
        FileManifest manifest = FileChunker.buildManifest(file);
        shared.put(manifest.getFileId(), manifest);
        return NetworkFileManifest.from(manifest);
    }

    private static NetworkFileManifest singleChunkManifest(String fileId, byte[] data) {
        ChunkInfo chunk = new ChunkInfo(0, 0, data.length, HashUtil.sha256(data));
        return new NetworkFileManifest(fileId, fileId + ".bin", data.length, CHUNK, List.of(chunk));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * A fake peer that drops the first N chunk requests (closes the socket
     * without sending data) and then serves the bytes normally.
     */
    private static final class FlakyServer implements AutoCloseable {
        final AtomicInteger chunkRequests = new AtomicInteger();
        private final ServerSocket socket;
        private final byte[] data;
        private final int failFirst;

        FlakyServer(byte[] data, int failFirst) throws IOException {
            this.data = data;
            this.failFirst = failFirst;
            this.socket = new ServerSocket(0);
            Thread thread = new Thread(this::serve, "flaky-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String request = reader.readLine();
                    if (request == null || !request.contains("GET_CHUNK")) {
                        continue;
                    }
                    if (chunkRequests.incrementAndGet() <= failFirst) {
                        continue; // closes without sending -> client sees EOF
                    }
                    client.getOutputStream().write(data);
                    client.getOutputStream().flush();
                } catch (IOException e) {
                    if (socket.isClosed()) {
                        return;
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
