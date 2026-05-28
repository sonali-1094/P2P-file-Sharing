package com.p2pfileshare.storage;

import java.time.Instant;

public class TransferRecord {
    private final String fileName;
    private final long fileSize;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String status;

    public TransferRecord(String fileName, long fileSize, Instant startedAt, Instant finishedAt, String status) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getStatus() {
        return status;
    }
}
