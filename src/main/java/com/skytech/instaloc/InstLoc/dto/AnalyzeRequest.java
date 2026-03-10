package com.skytech.instaloc.InstLoc.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AnalyzeRequest(
    @NotNull @NotEmpty List<String> images,
    String caption,
    String reelUrl
) {}
