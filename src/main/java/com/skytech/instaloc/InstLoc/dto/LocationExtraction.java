package com.skytech.instaloc.InstLoc.dto;

import org.jspecify.annotations.Nullable;

public record LocationExtraction(
    String name,
    @Nullable String address,
    @Nullable String category,
    @Nullable Double latitude,
    @Nullable Double longitude,
    @Nullable Double confidence
) {}
