package com.skytech.instaloc.InstLoc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    @Value("${app.extraction.frames-per-second:0.25}")
    private double framesPerSecond;

    @Value("${app.extraction.max-frames:5}")
    private int maxFrames;

    @Value("${app.extraction.temp-directory:/tmp/instaloc}")
    private String tempDirectory;

    public record FrameExtractionResult(List<File> frames, File videoFile) {}

    /**
     * Extract frames from a video URL
     */
    public FrameExtractionResult extractFrames(String videoUrl) throws IOException, InterruptedException {
        log.info("Starting frame extraction for URL: {}", videoUrl);

        Path extractionDir = Files.createTempDirectory(Path.of(tempDirectory), "reel-");
        Path videoPath = extractionDir.resolve("video.mp4");
        Path framesPath = extractionDir.resolve("frames");
        Files.createDirectory(framesPath);

        try {
            downloadVideo(videoUrl, videoPath);
            return extractFromPath(videoPath, framesPath, extractionDir.toFile());
        } catch (Exception e) {
            cleanupDirectory(extractionDir);
            throw e;
        }
    }

    /**
     * Extract frames from an uploaded video file
     */
    public FrameExtractionResult extractFrames(MultipartFile videoFile) throws IOException, InterruptedException {
        log.info("Starting frame extraction for uploaded file: {}", videoFile.getOriginalFilename());

        Path extractionDir = Files.createTempDirectory(Path.of(tempDirectory), "reel-");
        Path videoPath = extractionDir.resolve("video.mp4");
        Path framesPath = extractionDir.resolve("frames");
        Files.createDirectory(framesPath);

        try {
            // Save uploaded file
            videoFile.transferTo(videoPath);
            log.info("Video uploaded: {} bytes", videoPath.toFile().length());

            return extractFromPath(videoPath, framesPath, extractionDir.toFile());
        } catch (Exception e) {
            cleanupDirectory(extractionDir);
            throw e;
        }
    }

    /**
     * Extract frames from a pre-downloaded video file
     */
    public FrameExtractionResult extractFramesFromFile(File videoFile) throws IOException, InterruptedException {
        log.info("Starting frame extraction for downloaded file: {}", videoFile.getName());

        Path extractionDir = Files.createTempDirectory(Path.of(tempDirectory), "reel-");
        Path videoPath = extractionDir.resolve("video.mp4");
        Path framesPath = extractionDir.resolve("frames");
        Files.createDirectory(framesPath);

        try {
            // Copy the downloaded file to our extraction directory
            Files.copy(videoFile.toPath(), videoPath);
            log.info("Video copied: {} bytes", videoPath.toFile().length());

            return extractFromPath(videoPath, framesPath, extractionDir.toFile());
        } catch (Exception e) {
            cleanupDirectory(extractionDir);
            throw e;
        }
    }

    private FrameExtractionResult extractFromPath(Path videoPath, Path framesPath, File extractionDir)
            throws IOException, InterruptedException {

        log.info("Extracting frames at {} fps (max {} frames)", framesPerSecond, maxFrames);
        List<File> frames = extractFramesFromVideo(videoPath, framesPath);

        log.info("Successfully extracted {} frames", frames.size());
        return new FrameExtractionResult(frames, videoPath.toFile());
    }

    private void downloadVideo(String videoUrl, Path outputPath) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new URL(videoUrl).openStream());
             FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                if (totalBytes > 100 * 1024 * 1024) {
                    throw new IOException("Video file too large (max 100MB)");
                }
            }
        }
        log.info("Video downloaded: {} bytes", outputPath.toFile().length());
    }

    private List<File> extractFramesFromVideo(Path videoPath, Path framesPath) throws IOException, InterruptedException {
        // Uniform fps-based sampling: always deterministic, same frames for same reel.
        // framesPerSecond (default 0.25) = 1 frame every 4s, capped at maxFrames.
        String videoFilter = String.format("fps=%s,scale=768:-1", framesPerSecond);

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i", videoPath.toString(),
            "-vf", videoFilter,
            "-q:v", "3",
            "-frames:v", String.valueOf(maxFrames),
            framesPath.resolve("frame-%03d.jpg").toString()
        );

        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(60, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("FFmpeg process timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("FFmpeg output: {}", output);
            throw new IOException("FFmpeg failed with exit code " + exitCode);
        }

        List<File> frames = new ArrayList<>();
        Files.list(framesPath)
            .filter(p -> p.toString().endsWith(".jpg"))
            .sorted()
            .forEach(p -> frames.add(p.toFile()));

        return frames;
    }

    public void cleanup(File videoFile, List<File> frames) {
        log.info("Cleaning up temporary files");

        for (File frame : frames) {
            try {
                Files.deleteIfExists(frame.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete frame: {}", frame.getName());
            }
        }

        if (videoFile != null && videoFile.exists()) {
            try {
                Files.deleteIfExists(videoFile.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete video: {}", videoFile.getName());
            }
        }

        if (videoFile != null && videoFile.getParentFile() != null) {
            try {
                Path parentDir = videoFile.getParentFile().toPath();
                if (Files.exists(parentDir)) {
                    Files.walk(parentDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                }
            } catch (IOException e) {
                log.warn("Failed to clean up directory: {}", e.getMessage());
            }
        }
    }

    private void cleanupDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (IOException e) {
            log.warn("Failed to cleanup directory: {}", e.getMessage());
        }
    }

    public boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
