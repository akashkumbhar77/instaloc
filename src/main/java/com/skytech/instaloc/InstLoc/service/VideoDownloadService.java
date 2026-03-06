package com.skytech.instaloc.InstLoc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to handle video downloading from various sources.
 * Currently supports direct URLs. For Instagram, use a download service API.
 */
@Service
public class VideoDownloadService {

    private static final Logger log = LoggerFactory.getLogger(VideoDownloadService.class);

    @Value("${app.extraction.temp-directory:/tmp/instaloc}")
    private String tempDirectory;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Download video from URL and return local file path
     * For Instagram URLs, this will need a third-party service
     */
    public Path downloadVideo(String videoUrl) throws IOException {
        log.info("Downloading video from: {}", videoUrl);

        // Check if it's an Instagram URL
        if (isInstagramUrl(videoUrl)) {
            return downloadFromInstagram(videoUrl);
        }

        // For direct URLs, download directly
        return downloadDirect(videoUrl);
    }

    private boolean isInstagramUrl(String url) {
        return url.contains("instagram.com/reel/") ||
               url.contains("instagram.com/p/") ||
               url.contains("instagram.com/tv/");
    }

    /**
     * Download from Instagram using a third-party service
     * For MVP, we'll use a simple approach - in production, use RapidAPI
     */
    private Path downloadFromInstagram(String instagramUrl) throws IOException {
        log.info("Instagram URL detected: {}", instagramUrl);

        // Extract the shortcode from Instagram URL
        String shortcode = extractInstagramShortcode(instagramUrl);
        if (shortcode == null) {
            throw new IOException("Could not extract shortcode from Instagram URL");
        }

        // For MVP: Try using oembed to get info, then a download approach
        // In production: Use RapidAPI Instagram Downloader API

        // Try alternative: construct the oembed URL
        String oembedUrl = "https://api.instagram.com/oembed/?url=" + instagramUrl;

        try {
            // This is a placeholder - in production, integrate with RapidAPI or similar
            // For now, we'll throw a more helpful error
            throw new IOException(
                "Instagram download requires additional setup. " +
                "For MVP, please use a direct video URL or upload the video file. " +
                "To enable Instagram download, integrate with RapidAPI Instagram Downloader service."
            );
        } catch (Exception e) {
            throw new IOException("Instagram download not configured: " + e.getMessage());
        }
    }

    private String extractInstagramShortcode(String url) {
        Pattern pattern = Pattern.compile("/reel/([A-Za-z0-9_-]+)/|/p/([A-Za-z0-9_-]+)/|/tv/([A-Za-z0-9_-]+)/");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) :
                   matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        }
        return null;
    }

    private Path downloadDirect(String videoUrl) throws IOException {
        Path tempDir = Files.createTempDirectory(Path.of(tempDirectory), "download-");
        Path outputPath = tempDir.resolve("video.mp4");

        try {
            byte[] videoData = restTemplate.getForObject(URI.create(videoUrl), byte[].class);
            if (videoData == null) {
                throw new IOException("Empty response from video URL");
            }

            if (videoData.length > 100 * 1024 * 1024) {
                throw new IOException("Video file too large (max 100MB)");
            }

            Files.write(outputPath, videoData);
            log.info("Video downloaded successfully: {} bytes", videoData.length);
            return outputPath;

        } catch (Exception e) {
            // Clean up on failure
            try {
                Files.deleteIfExists(outputPath);
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
            throw new IOException("Failed to download video: " + e.getMessage(), e);
        }
    }
}
