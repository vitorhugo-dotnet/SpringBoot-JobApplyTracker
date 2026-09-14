package com.jobtracker.dto.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AssistantApplicationView(
        UUID id,
        String vacancyName,
        String organization,
        String recruiterName,
        String status,
        LocalDate applicationDate,
        String platform,
        int interviewCount,
        LocalDateTime nextStepDateTime,
        String noteExcerpt,
        boolean archived,
        Double relevance
) {}
