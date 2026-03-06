package com.skytech.instaloc.InstLoc.repository;

import com.skytech.instaloc.InstLoc.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    Optional<LocationEntity> findByPlaceId(String placeId);

    List<LocationEntity> findByUserId(String userId);

    boolean existsByPlaceId(String placeId);

    List<LocationEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByIdAndUserId(Long id, String userId);
}
