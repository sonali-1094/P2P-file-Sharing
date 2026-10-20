package com.p2pfileshare.tracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="transfers", indexes={@Index(name="idx_transfers_downloader_id",columnList="downloader_id"),@Index(name="idx_transfers_file_status",columnList="file_id,status")})
public class Transfer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="file_id",nullable=false,foreignKey=@ForeignKey(name="fk_transfers_file")) private SharedFile file;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="downloader_id",nullable=false,foreignKey=@ForeignKey(name="fk_transfers_downloader")) private User downloader;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TransferStatus status;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    protected Transfer() {}
    public Transfer(SharedFile file,User downloader,TransferStatus status,Instant startedAt,Instant finishedAt){this.file=file;this.downloader=downloader;this.status=status;this.startedAt=startedAt;this.finishedAt=finishedAt;}
    public Long getId(){return id;} public SharedFile getFile(){return file;} public User getDownloader(){return downloader;}
    public TransferStatus getStatus(){return status;} public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;}
    public void finish(TransferStatus status,Instant finishedAt){this.status=status;this.finishedAt=finishedAt;}
    @Override public boolean equals(Object o){return this==o || o instanceof Transfer other && id!=null && id.equals(other.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
