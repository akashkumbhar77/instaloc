package com.skytech.instaloc.InstLoc.dto;

import org.jspecify.annotations.Nullable;

public record LocationExtraction(
    String name,
    @Nullable String category,
    @Nullable Double confidence
) {}
