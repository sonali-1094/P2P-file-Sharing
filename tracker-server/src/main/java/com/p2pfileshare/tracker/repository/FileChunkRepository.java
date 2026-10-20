package com.p2pfileshare.tracker.repository;
import com.p2pfileshare.tracker.entity.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FileChunkRepository extends JpaRepository<FileChunk,Long> {}
