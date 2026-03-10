package com.skytech.instaloc.InstLoc.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytech.instaloc.InstLoc.dto.*;
import com.skytech.instaloc.InstLoc.entity.ExtractionJobEntity;
import com.skytech.instaloc.InstLoc.entity.LocationEntity;
import com.skytech.instaloc.InstLoc.entity.ReelCacheEntity;
import com.skytech.instaloc.InstLoc.repository.ExtractionJobRepository;
import com.skytech.instaloc.InstLoc.repository.LocationRepository;
import com.skytech.instaloc.InstLoc.repository.ReelCacheRepository;
import com.skytech.instaloc.InstLoc.service.AsyncExtractionService;
import com.skytech.instaloc.InstLoc.service.FfmpegService;
import com.skytech.instaloc.InstLoc.service.GroundingService;
import com.skytech.instaloc.InstLoc.service.InstagramDownloadService;
import com.skytech.instaloc.InstLoc.service.OptimizedExtractionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final FfmpegService ffmpegService;
    private final OptimizedExtractionService optimizedExtractionService;
    private final GroundingService groundingService;
    private final InstagramDownloadService instagramDownloadService;
    private final LocationRepository locationRepository;
    private final ReelCacheRepository reelCacheRepository;
    private final ExtractionJobRepository jobRepository;
    private final AsyncExtractionService asyncExtractionService;
    private final ObjectMapper objectMapper;

    public ExtractionController(
            FfmpegService ffmpegService,
            OptimizedExtractionService optimizedExtractionService,
            GroundingService groundingService,
            InstagramDownloadService instagramDownloadService,
            LocationRepository locationRepository,
            ReelCacheRepository reelCacheRepository,
            ExtractionJobRepository jobRepository,
            AsyncExtractionService asyncExtractionService) {
        this.ffmpegService = ffmpegService;
        this.optimizedExtractionService = optimizedExtractionService;
        this.groundingService = groundingService;
        this.instagramDownloadService = instagramDownloadService;
        this.locationRepository = locationRepository;
        this.reelCacheRepository = reelCacheRepository;
        this.jobRepository = jobRepository;
        this.asyncExtractionService = asyncExtractionService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get user ID - using API key auth, so default to a single user
     */
    private String getUserId() {
        return "api-user";
    }

    /**
     * Main extraction endpoint - ASYNC with caching
     * Returns 202 Accepted immediately with job ID for polling
     */
    @PostMapping("/extract")
    public ResponseEntity<JobSubmissionResponse> extractLocationsAsync(
            @Valid @RequestBody ExtractionRequest request) {

        String user = getUserId();
        String reelUrl = request.reelUrl();

        log.info("Async extraction request for user: {}, URL: {}", user, reelUrl);

        // Check cache first - return immediately if cached
        if (reelCacheRepository.existsByReelUrl(reelUrl)) {
            log.info("URL found in cache: {}", reelUrl);
            ReelCacheEntity cache = reelCacheRepository.findByReelUrl(reelUrl).orElse(null);
            if (cache != null && cache.getLocationIds() != null) {
                List<Long> locationIds = Arrays.stream(cache.getLocationIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList());

                List<LocationEntity> locations = locationRepository.findAllById(locationIds);
                List<LocationResponse> responses = locations.stream()
                        .map(this::toLocationResponse)
                        .collect(Collectors.toList());

                return ResponseEntity.ok(new JobSubmissionResponse(
                        null,
                        "success",
                        "Returned cached result",
                        "/api/v1/extract/cached"));
            }
        }

        // Create new job
        ExtractionJobEntity job = new ExtractionJobEntity();
        job.setUrl(reelUrl);
        job.setUserId(user);
        job.setStatus(ExtractionJobEntity.Status.PENDING);
        job = jobRepository.save(job);

        log.info("Created job: {}", job.getId());

        // Start async processing
        asyncExtractionService.processExtractionJob(job.getId(), reelUrl, user);

        // Return immediately with job ID
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new JobSubmissionResponse(
                job.getId(),
                "pending",
                "Extraction job created. Poll status for results.",
                "/api/v1/extract/" + job.getId() + "/status"));
    }

    /**
     * Poll job status - returns location results when complete
     */
    @GetMapping("/extract/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable Long jobId) {
        ExtractionJobEntity job = jobRepository.findById(jobId).orElse(null);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        List<LocationResponse> locations = null;
        if (job.getStatus() == ExtractionJobEntity.Status.COMPLETED && job.getResultJson() != null) {
            try {
                locations = objectMapper.readValue(job.getResultJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, LocationResponse.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse result JSON: {}", e.getMessage());
            }
        }

        JobStatusResponse response = new JobStatusResponse(
                job.getId(),
                job.getStatus().name(),
                getStatusMessage(job.getStatus()),
                job.getFramesExtracted(),
                job.getLocationsFound(),
                job.getLocationsGrounded(),
                job.getErrorMessage(),
                locations);

        return ResponseEntity.ok(response);
    }

    private String getStatusMessage(ExtractionJobEntity.Status status) {
        return switch (status) {
            case PENDING -> "Job created, waiting to start";
            case PROCESSING -> "Processing extraction (download, AI analysis, grounding)";
            case COMPLETED -> "Extraction completed successfully";
            case FAILED -> "Extraction failed";
            default -> "Unknown status";
        };
    }

    /**
     * Legacy synchronous endpoint - kept for backward compatibility
     */
    @PostMapping("/extract/sync")
    public ResponseEntity<ExtractionResponse> extractLocationsSync(
            @Valid @RequestBody ExtractionRequest request) {

        long startTime = System.currentTimeMillis();
        String user = getUserId();
        String reelUrl = request.reelUrl();

        log.info("Starting SYNC extraction for user: {}, URL: {}", user, reelUrl);

        File downloadedVideo = null;

        try {
            // Check cache first
            if (reelCacheRepository.existsByReelUrl(reelUrl)) {
                log.info("URL found in cache: {}", reelUrl);
                ReelCacheEntity cache = reelCacheRepository.findByReelUrl(reelUrl).orElse(null);
                if (cache != null && cache.getLocationIds() != null) {
                    List<Long> locationIds = Arrays.stream(cache.getLocationIds().split(","))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());

                    List<LocationEntity> locations = locationRepository.findAllById(locationIds);
                    List<LocationResponse> responses = locations.stream()
                            .map(this::toLocationResponse)
                            .collect(Collectors.toList());

                    return ResponseEntity.ok(new ExtractionResponse(
                            "success",
                            "Returned cached result",
                            responses,
                            new ExtractionResponse.ExtractionStats(
                                    0, responses.size(), responses.size(), 0)));
                }
            }

            // Step 0: Handle Instagram URLs - download video and caption
            String caption = null;
            if (isInstagramUrl(reelUrl)) {
                log.info("Step 0: Downloading Instagram video with caption...");
                InstagramDownloadService.InstagramDownloadResult downloadResult = instagramDownloadService
                        .downloadFromInstagram(reelUrl);
                downloadedVideo = downloadResult.videoFile();
                caption = downloadResult.caption();
            }

            // Step 1: Extract frames
            log.info("Step 1: Extracting frames from video...");
            FfmpegService.FrameExtractionResult frameResult;

            if (downloadedVideo != null) {
                frameResult = ffmpegService.extractFramesFromFile(downloadedVideo);
            } else {
                frameResult = ffmpegService.extractFrames(reelUrl);
            }

            int frameCount = frameResult.frames().size();
            log.info("Extracted {} frames", frameCount);

            // Step 2: AI Extraction - Caption-first, then vision fallback
            log.info("Step 2: Analyzing for locations (caption-first)...");
            List<LocationExtraction> extractions = optimizedExtractionService.extractLocations(caption,
                    frameResult.frames());
            log.info("AI found {} potential locations", extractions.size());

            // Clean up
            ffmpegService.cleanup(frameResult.videoFile(), frameResult.frames());
            if (downloadedVideo != null && downloadedVideo.exists()) {
                downloadedVideo.delete();
            }

            // Step 3: Google Places Grounding
            log.info("Step 3: Grounding locations with Google Places...");
            List<LocationEntity> groundedLocations = groundingService.groundLocations(
                    extractions, user, reelUrl);
            log.info("Grounded {} locations", groundedLocations.size());

            // Save to cache
            if (!groundedLocations.isEmpty()) {
                ReelCacheEntity cache = new ReelCacheEntity();
                cache.setReelUrl(reelUrl);
                cache.setLocationIds(groundedLocations.stream()
                        .map(l -> l.getId().toString())
                        .collect(Collectors.joining(",")));
                reelCacheRepository.save(cache);
            }

            List<LocationResponse> locationResponses = groundedLocations.stream()
                    .map(this::toLocationResponse)
                    .collect(Collectors.toList());

            long processingTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(new ExtractionResponse(
                    "success",
                    "Extraction completed successfully",
                    locationResponses,
                    new ExtractionResponse.ExtractionStats(
                            frameCount,
                            extractions.size(),
                            groundedLocations.size(),
                            processingTime)));

        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage(), e);
            if (downloadedVideo != null && downloadedVideo.exists()) {
                downloadedVideo.delete();
            }
            return ResponseEntity.internalServerError()
                    .body(new ExtractionResponse(
                            "error",
                            "Extraction failed: " + e.getMessage(),
                            new ArrayList<>(),
                            null));
        }
    }

    /**
     * Get all locations for the authenticated user
     */
    @GetMapping("/locations")
    public ResponseEntity<List<LocationResponse>> getLocations(
) {

        String user = getUserId();
        log.info("Fetching locations for user: {}", user);

        List<LocationEntity> locations = locationRepository.findByUserIdOrderByCreatedAtDesc(user);

        List<LocationResponse> responses = locations.stream()
                .map(this::toLocationResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get single location by ID (only if owned by authenticated user)
     */
    @GetMapping("/locations/{id}")
    public ResponseEntity<LocationResponse> getLocation(
            @PathVariable Long id) {

        String user = getUserId();
        var location = locationRepository.findById(id);

        if (location.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Check ownership
        if (!user.equals(location.get().getUserId())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toLocationResponse(location.get()));
    }

    /**
     * Delete a location
     */
    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deleteLocation(
            @PathVariable Long id) {

        String user = getUserId();
        var location = locationRepository.findById(id);
        if (location.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!user.equals(location.get().getUserId())) {
            return ResponseEntity.status(403).build();
        }

        locationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    /**
     * FFmpeg availability check
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        boolean ffmpegAvailable = ffmpegService.isFfmpegAvailable();
        boolean ytDlpAvailable = instagramDownloadService.isYtDlpAvailable();

        return ResponseEntity.ok(java.util.Map.of(
                "status", ffmpegAvailable && ytDlpAvailable ? "ready" : "missing_dependencies",
                "ffmpegAvailable", ffmpegAvailable,
                "ytDlpAvailable", ytDlpAvailable));
    }

    private boolean isInstagramUrl(String url) {
        return url != null && url.contains("instagram.com");
    }

    private LocationResponse toLocationResponse(LocationEntity entity) {
        return new LocationResponse(
                entity.getId(),
                entity.getName(),
                entity.getAddress(),
                entity.getPlaceId(),
                entity.getCategory(),
                entity.getConfidence(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getReelUrl(),
                entity.getCreatedAt());
    }
}
