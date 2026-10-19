package com.p2pfileshare.model;

public class ChunkInfo {
    private int index;
    private long offset;
    private int size;
    private String sha256;

    public ChunkInfo() {
    }

    public ChunkInfo(int index, long offset, int size, String sha256) {
        this.index = index;
        this.offset = offset;
        this.size = size;
        this.sha256 = sha256;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}
