package com.p2pfileshare.tracker.repository;
import com.p2pfileshare.tracker.entity.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface SharedFileRepository extends JpaRepository<SharedFile,Long> {
    Optional<SharedFile> findByFileHash(String fileHash);
    Optional<SharedFile> findByOwnerIdAndFileHash(Long ownerId,String fileHash);
    List<SharedFile> findByOwnerId(Long ownerId);
    @EntityGraph(attributePaths="chunks") @Query("select distinct f from SharedFile f where f.id=:id") Optional<SharedFile> findDetailedById(@Param("id") Long id);
    @EntityGraph(attributePaths="owner") @Query("select f from SharedFile f where (f.visibility <> com.p2pfileshare.tracker.entity.Visibility.PRIVATE or f.owner.id=:userId or :admin=true) and lower(f.name) like lower(concat('%',:search,'%'))") Page<SharedFile> findVisible(@Param("userId") Long userId,@Param("admin") boolean admin,@Param("search") String search,Pageable pageable);
    long countByVisibility(com.p2pfileshare.tracker.entity.Visibility visibility);
    @Query("select coalesce(sum(f.sizeBytes),0) from SharedFile f where f.visibility <> com.p2pfileshare.tracker.entity.Visibility.PRIVATE") long totalSharedBytes();
}
