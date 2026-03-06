package com.skytech.instaloc.InstLoc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_jobs", indexes = {
    @Index(name = "idx_extraction_jobs_status", columnList = "status")
})
public class ExtractionJobEntity {

    public enum Status {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "frames_extracted")
    private Integer framesExtracted;

    @Column(name = "locations_found")
    private Integer locationsFound;

    @Column(name = "locations_grounded")
    private Integer locationsGrounded;

    @Column(name = "user_id")
    private String userId;

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

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getFramesExtracted() { return framesExtracted; }
    public void setFramesExtracted(Integer framesExtracted) { this.framesExtracted = framesExtracted; }

    public Integer getLocationsFound() { return locationsFound; }
    public void setLocationsFound(Integer locationsFound) { this.locationsFound = locationsFound; }

    public Integer getLocationsGrounded() { return locationsGrounded; }
    public void setLocationsGrounded(Integer locationsGrounded) { this.locationsGrounded = locationsGrounded; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
