package com.skytech.instaloc.InstLoc.dto;

import java.util.List;

public record JobStatusResponse(
    Long jobId,
    String status,
    String message,
    Integer framesExtracted,
    Integer locationsFound,
    Integer locationsGrounded,
    String errorMessage,
    List<LocationResponse> locations
) {}
