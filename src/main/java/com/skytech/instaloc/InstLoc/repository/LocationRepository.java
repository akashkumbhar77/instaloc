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

    // Scoped to the current user — prevents sharing a place row across users
    Optional<LocationEntity> findByPlaceIdAndUserId(String placeId, String userId);

    // Dedup check for locations saved without a placeId
    Optional<LocationEntity> findByUserIdAndNameIgnoreCase(String userId, String name);

    List<LocationEntity> findByUserId(String userId);

    boolean existsByPlaceId(String placeId);

    // Distinct-by-name to prevent duplicates in the list view
    @Query("SELECT DISTINCT l FROM LocationEntity l WHERE l.userId = :userId " +
           "ORDER BY l.createdAt DESC")
    List<LocationEntity> findDistinctByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    List<LocationEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByIdAndUserId(Long id, String userId);
}
