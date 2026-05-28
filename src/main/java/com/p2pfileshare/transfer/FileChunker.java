package com.p2pfileshare.transfer;

import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.model.FileManifest;
import com.p2pfileshare.util.HashUtil;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FileChunker {
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

    private FileChunker() {
    }

    public static FileManifest buildManifest(Path filePath) throws IOException {
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Path must point to a regular file");
        }
        long totalSize = Files.size(filePath);
        List<ChunkInfo> chunks = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            int chunkSize = DEFAULT_CHUNK_SIZE;
            long offset = 0;
            int index = 0;
            byte[] buffer = new byte[chunkSize];
            while (offset < totalSize) {
                int toRead = (int) Math.min(chunkSize, totalSize - offset);
                raf.readFully(buffer, 0, toRead);
                String hash = HashUtil.sha256(buffer.length == toRead ? buffer : copyOf(buffer, toRead));
                chunks.add(new ChunkInfo(index, offset, toRead, hash));
                offset += toRead;
                index++;
            }
        }
        return new FileManifest(UUID.randomUUID().toString(), filePath.getFileName().toString(), filePath, totalSize, DEFAULT_CHUNK_SIZE, chunks);
    }

    private static byte[] copyOf(byte[] original, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(original, 0, copy, 0, length);
        return copy;
    }
}
