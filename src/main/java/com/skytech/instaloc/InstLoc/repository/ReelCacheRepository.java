package com.skytech.instaloc.InstLoc.repository;

import com.skytech.instaloc.InstLoc.entity.ReelCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReelCacheRepository extends JpaRepository<ReelCacheEntity, Long> {
    Optional<ReelCacheEntity> findByReelUrl(String reelUrl);
    boolean existsByReelUrl(String reelUrl);
}
