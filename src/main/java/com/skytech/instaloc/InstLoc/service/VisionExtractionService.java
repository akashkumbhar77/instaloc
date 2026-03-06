package com.skytech.instaloc.InstLoc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytech.instaloc.InstLoc.dto.LocationExtraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class VisionExtractionService {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USER_PROMPT = """
            You are an expert at analyzing Instagram Reels to find locations.

            TASK: Look carefully at every frame and identify ALL locations shown in the video.

            For each location you find, extract:
            - name: The EXACT business name, building name, or place name as shown in the video
            - category: One of [restaurant, cafe, bar, beach, landmark, hotel, street, market, temple, park, mall, other]
            - confidence: How sure you are (0.0 to 1.0)

            CRITICAL INSTRUCTIONS:
            1. READ ALL TEXT IN THE VIDEO - signs, logos, menus, street names
            2. LOOK AT THE BACKGROUND - buildings, landmarks, storefronts
            3. IDENTIFY LANDMARKS - famous places, monuments, natural features
            4. NOTE RESTAURANTS/BARS - look for names on awnings, menus
            5. BE THOROUGH - extract every single location

            Return ONLY a valid JSON array. Example format:
            [{"name": "Starbucks", "category": "cafe", "confidence": 0.9}, {"name": "Marina Bay Sands", "category": "landmark", "confidence": 0.95}]

            If you find NO locations, return an empty array: []
            """;

    public VisionExtractionService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                    You are a location extraction expert for travel content.
                    Return ONLY valid JSON array, no explanations.
                    """)
                .build();
    }

    /**
     * Extract locations from a list of frame images
     */
    public List<LocationExtraction> extractLocations(List<File> frames) {
        if (frames == null || frames.isEmpty()) {
            log.warn("No frames provided for extraction");
            return Collections.emptyList();
        }

        log.info("Analyzing {} frames for location extraction", frames.size());

        try {
            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(USER_PROMPT);
                        int maxFrames = Math.min(frames.size(), 15);
                        for (int i = 0; i < maxFrames; i++) {
                            u.media(MimeTypeUtils.IMAGE_JPEG, new FileSystemResource(frames.get(i)));
                        }
                    })
                    .call()
                    .content();

            log.info("AI Response (first 500 chars): {}", response.substring(0, Math.min(500, response.length())));

            return parseLocationsResponse(response);

        } catch (Exception e) {
            log.error("Failed to extract locations from frames: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse the AI response using Jackson
     */
    private List<LocationExtraction> parseLocationsResponse(String response) {
        try {
            // Extract JSON array from response
            String jsonContent = extractJsonArray(response);
            log.info("Extracted JSON: {}", jsonContent);

            if (jsonContent == null || jsonContent.trim().isEmpty() || jsonContent.equals("[]")) {
                return Collections.emptyList();
            }

            // Parse using Jackson
            JsonNode rootNode = objectMapper.readTree(jsonContent);

            if (!rootNode.isArray()) {
                log.warn("Response is not a JSON array");
                return Collections.emptyList();
            }

            List<LocationExtraction> locations = new ArrayList<>();

            for (JsonNode node : rootNode) {
                String name = node.has("name") ? node.get("name").asText() : null;
                String category = node.has("category") ? node.get("category").asText() : "other";
                double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.5;

                if (name != null && !name.isBlank()) {
                    locations.add(new LocationExtraction(name, category, confidence));
                }
            }

            log.info("Successfully parsed {} locations", locations.size());
            return locations;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON: {}", e.getMessage());
            log.error("Raw response: {}", response);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error parsing: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract JSON array from response text
     */
    private String extractJsonArray(String response) {
        // Find the first [ and last ]
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        // Try to find JSON object
        start = response.indexOf('{');
        end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return "[" + response.substring(start, end + 1) + "]";
        }

        return response.trim();
    }
}
