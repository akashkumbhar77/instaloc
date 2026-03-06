package com.skytech.instaloc.InstLoc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reel_cache", indexes = {
    @Index(name = "idx_reel_cache_url", columnList = "reelUrl", unique = true)
})
public class ReelCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reel_url", nullable = false, unique = true)
    private String reelUrl;

    @Column(name = "location_ids")
    private String locationIds; // Comma-separated location IDs

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReelUrl() { return reelUrl; }
    public void setReelUrl(String reelUrl) { this.reelUrl = reelUrl; }

    public String getLocationIds() { return locationIds; }
    public void setLocationIds(String locationIds) { this.locationIds = locationIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
