package com.skytech.instaloc.InstLoc.dto;

public record UploadResponse(
    String status,
    String message,
    String tempVideoPath
) {}
