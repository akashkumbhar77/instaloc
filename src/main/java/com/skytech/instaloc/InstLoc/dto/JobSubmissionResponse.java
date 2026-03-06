package com.skytech.instaloc.InstLoc.dto;

public record JobSubmissionResponse(
    Long jobId,
    String status,
    String message,
    String pollUrl
) {}
