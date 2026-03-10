package com.skytech.instaloc.InstLoc.service;

import com.skytech.instaloc.InstLoc.dto.LocationExtraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimized AI extraction service using caption-first approach
 * Step A: Try text-only extraction from caption
 * Step B: Fallback to vision if caption doesn't yield confident results
 */
@Service
public class OptimizedExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OptimizedExtractionService.class);

    private final ChatClient chatClient;

    private static final String CAPTION_EXTRACTION_PROMPT = """
            You are a location extraction expert. Analyze this Instagram caption/description and extract ALL locations mentioned.

            Look for:
            - Restaurant names, cafe names, bar names
            - Hotel names, landmark names
            - Street names, neighborhood names
            - Beach names, park names
            - Any location tags (@location or #location hashtags)

            For each location found, provide:
            - name: The exact name as mentioned
            - category: One of [restaurant, cafe, bar, beach, landmark, hotel, street, park, market, other]
            - confidence: How confident you are (0.0 to 1.0)

            Return ONLY a valid JSON array. Example:
            [{"name": "Starbucks", "category": "cafe", "confidence": 0.9}, {"name": "Eiffel Tower", "category": "landmark", "confidence": 0.95}]

            If no locations found, return empty array: []
            """;

    private static final String VISION_EXTRACTION_PROMPT = """
            You are an expert at analyzing Instagram Reel frames to find locations.

            TASK: Look carefully at every frame and identify ALL locations shown in the video.

            For each location you find, extract:
            - name: The EXACT business name, building name, or place name as shown
            - category: One of [restaurant, cafe, bar, beach, landmark, hotel, street, park, mall, other]
            - confidence: How sure you are (0.0 to 1.0)

            CRITICAL: Read all text in frames - signs, logos, menus, street names.
            Be thorough - extract every single location.

            Return ONLY a valid JSON array. Example:
            [{"name": "Starbucks", "category": "cafe", "confidence": 0.9}]

            If you find NO locations, return empty array: []
            """;

    public OptimizedExtractionService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * Two-step extraction: Caption-first, then vision fallback
     * @param caption The Instagram caption/description (can be null)
     * @param frames The video frames (can be empty)
     * @return List of extracted locations
     */
    public List<LocationExtraction> extractLocations(String caption, List<File> frames) {
        log.info("Starting optimized extraction - caption: {}, frames: {}",
            caption != null ? caption.length() + " chars" : "null",
            frames != null ? frames.size() : 0);

        // Step A: Try caption-first (text-only)
        if (caption != null && !caption.isBlank()) {
            List<LocationExtraction> captionLocations = extractFromCaption(caption);
            if (!captionLocations.isEmpty()) {
                log.info("Caption extraction found {} locations", captionLocations.size());
                return captionLocations;
            }
        }

        // Step B: Vision fallback (only if caption didn't find locations)
        if (frames != null && !frames.isEmpty()) {
            log.info("Caption extraction found nothing, falling back to vision");
            return extractFromFrames(frames);
        }

        log.info("No caption or frames available for extraction");
        return Collections.emptyList();
    }

    /**
     * Two-step extraction with base64 images (for client-side FFmpeg)
     * @param caption The Instagram caption/description (can be null)
     * @param base64Images List of base64-encoded images
     * @return List of extracted locations
     */
    public List<LocationExtraction> extractLocations(String caption, List<String> base64Images) {
        log.info("Starting optimized extraction - caption: {}, base64 images: {}",
            caption != null ? caption.length() + " chars" : "null",
            base64Images != null ? base64Images.size() : 0);

        // Step A: Try caption-first (text-only)
        if (caption != null && !caption.isBlank()) {
            List<LocationExtraction> captionLocations = extractFromCaption(caption);
            if (!captionLocations.isEmpty()) {
                log.info("Caption extraction found {} locations", captionLocations.size());
                return captionLocations;
            }
        }

        // Step B: Vision fallback with base64 images
        if (base64Images != null && !base64Images.isEmpty()) {
            log.info("Caption extraction found nothing, falling back to vision with base64 images");
            return extractFromBase64Images(base64Images);
        }

        log.info("No caption or images available for extraction");
        return Collections.emptyList();
    }

    /**
     * Step A: Extract locations from caption text
     */
    public List<LocationExtraction> extractFromCaption(String caption) {
        try {
            log.info("Extracting locations from caption ({} chars)", caption.length());

            String response = chatClient.prompt()
                    .system(CAPTION_EXTRACTION_PROMPT)
                    .user(caption)
                    .call()
                    .content();

            log.debug("Caption AI response: {}", response.substring(0, Math.min(500, response.length())));

            return parseLocationsResponse(response);

        } catch (Exception e) {
            log.error("Caption extraction failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Step B: Extract locations from video frames
     */
    public List<LocationExtraction> extractFromFrames(List<File> frames) {
        try {
            log.info("Extracting locations from {} frames (optimized - max 5)", frames.size());

            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(VISION_EXTRACTION_PROMPT);
                        // Use all available frames (max 5)
                        for (File frame : frames) {
                            u.media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG,
                                new org.springframework.core.io.FileSystemResource(frame));
                        }
                    })
                    .call()
                    .content();

            log.debug("Vision AI response: {}", response.substring(0, Math.min(500, response.length())));

            return parseLocationsResponse(response);

        } catch (Exception e) {
            log.error("Vision extraction failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Step B (alt): Extract locations from base64-encoded images
     */
    public List<LocationExtraction> extractFromBase64Images(List<String> base64Images) {
        try {
            log.info("Extracting locations from {} base64 images", base64Images.size());

            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(VISION_EXTRACTION_PROMPT);
                        // Send images as data URLs
                        for (String base64 : base64Images) {
                            u.media("image/jpeg", "data:image/jpeg;base64," + base64);
                        }
                    })
                    .call()
                    .content();

            log.debug("Vision AI response: {}", response.substring(0, Math.min(500, response.length())));

            return parseLocationsResponse(response);

        } catch (Exception e) {
            log.error("Base64 vision extraction failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parse AI response to extract location objects
     */
    private List<LocationExtraction> parseLocationsResponse(String response) {
        String jsonContent = extractJsonArray(response);

        if (jsonContent == null || jsonContent.trim().isEmpty() || jsonContent.equals("[]")) {
            return Collections.emptyList();
        }

        List<LocationExtraction> locations = new ArrayList<>();

        // Simple regex parsing
        Pattern pattern = Pattern.compile(
            "\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"category\"\\s*:\\s*\"([^\"]+)\"[^}]*\"confidence\"\\s*:\\s*([0-9.]+)[^}]*\\}"
        );

        Matcher matcher = pattern.matcher(jsonContent);
        while (matcher.find()) {
            String name = matcher.group(1);
            String category = matcher.group(2);
            Double confidence = Double.parseDouble(matcher.group(3));
            locations.add(new LocationExtraction(name, category, confidence));
        }

        log.info("Parsed {} locations from response", locations.size());
        return locations;
    }

    /**
     * Extract JSON array from response text
     */
    private String extractJsonArray(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.trim();
    }
}
