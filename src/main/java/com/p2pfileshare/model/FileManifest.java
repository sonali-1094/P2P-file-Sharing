package com.p2pfileshare.model;

import java.nio.file.Path;
import java.util.List;

public class FileManifest {
    private final String fileId;
    private final String fileName;
    private final Path filePath;
    private final long fileSize;
    private final int chunkSize;
    private final List<ChunkInfo> chunks;

    public FileManifest(String fileId, String fileName, Path filePath, long fileSize, int chunkSize, List<ChunkInfo> chunks) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.chunks = chunks;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public Path getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public List<ChunkInfo> getChunks() {
        return chunks;
    }
}
