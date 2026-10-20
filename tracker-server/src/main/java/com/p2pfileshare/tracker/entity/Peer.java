package com.p2pfileshare.tracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "peers", uniqueConstraints = @UniqueConstraint(name = "uk_peers_peer_uid", columnNames = "peer_uid"), indexes = {@Index(name="idx_peers_last_seen", columnList="last_seen"), @Index(name="idx_peers_user_id", columnList="user_id")})
public class Peer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name="fk_peers_user")) private User user;
    @Column(name="peer_uid", nullable=false, unique=true, length=100) private String peerUid;
    @Column(name="ip_address", nullable=false, length=45) private String ipAddress;
    @Column(nullable=false) private int port;
    @Column(name="last_seen", nullable=false) private Instant lastSeen;
    protected Peer() {}
    public Peer(User user, String peerUid, String ipAddress, int port, Instant lastSeen) { this.user=user; this.peerUid=peerUid; this.ipAddress=ipAddress; this.port=port; this.lastSeen=lastSeen; }
    public Long getId(){return id;} public User getUser(){return user;} public String getPeerUid(){return peerUid;} public Instant getLastSeen(){return lastSeen;}
    public String getIpAddress(){return ipAddress;} public int getPort(){return port;}
    public void refresh(String ipAddress,int port,Instant lastSeen){this.ipAddress=ipAddress;this.port=port;this.lastSeen=lastSeen;}
    @Override public boolean equals(Object o){return this==o || o instanceof Peer other && id!=null && id.equals(other.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
