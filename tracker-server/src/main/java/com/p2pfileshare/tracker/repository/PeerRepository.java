package com.p2pfileshare.tracker.repository;
import com.p2pfileshare.tracker.entity.Peer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
public interface PeerRepository extends JpaRepository<Peer,Long> { Optional<Peer> findByPeerUid(String peerUid); @EntityGraph(attributePaths="user") List<Peer> findByLastSeenAfter(Instant instant); @EntityGraph(attributePaths="user") List<Peer> findByUserIdAndLastSeenAfter(Long userId,Instant instant); long countByLastSeenAfter(Instant instant); }
