package com.p2pfileshare.network;

import java.util.List;

import com.p2pfileshare.model.ChunkInfo;
import com.p2pfileshare.model.FileManifest;

public record NetworkFileManifest(
        String fileId,
        String fileName,
        long fileSize,
        int chunkSize,
        List<ChunkInfo> chunks
) {
    public static NetworkFileManifest from(FileManifest manifest) {
        return new NetworkFileManifest(
                manifest.getFileId(),
                manifest.getFileName(),
                manifest.getFileSize(),
                manifest.getChunkSize(),
                List.copyOf(manifest.getChunks()) // FIX: defensive copy
        );
    }
}