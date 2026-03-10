package com.skytech.instaloc.InstLoc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytech.instaloc.InstLoc.dto.LocationExtraction;
import com.skytech.instaloc.InstLoc.dto.LocationResponse;
import com.skytech.instaloc.InstLoc.entity.ExtractionJobEntity;
import com.skytech.instaloc.InstLoc.entity.LocationEntity;
import com.skytech.instaloc.InstLoc.entity.ReelCacheEntity;
import com.skytech.instaloc.InstLoc.repository.ExtractionJobRepository;
import com.skytech.instaloc.InstLoc.repository.LocationRepository;
import com.skytech.instaloc.InstLoc.repository.ReelCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AsyncExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AsyncExtractionService.class);

    private final InstagramDownloadService instagramDownloadService;
    private final FfmpegService ffmpegService;
    private final OptimizedExtractionService optimizedExtractionService;
    private final GroundingService groundingService;
    private final ExtractionJobRepository jobRepository;
    private final LocationRepository locationRepository;
    private final ReelCacheRepository reelCacheRepository;
    private final ObjectMapper objectMapper;

    public AsyncExtractionService(
            InstagramDownloadService instagramDownloadService,
            FfmpegService ffmpegService,
            OptimizedExtractionService optimizedExtractionService,
            GroundingService groundingService,
            ExtractionJobRepository jobRepository,
            LocationRepository locationRepository,
            ReelCacheRepository reelCacheRepository) {
        this.instagramDownloadService = instagramDownloadService;
        this.ffmpegService = ffmpegService;
        this.optimizedExtractionService = optimizedExtractionService;
        this.groundingService = groundingService;
        this.jobRepository = jobRepository;
        this.locationRepository = locationRepository;
        this.reelCacheRepository = reelCacheRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Async("taskExecutor")
    public void processExtractionJob(Long jobId, String reelUrl, String userId) {
        log.info("Starting async extraction for job: {}, URL: {}", jobId, reelUrl);

        ExtractionJobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        try {
            // Update status to processing
            job.setStatus(ExtractionJobEntity.Status.PROCESSING);
            jobRepository.save(job);

            // Determine if it's an Instagram URL
            boolean isInstagram = reelUrl.contains("instagram.com");
            String caption = null;
            File downloadedVideo = null;
            FfmpegService.FrameExtractionResult frameResult = null;

            // Step 1: Get video and caption
            if (isInstagram) {
                log.info("Downloading Instagram video with caption for job: {}", jobId);
                InstagramDownloadService.InstagramDownloadResult downloadResult =
                    instagramDownloadService.downloadFromInstagram(reelUrl);
                downloadedVideo = downloadResult.videoFile();
                caption = downloadResult.caption();

                // Extract frames from downloaded video
                frameResult = ffmpegService.extractFramesFromFile(downloadedVideo);
            } else {
                log.info("Extracting frames from URL for job: {}", jobId);
                frameResult = ffmpegService.extractFrames(reelUrl);
            }

            int frameCount = frameResult.frames().size();
            job.setFramesExtracted(frameCount);
            log.info("Extracted {} frames for job: {}", frameCount, jobId);

            // Step 2: OPTIMIZED AI Extraction (Caption-first, then vision fallback)
            List<LocationExtraction> extractions = optimizedExtractionService.extractLocations(caption, frameResult.frames());
            job.setLocationsFound(extractions.size());
            log.info("AI found {} locations for job: {}", extractions.size(), jobId);

            // Clean up frames
            ffmpegService.cleanup(frameResult.videoFile(), frameResult.frames());

            // Clean up downloaded video
            if (downloadedVideo != null && downloadedVideo.exists()) {
                downloadedVideo.delete();
            }

            // Step 3: Google Places Grounding
            List<LocationEntity> groundedLocations = groundingService.groundLocations(
                    extractions, userId, reelUrl);
            job.setLocationsGrounded(groundedLocations.size());
            log.info("Grounded {} locations for job: {}", groundedLocations.size(), jobId);

            // Convert to response
            List<LocationResponse> locationResponses = groundedLocations.stream()
                    .map(this::toLocationResponse)
                    .collect(Collectors.toList());

            // Save cache entry
            if (!groundedLocations.isEmpty()) {
                ReelCacheEntity cache = new ReelCacheEntity();
                cache.setReelUrl(reelUrl);
                String locationIds = groundedLocations.stream()
                        .map(l -> l.getId().toString())
                        .collect(Collectors.joining(","));
                cache.setLocationIds(locationIds);
                reelCacheRepository.save(cache);
            }

            // Save result JSON
            try {
                job.setResultJson(objectMapper.writeValueAsString(locationResponses));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize result: {}", e.getMessage());
            }

            // Mark as completed
            job.setStatus(ExtractionJobEntity.Status.COMPLETED);
            jobRepository.save(job);

            log.info("Job {} completed successfully with {} locations", jobId, groundedLocations.size());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(ExtractionJobEntity.Status.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        }
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
                entity.getCreatedAt()
        );
    }
}
