package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Aggregates every filter the application list endpoint supports.
 *
 * <p>{@code search} is the global, free-text query matched against several text columns at once.
 * The remaining fields are the per-field "advanced" filters, each applied independently with an AND.
 */
@Schema(description = "Filter criteria for listing job applications")
public record ApplicationFilter(
        @Schema(description = "Global free-text search across vacancy name, recruiter, organization, note, platform and status")
        String search,
        @Schema(description = "Exact status display name, or 'TO_SEND_LATER' for drafts")
        String status,
        @Schema(description = "Vacancy name partial match")
        String vacancyName,
        @Schema(description = "Recruiter name partial match")
        String recruiterName,
        @Schema(description = "Organization partial match")
        String organization,
        @Schema(description = "Exact vacancy URL match — used for duplicate detection")
        String vacancyLink,
        @Schema(description = "Note partial match")
        String note,
        @Schema(description = "Platform partial match")
        String platform,
        @Schema(description = "Application date range start (inclusive, yyyy-MM-dd)")
        LocalDate applicationDateFrom,
        @Schema(description = "Application date range end (inclusive, yyyy-MM-dd)")
        LocalDate applicationDateTo,
        @Schema(description = "Next-step date range start (inclusive, yyyy-MM-dd)")
        LocalDate nextStepDateFrom,
        @Schema(description = "Next-step date range end (inclusive, yyyy-MM-dd)")
        LocalDate nextStepDateTo,
        @Schema(description = "Filter by interview scheduled flag")
        Boolean interviewScheduled,
        @Schema(description = "Filter by recruiter DM reminder flag")
        Boolean recruiterDmReminderEnabled,
        @Schema(description = "Filter by recruiter accepted connection flag")
        Boolean rhAcceptedConnection,
        @Schema(description = "Filter by to-send-later (draft) flag")
        Boolean toSendLater,
        @Schema(description = "Minimum interview count (inclusive)")
        Integer interviewCountMin,
        @Schema(description = "Maximum interview count (inclusive)")
        Integer interviewCountMax,
        @Schema(description = "Filter by archived flag (defaults to false)")
        Boolean archived
) {}
