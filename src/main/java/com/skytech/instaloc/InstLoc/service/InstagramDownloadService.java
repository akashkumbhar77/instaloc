package com.skytech.instaloc.InstLoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to download Instagram videos using yt-dlp
 */
@Service
public class InstagramDownloadService {

    private static final Logger log = LoggerFactory.getLogger(InstagramDownloadService.class);

    @Value("${app.extraction.temp-directory:/tmp/instaloc}")
    private String tempDirectory;

    private static final int TIMEOUT_MINUTES = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Result of Instagram download containing video and optional caption
     */
    public record InstagramDownloadResult(File videoFile, String caption) {}

    /**
     * Result containing video URL and caption
     */
    public record VideoUrlResult(String videoUrl, String caption) {}

    /**
     * Get direct video URL and caption from Instagram without downloading
     * @param instagramUrl The Instagram reel URL
     * @return VideoUrlResult containing both videoUrl and caption
     * @throws IOException If extraction fails
     */
    public VideoUrlResult getVideoUrlWithCaption(String instagramUrl) throws IOException, InterruptedException {
        log.info("Getting video URL and caption from: {}", instagramUrl);

        if (!isValidInstagramUrl(instagramUrl)) {
            throw new IOException("Invalid Instagram URL: " + instagramUrl);
        }

        // Use --dump-json to get both URL and description in JSON format
        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "-f", "best[ext=mp4]/best",
            "--dump-json",
            "--no-warnings",
            instagramUrl
        );

        pb.redirectErrorStream(true);
        log.info("Running yt-dlp --dump-json for: {}", instagramUrl);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("yt-dlp timeout while getting video URL");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("yt-dlp failed with exit code: " + exitCode);
        }

        String jsonOutput = output.toString().trim();
        log.debug("JSON output: {}", jsonOutput.substring(0, Math.min(500, jsonOutput.length())));

        // Parse JSON to extract url and description
        String videoUrl = null;
        String caption = null;

        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonOutput);
            videoUrl = root.has("url") ? root.get("url").asText() : null;
            caption = root.has("description") ? root.get("description").asText() : null;
        } catch (Exception e) {
            log.warn("Failed to parse JSON output: {}", e.getMessage());
            // Fallback: try to extract URL from first line
            String[] lines = jsonOutput.split("\n");
            for (String line : lines) {
                if (line.contains("\"url\"")) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(line);
                        videoUrl = node.has("url") ? node.get("url").asText() : null;
                        caption = node.has("description") ? node.get("description").asText() : null;
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IOException("Failed to extract video URL from yt-dlp output");
        }

        log.info("Got video URL: {} (caption length: {})", videoUrl, caption != null ? caption.length() : 0);
        return new VideoUrlResult(videoUrl, caption);
    }

    /**
     * Get direct video URL from Instagram without downloading (legacy)
     * @param instagramUrl The Instagram reel URL
     * @return Direct URL to the video file
     * @throws IOException If extraction fails
     */
    public String getVideoUrl(String instagramUrl) throws IOException, InterruptedException {
        VideoUrlResult result = getVideoUrlWithCaption(instagramUrl);
        return result.videoUrl();
    }

    /**
     * Download video from Instagram URL using yt-dlp with metadata
     * @param instagramUrl The Instagram reel URL
     * @return InstagramDownloadResult with video file and caption
     * @throws IOException If download fails
     */
    public InstagramDownloadResult downloadFromInstagram(String instagramUrl) throws IOException, InterruptedException {
        log.info("Downloading from Instagram: {}", instagramUrl);

        // Validate URL
        if (!isValidInstagramUrl(instagramUrl)) {
            throw new IOException("Invalid Instagram URL: " + instagramUrl);
        }

        // Create temp directory
        Path downloadDir = Path.of(tempDirectory);
        try {
            Files.createDirectories(downloadDir);
        } catch (IOException e) {
            log.warn("Could not create temp directory: {}", e.getMessage());
            downloadDir = Path.of("/tmp");
        }

        String outputTemplate = downloadDir.resolve("instaloc_%(id)s.%(ext)s").toString();

        // Build yt-dlp command - include description/metadata
        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "-f", "best[ext=mp4]/best",
            "--write-description",  // Download description to .description file
            "--write-info-json",    // Download metadata to .info.json file
            "-o", outputTemplate,
            "--no-playlist",
            "--no-warnings",
            instagramUrl
        );

        pb.redirectErrorStream(true);

        log.info("Running yt-dlp with metadata for: {}", instagramUrl);

        Process process = pb.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("yt-dlp: {}", line);
            }
        }

        // Wait for completion
        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Instagram download timed out after " + TIMEOUT_MINUTES + " minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("yt-dlp failed with exit code {}: {}", exitCode, output);
            throw new IOException("Failed to download Instagram video. Exit code: " + exitCode);
        }

        // Find the downloaded file
        File videoFile = findDownloadedFile(downloadDir, instagramUrl);

        if (videoFile == null || !videoFile.exists()) {
            throw new IOException("Download completed but video file not found");
        }

        // Read caption from description file
        String caption = readDescriptionFile(downloadDir, instagramUrl);

        log.info("Successfully downloaded video: {} ({} bytes)", videoFile.getName(), videoFile.length());
        if (caption != null && !caption.isBlank()) {
            log.info("Caption length: {} characters", caption.length());
        }

        return new InstagramDownloadResult(videoFile, caption);
    }

    /**
     * Read caption from .description file
     */
    private String readDescriptionFile(Path downloadDir, String url) {
        try {
            String videoId = extractVideoId(url);
            // Look for .description file
            File[] descFiles = downloadDir.toFile().listFiles((dir, name) ->
                name.contains(videoId) && name.endsWith(".description")
            );

            if (descFiles != null && descFiles.length > 0) {
                String content = Files.readString(descFiles[0].toPath());
                log.debug("Description content: {}", content.substring(0, Math.min(200, content.length())));
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("Could not read description file: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if URL is a valid Instagram URL
     */
    private boolean isValidInstagramUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.contains("instagram.com/reel/") ||
               url.contains("instagram.com/p/") ||
               url.contains("instagram.com/tv/");
    }

    /**
     * Find the downloaded file in the temp directory
     */
    private File findDownloadedFile(Path downloadDir, String url) throws IOException {
        String videoId = extractVideoId(url);

        File[] files = downloadDir.toFile().listFiles((dir, name) ->
            name.contains(videoId) && name.endsWith(".mp4")
        );

        if (files != null && files.length > 0) {
            File latest = files[0];
            for (File f : files) {
                if (f.lastModified() > latest.lastModified()) {
                    latest = f;
                }
            }
            return latest;
        }

        // Fallback: any mp4 file
        files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp4"));
        if (files != null && files.length > 0) {
            return files[0];
        }

        return null;
    }

    /**
     * Extract video ID from Instagram URL
     */
    private String extractVideoId(String url) {
        Pattern pattern = Pattern.compile("/reel/([A-Za-z0-9_-]+)|/p/([A-Za-z0-9_-]+)|/tv/([A-Za-z0-9_-]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) :
                   matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        }
        return "unknown";
    }

    /**
     * Check if yt-dlp is available
     */
    public boolean isYtDlpAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            if (available) {
                log.info("yt-dlp is available");
            } else {
                log.warn("yt-dlp check failed");
            }
            return available;
        } catch (Exception e) {
            log.warn("yt-dlp not available: {}", e.getMessage());
            return false;
        }
    }
}
