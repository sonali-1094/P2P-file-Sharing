package com.p2pfileshare.tracker.repository;
import com.p2pfileshare.tracker.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
public interface TransferRepository extends JpaRepository<Transfer,Long> { List<Transfer> findByDownloaderId(Long downloaderId); @EntityGraph(attributePaths={"file","downloader"}) Page<Transfer> findByDownloaderId(Long downloaderId,Pageable pageable); @EntityGraph(attributePaths={"file","downloader"}) Page<Transfer> findAll(Pageable pageable); long countByStatus(com.p2pfileshare.tracker.entity.TransferStatus status); }
