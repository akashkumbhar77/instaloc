package com.skytech.instaloc.InstLoc.entity;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import java.time.LocalDateTime;

@Entity
@Table(name = "locations", indexes = {
    @Index(name = "idx_location_user_id", columnList = "userId"),
    @Index(name = "idx_location_place_id", columnList = "placeId", unique = true)
})
public class LocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nullable
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String name;

    @Nullable
    private String address;

    @Column(name = "place_id", unique = true)
    @Nullable
    private String placeId;

    @Nullable
    private String category;

    @Nullable
    @Column(name = "state_or_region")
    private String stateOrRegion;

    @Nullable
    @Column(name = "confidence")
    private Double confidence;

    // Store coordinates as simple doubles for H2/PostgreSQL compatibility
    @Nullable
    private Double latitude;

    @Nullable
    private Double longitude;

    @Column(name = "reel_url")
    @Nullable
    private String reelUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public @Nullable String getUserId() { return userId; }
    public void setUserId(@Nullable String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public @Nullable String getAddress() { return address; }
    public void setAddress(@Nullable String address) { this.address = address; }

    public @Nullable String getPlaceId() { return placeId; }
    public void setPlaceId(@Nullable String placeId) { this.placeId = placeId; }

    public @Nullable String getCategory() { return category; }
    public void setCategory(@Nullable String category) { this.category = category; }

    public @Nullable String getStateOrRegion() { return stateOrRegion; }
    public void setStateOrRegion(@Nullable String stateOrRegion) { this.stateOrRegion = stateOrRegion; }

    public @Nullable Double getConfidence() { return confidence; }
    public void setConfidence(@Nullable Double confidence) { this.confidence = confidence; }

    public @Nullable Double getLatitude() { return latitude; }
    public void setLatitude(@Nullable Double latitude) { this.latitude = latitude; }

    public @Nullable Double getLongitude() { return longitude; }
    public void setLongitude(@Nullable Double longitude) { this.longitude = longitude; }

    public @Nullable String getReelUrl() { return reelUrl; }
    public void setReelUrl(@Nullable String reelUrl) { this.reelUrl = reelUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
