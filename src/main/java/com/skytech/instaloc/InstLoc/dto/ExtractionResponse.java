package com.skytech.instaloc.InstLoc.dto;

import java.util.List;

public record ExtractionResponse(
    String status,
    String message,
    List<LocationResponse> locations,
    ExtractionStats stats
) {
    public record ExtractionStats(
        int framesExtracted,
        int locationsFound,
        int locationsGrounded,
        long processingTimeMs
    ) {}
}
