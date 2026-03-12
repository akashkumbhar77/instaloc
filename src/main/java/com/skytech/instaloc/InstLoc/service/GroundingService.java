package com.skytech.instaloc.InstLoc.service;

import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import com.skytech.instaloc.InstLoc.dto.LocationExtraction;
import com.skytech.instaloc.InstLoc.entity.LocationEntity;
import com.skytech.instaloc.InstLoc.repository.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GroundingService {

    private static final Logger log = LoggerFactory.getLogger(GroundingService.class);

    private final LocationRepository locationRepository;

    @Value("${google.maps.api-key}")
    private String googleMapsApiKey;

    private GeoApiContext geoApiContext;

    public GroundingService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @PostConstruct
    public void init() {
        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(googleMapsApiKey)
                .build();
    }

    /**
     * Ground extracted locations using Google Places API
     * @param extractions List of AI-extracted locations
     * @param userId User ID for ownership
     * @param reelUrl Source reel URL
     * @return List of grounded LocationEntity objects
     */
    public List<LocationEntity> groundLocations(
            List<LocationExtraction> extractions,
            String userId,
            String reelUrl) {

        if (extractions == null || extractions.isEmpty()) {
            log.warn("No extractions provided for grounding");
            return new ArrayList<>();
        }

        log.info("Grounding {} extracted locations: {}", extractions.size(), extractions);
        List<LocationEntity> groundedLocations = new ArrayList<>();

        for (LocationExtraction extraction : extractions) {
            try {
                log.info("Grounding location: {} (category: {}, confidence: {})",
                    extraction.name(), extraction.category(), extraction.confidence());
                LocationEntity entity = groundSingleLocation(extraction, userId, reelUrl);
                if (entity != null) {
                    groundedLocations.add(entity);
                    log.info("Successfully grounded: {}", entity.getName());
                } else {
                    log.warn("No result from Google Places for: {}", extraction.name());
                }
            } catch (Exception e) {
                log.error("Failed to ground location '{}': {}", extraction.name(), e.getMessage(), e);
            }
        }

        log.info("Successfully grounded {} out of {} locations", groundedLocations.size(), extractions.size());
        return groundedLocations;
    }

    /**
     * Ground a single location using Google Places API
     */
    private LocationEntity groundSingleLocation(
            LocationExtraction extraction,
            String userId,
            String reelUrl) throws IOException, InterruptedException, ApiException {

        // Try with original name first, then add location context
        String query = extraction.name();
        log.info("Searching for: {}", query);

        // Search for the place - try original query first
        PlacesSearchResponse response = null;
        Exception lastException = null;

        // Build search queries: ALWAYS prefer region-qualified query to avoid wrong-country results.
        // Bare name is only tried last as a fallback.
        List<String> queryList = new java.util.ArrayList<>();
        if (extraction.stateOrRegion() != null && !extraction.stateOrRegion().isBlank()) {
            queryList.add(query + " " + extraction.stateOrRegion());
        }
        // General India fallback if no region info
        queryList.add(query + " India");
        // Bare name last (accept any country only if nothing else matched)
        queryList.add(query);
        String[] searchQueries = queryList.toArray(new String[0]);

        for (String searchQuery : searchQueries) {
            try {
                log.info("Trying Google Places search: {}", searchQuery);
                response = PlacesApi.textSearchQuery(geoApiContext, searchQuery).await();
                if (response.results != null && response.results.length > 0) {
                    log.info("Found result for: {}", searchQuery);
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Search failed for '{}': {}", searchQuery, e.getMessage());
            }
        }

        if (response == null || response.results == null || response.results.length == 0) {
            log.warn("No Google Places result found for: '{}' — saving as ungrounded location", query);
            return saveUngrounded(extraction, userId, reelUrl);
        }

        // Post-search validation: reject results from wrong country/region when we have region context.
        PlacesSearchResult result = response.results[0];
        if (extraction.stateOrRegion() != null && !extraction.stateOrRegion().isBlank()
                && result.formattedAddress != null) {
            String addr = result.formattedAddress.toLowerCase();
            String region = extraction.stateOrRegion().toLowerCase();
            // Accept only if the address contains region OR a major Indian geo term
            boolean regionMatch = addr.contains(region)
                    || addr.contains("india") || addr.contains("india");
            if (!regionMatch) {
                log.warn("Rejected wrong-country result: '{}' address='{}' expected region='{}'",
                        result.name, result.formattedAddress, extraction.stateOrRegion());
                return saveUngrounded(extraction, userId, reelUrl);
            }
        }

        // Check if place already exists for this user (by placeId OR by name+region)
        if (result.placeId != null) {
            var existing = locationRepository.findByPlaceIdAndUserId(result.placeId, userId);
            if (existing.isPresent()) {
                log.info("Location already exists for this user: {}", result.name);
                return existing.get();
            }
        }

        // Create new entity
        LocationEntity entity = new LocationEntity();
        entity.setUserId(userId);
        entity.setName(result.name);
        entity.setAddress(result.formattedAddress);
        entity.setPlaceId(result.placeId);
        entity.setCategory(extraction.category());
        entity.setStateOrRegion(extraction.stateOrRegion());
        entity.setConfidence(extraction.confidence());
        entity.setReelUrl(reelUrl);

        // Set coordinates as simple doubles
        if (result.geometry != null && result.geometry.location != null) {
            entity.setLatitude(result.geometry.location.lat);
            entity.setLongitude(result.geometry.location.lng);
        }

        // Save to database
        return locationRepository.save(entity);
    }

    /**
     * Ground a single extraction and save to database
     */
    public LocationEntity groundAndSave(LocationExtraction extraction, String userId, String reelUrl) {
        try {
            return groundSingleLocation(extraction, userId, reelUrl);
        } catch (Exception e) {
            log.error("Failed to ground and save location: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Save an AI-extracted location without a Google Places placeId.
     * Deduplicates by (userId, name) to avoid stacking null-placeId duplicates.
     */
    private LocationEntity saveUngrounded(LocationExtraction extraction, String userId, String reelUrl) {
        // Check if we already have this name for this user
        var existing = locationRepository.findByUserIdAndNameIgnoreCase(userId, extraction.name());
        if (existing.isPresent()) {
            log.info("Ungrounded location already exists for user: {}", extraction.name());
            return existing.get();
        }
        LocationEntity ungrounded = new LocationEntity();
        ungrounded.setUserId(userId);
        ungrounded.setName(extraction.name());
        ungrounded.setAddress(extraction.address());
        ungrounded.setPlaceId(null);
        ungrounded.setCategory(extraction.category());
        ungrounded.setStateOrRegion(extraction.stateOrRegion());
        ungrounded.setConfidence(extraction.confidence());
        ungrounded.setReelUrl(reelUrl);
        if (extraction.latitude() != null) ungrounded.setLatitude(extraction.latitude());
        if (extraction.longitude() != null) ungrounded.setLongitude(extraction.longitude());
        return locationRepository.save(ungrounded);
    }
}
