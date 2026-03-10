package com.skytech.instaloc.InstLoc.dto;

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

public record LocationResponse(
    Long id,
    String name,
    @Nullable String address,
    @Nullable String placeId,
    @Nullable String category,
    @Nullable String stateOrRegion,
    @Nullable Double confidence,
    @Nullable Double latitude,
    @Nullable Double longitude,
    @Nullable String reelUrl,
    LocalDateTime createdAt
) {}
