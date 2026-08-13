package com.jobtracker.dto.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Filters applied before an export runs. They map onto the very same predicates used by the
 * application listing endpoint (see
 * {@link com.jobtracker.repository.ApplicationSpecifications#forExport}).
 *
 * <p>Persisted as JSON on a schedule, so unknown properties are ignored to keep old rows readable
 * after new filters are added.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Filters applied to the applications before exporting")
public record ExportFilters(
        @Schema(description = "Status display names to include; use 'TO_SEND_LATER' for drafts. Empty means every status.")
        List<String> status,

        @Schema(description = "Global free-text search across vacancy, recruiter, organization, note, platform and status")
        String search,

        @Schema(description = "Organization partial match")
        String organization,

        @Schema(description = "Platform partial match")
        String platform,

        @Schema(description = "Application date range start (inclusive, yyyy-MM-dd)")
        LocalDate applicationDateFrom,

        @Schema(description = "Application date range end (inclusive, yyyy-MM-dd)")
        LocalDate applicationDateTo,

        @Schema(description = "true = only archived, false = only active, null = both")
        Boolean archived,

        @Schema(description = "Filter by interview scheduled flag")
        Boolean interviewScheduled,

        @Schema(description = "Filter by to-send-later (draft) flag")
        Boolean toSendLater
) {

    public static ExportFilters empty() {
        return new ExportFilters(List.of(), null, null, null, null, null, null, null, null);
    }
}
