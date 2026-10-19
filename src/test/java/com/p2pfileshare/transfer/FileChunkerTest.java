package com.p2pfileshare.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.util.HashUtil;

class FileChunkerTest {

    private static final int CHUNK = FileChunker.DEFAULT_CHUNK_SIZE;

    @TempDir
    Path tempDir;

    private Path createFile(String name, int size) throws IOException {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        Path file = tempDir.resolve(name);
        Files.write(file, data);
        return file;
    }

    @Test
    void splitsFileIntoTwoFullChunksAndOneHalfChunk() throws IOException {
        int size = CHUNK * 2 + CHUNK / 2;
        FileManifest manifest = FileChunker.buildManifest(createFile("a.bin", size));

        List<ChunkInfo> chunks = manifest.getChunks();
        assertEquals(3, chunks.size());
        assertEquals((long) size, manifest.getFileSize());
        assertEquals("a.bin", manifest.getFileName());
        assertEquals(CHUNK, chunks.get(0).getSize());
        assertEquals(CHUNK, chunks.get(1).getSize());
        assertEquals(CHUNK / 2, chunks.get(2).getSize());
    }

    @Test
    void offsetsAreContiguousAndCoverTheWholeFile() throws IOException {
        int size = CHUNK * 2 + 777;
        FileManifest manifest = FileChunker.buildManifest(createFile("b.bin", size));

        long expectedOffset = 0;
        for (int i = 0; i < manifest.getChunks().size(); i++) {
            ChunkInfo chunk = manifest.getChunks().get(i);
            assertEquals(i, chunk.getIndex());
            assertEquals(expectedOffset, chunk.getOffset());
            expectedOffset += chunk.getSize();
        }
        assertEquals((long) size, expectedOffset);
    }

    @Test
    void eachChunkHashMatchesTheActualBytes() throws IOException {
        int size = CHUNK + 5000;
        Path file = createFile("c.bin", size);
        byte[] all = Files.readAllBytes(file);
        FileManifest manifest = FileChunker.buildManifest(file);

        for (ChunkInfo chunk : manifest.getChunks()) {
            int from = (int) chunk.getOffset();
            byte[] slice = Arrays.copyOfRange(all, from, from + chunk.getSize());
            assertEquals(HashUtil.sha256(slice), chunk.getSha256());
        }
    }

    @Test
    void exactMultipleOfChunkSizeHasNoExtraEmptyChunk() throws IOException {
        FileManifest manifest = FileChunker.buildManifest(createFile("d.bin", CHUNK * 2));
        assertEquals(2, manifest.getChunks().size());
        assertEquals(CHUNK, manifest.getChunks().get(1).getSize());
    }

    @Test
    void smallFileIsOneChunk() throws IOException {
        FileManifest manifest = FileChunker.buildManifest(createFile("e.bin", 10));
        assertEquals(1, manifest.getChunks().size());
        assertEquals(10, manifest.getChunks().get(0).getSize());
    }

    @Test
    void emptyFileHasNoChunks() throws IOException {
        FileManifest manifest = FileChunker.buildManifest(createFile("empty.bin", 0));
        assertEquals(0, manifest.getChunks().size());
        assertEquals(0L, manifest.getFileSize());
    }

    @Test
    void directoryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FileChunker.buildManifest(tempDir));
    }

    @Test
    void missingFileIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FileChunker.buildManifest(tempDir.resolve("does-not-exist.bin")));
    }

    @Test
    void eachManifestGetsAUniqueFileId() throws IOException {
        Path file = createFile("f.bin", 100);
        String first = FileChunker.buildManifest(file).getFileId();
        String second = FileChunker.buildManifest(file).getFileId();
        assertNotEquals(first, second);
    }
}
