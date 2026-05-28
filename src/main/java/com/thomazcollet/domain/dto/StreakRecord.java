package com.thomazcollet.domain.dto;

import java.time.LocalDate;

public record StreakRecord(
    Long id,
    Long profileId,
    int durationDays,
    LocalDate startDate,
    LocalDate endDate
) {}