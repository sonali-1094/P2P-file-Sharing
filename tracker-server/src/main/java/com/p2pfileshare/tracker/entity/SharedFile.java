package com.p2pfileshare.tracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name="shared_files", indexes={@Index(name="idx_shared_files_file_hash", columnList="file_hash"), @Index(name="idx_shared_files_owner_id", columnList="owner_id")})
public class SharedFile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="owner_id",nullable=false,foreignKey=@ForeignKey(name="fk_shared_files_owner")) private User owner;
    @Column(name="file_hash",nullable=false,length=64) private String fileHash;
    @Column(nullable=false) private String name;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    @Column(name="chunk_size",nullable=false) private int chunkSize;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Visibility visibility;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @OneToMany(mappedBy="file",cascade=CascadeType.ALL,orphanRemoval=true) private List<FileChunk> chunks=new ArrayList<>();
    protected SharedFile() {}
    public SharedFile(User owner,String fileHash,String name,long sizeBytes,int chunkSize,Visibility visibility){this.owner=owner;this.fileHash=fileHash;this.name=name;this.sizeBytes=sizeBytes;this.chunkSize=chunkSize;this.visibility=visibility;this.createdAt=Instant.now();}
    public void addChunk(FileChunk chunk){chunks.add(chunk);chunk.setFile(this);}
    public Long getId(){return id;} public User getOwner(){return owner;} public String getFileHash(){return fileHash;} public List<FileChunk> getChunks(){return chunks;}
    public String getName(){return name;} public long getSizeBytes(){return sizeBytes;} public int getChunkSize(){return chunkSize;} public Visibility getVisibility(){return visibility;} public Instant getCreatedAt(){return createdAt;}
    public void update(String name,Visibility visibility){this.name=name;this.visibility=visibility;}
    @Override public boolean equals(Object o){return this==o || o instanceof SharedFile other && id!=null && id.equals(other.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
