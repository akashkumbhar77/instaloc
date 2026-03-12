package com.skytech.instaloc.InstLoc.repository;

import com.skytech.instaloc.InstLoc.entity.ExtractionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExtractionJobRepository extends JpaRepository<ExtractionJobEntity, Long> {
    List<ExtractionJobEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<ExtractionJobEntity> findAllByOrderByCreatedAtDesc();
    List<ExtractionJobEntity> findByStatusAndUpdatedAtBefore(ExtractionJobEntity.Status status, LocalDateTime time);
}
