package com.p2pfileshare.tracker.entity;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name="file_chunks", uniqueConstraints=@UniqueConstraint(name="uk_file_chunks_file_index",columnNames={"file_id","chunk_index"}))
public class FileChunk {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="file_id",nullable=false,foreignKey=@ForeignKey(name="fk_file_chunks_file")) private SharedFile file;
    @Column(name="chunk_index",nullable=false) private int chunkIndex;
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="size_bytes",nullable=false) private int sizeBytes;
    protected FileChunk() {}
    public FileChunk(int chunkIndex,String sha256,int sizeBytes){this.chunkIndex=chunkIndex;this.sha256=sha256;this.sizeBytes=sizeBytes;}
    void setFile(SharedFile file){this.file=file;}
    public Long getId(){return id;} public int getChunkIndex(){return chunkIndex;} public String getSha256(){return sha256;} public int getSizeBytes(){return sizeBytes;}
    @Override public boolean equals(Object o){return this==o || o instanceof FileChunk other && id!=null && id.equals(other.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
