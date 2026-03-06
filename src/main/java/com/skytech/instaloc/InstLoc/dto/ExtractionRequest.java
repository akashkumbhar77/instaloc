package com.skytech.instaloc.InstLoc.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtractionRequest(
    @NotBlank(message = "Reel URL is required")
    String reelUrl
) {}
