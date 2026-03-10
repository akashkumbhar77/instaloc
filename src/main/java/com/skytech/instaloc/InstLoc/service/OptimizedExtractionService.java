package com.skytech.instaloc.InstLoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            You are an expert geospatial intelligence agent. Analyze these video frames and the accompanying caption text to identify specific, mappable locations.

            TASK: Cross-reference visual clues in the frames with the caption text. Look for storefront signs, street names, menu boards, logos, and iconic landmarks. Identify specific, real-world locations that can be mapped (cafes, hotels, restaurants, attractions, etc.).

            IGNORE: Generic objects, people, food items, furniture, vehicles, or anything that is not a specific place.

            STRICT REQUIREMENTS:
            1. You MUST classify every location into ONE of these exact categories: [Restaurant, Cafe, Hotel, Landmark, Shopping, Nature]
            2. You MUST identify the broader region or state (e.g., Hanoi, Quang Ninh, Ho Chi Minh City, etc.)

            For each location found, provide:
            - name: The EXACT business name, building name, or place name as shown in signs/logos
            - address: The street address if visible, otherwise the neighborhood/area name
            - category: EXACTLY ONE of [Restaurant, Cafe, Hotel, Landmark, Shopping, Nature] - use this exact casing
            - stateOrRegion: The broader region, state, or city (e.g., Hanoi, Quang Ninh, Bali, etc.)
            - latitude: Estimated latitude if you can determine location (optional)
            - longitude: Estimated longitude if you can determine location (optional)
            - confidence: How sure you are this is a real, mappable location (0.0 to 1.0)

            CRITICAL INSTRUCTIONS:
            1. Read ALL text in frames - signs, logos, menus, street signs, store fronts
            2. Cross-reference visual clues with caption text for verification
            3. Be AGGRESSIVE - if there's any indication of a location, include it
            4. Prioritize named businesses with visible signage
            5. Ignore generic objects completely
            6. ALWAYS include stateOrRegion - infer from signs, captions, or context

            Return ONLY a valid JSON array. Example:
            [{"name": "Blue Bottle Coffee", "address": "123 Main St", "category": "Cafe", "stateOrRegion": "Hanoi", "latitude": 21.0285, "longitude": 105.8542, "confidence": 0.95}]

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
    public List<LocationExtraction> extractLocationsFromBase64(String caption, List<String> base64Images) {
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

        // Step B: Vision fallback with base64 images (pass caption for context)
        if (base64Images != null && !base64Images.isEmpty()) {
            log.info("Caption extraction found nothing, falling back to vision with base64 images");
            return extractFromBase64Images(base64Images, caption);
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
     * Step B (alt): Extract locations from base64-encoded images with caption context
     */
    public List<LocationExtraction> extractFromBase64Images(List<String> base64Images, String caption) {
        try {
            log.info("Extracting locations from {} base64 images (caption: {})",
                base64Images.size(), caption != null ? caption.length() + " chars" : "none");

            // Build user prompt with caption context (use final variable for lambda)
            final String userPrompt;
            if (caption != null && !caption.isBlank()) {
                userPrompt = VISION_EXTRACTION_PROMPT + "\n\nCAPTION TEXT (for cross-reference):\n" + caption;
            } else {
                userPrompt = VISION_EXTRACTION_PROMPT;
            }

            // Capture base64Images in final array for lambda
            final List<String> finalBase64Images = base64Images;

            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(userPrompt);
                        // Send images as data URLs using ByteArrayResource
                        for (String base64 : finalBase64Images) {
                            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64);
                            u.media(org.springframework.util.MimeTypeUtils.IMAGE_JPEG,
                                new org.springframework.core.io.ByteArrayResource(imageBytes));
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

        // Try JSON parsing first (more robust)
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonContent);
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    String name = node.has("name") ? node.get("name").asText() : null;
                    if (name != null && !name.isBlank()) {
                        String address = node.has("address") ? node.get("address").asText() : null;
                        String category = node.has("category") ? node.get("category").asText() : null;
                        String stateOrRegion = node.has("stateOrRegion") ? node.get("stateOrRegion").asText() : null;
                        Double latitude = node.has("latitude") && !node.get("latitude").isNull()
                            ? node.get("latitude").asDouble() : null;
                        Double longitude = node.has("longitude") && !node.get("longitude").isNull()
                            ? node.get("longitude").asDouble() : null;
                        Double confidence = node.has("confidence") && !node.get("confidence").isNull()
                            ? node.get("confidence").asDouble() : null;

                        locations.add(new LocationExtraction(name, address, category, stateOrRegion, latitude, longitude, confidence));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JSON parsing failed, falling back to regex: {}", e.getMessage());
            // Fallback to regex for backward compatibility
            Pattern pattern = Pattern.compile(
                "\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"category\"\\s*:\\s*\"([^\"]+)\"[^}]*\"confidence\"\\s*:\\s*([0-9.]+)[^}]*\\}"
            );

            Matcher matcher = pattern.matcher(jsonContent);
            while (matcher.find()) {
                String name = matcher.group(1);
                String category = matcher.group(2);
                Double confidence = Double.parseDouble(matcher.group(3));
                locations.add(new LocationExtraction(name, null, category, null, null, null, confidence));
            }
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
