package com.jobtracker.service.export;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The columns an application export can contain.
 *
 * <p>This enum is the allow-list: nothing outside it can ever reach an exported file, which keeps
 * internal and authentication-related fields (password hashes, tokens, Drive file identifiers,
 * gamification bookkeeping flags) out of user-facing exports by construction.
 */
public enum ExportColumn {

    ID("id", "ID", app -> app.getId() == null ? "" : app.getId().toString()),
    VACANCY_NAME("vacancyName", "Vacancy", JobApplication::getVacancyName),
    ORGANIZATION("organization", "Organization", JobApplication::getOrganization),
    RECRUITER_NAME("recruiterName", "Recruiter", JobApplication::getRecruiterName),
    PLATFORM("platform", "Platform", JobApplication::getPlatform),
    VACANCY_LINK("vacancyLink", "Vacancy link", JobApplication::getVacancyLink),
    STATUS("status", "Status", app -> app.getStatus() == null ? "TO_SEND_LATER" : app.getStatus()),
    APPLICATION_DATE("applicationDate", "Application date",
            app -> app.getApplicationDate() == null ? "" : app.getApplicationDate().format(Formats.DATE)),
    NEXT_STEP_DATE_TIME("nextStepDateTime", "Next step",
            app -> app.getNextStepDateTime() == null ? "" : app.getNextStepDateTime().format(Formats.DATE_TIME)),
    INTERVIEW_SCHEDULED("interviewScheduled", "Interview scheduled",
            app -> yesNo(app.isInterviewScheduled())),
    INTERVIEW_COUNT("interviewCount", "Interviews", app -> String.valueOf(app.getInterviewCount())),
    RECRUITER_DM_SENT("recruiterDmSent", "Recruiter DM sent",
            app -> yesNo(app.getRecruiterDmSentAt() != null)),
    RECRUITER_DM_SENT_AT("recruiterDmSentAt", "Recruiter DM sent at",
            app -> app.getRecruiterDmSentAt() == null ? "" : app.getRecruiterDmSentAt().format(Formats.DATE_TIME)),
    ARCHIVED("archived", "Archived", app -> yesNo(app.isArchived())),
    NOTE("note", "Notes", JobApplication::getNote),
    CREATED_AT("createdAt", "Created at",
            app -> app.getCreatedAt() == null ? "" : app.getCreatedAt().format(Formats.DATE_TIME)),
    UPDATED_AT("updatedAt", "Updated at",
            app -> app.getUpdatedAt() == null ? "" : app.getUpdatedAt().format(Formats.DATE_TIME));

    /** Nested so the enum constants above can reference the formatters. */
    private static final class Formats {
        private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    }

    private static final Map<String, ExportColumn> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(column -> column.key.toLowerCase(Locale.ROOT), Function.identity()));

    private final String key;
    private final String header;
    private final Function<JobApplication, String> extractor;

    ExportColumn(String key, String header, Function<JobApplication, String> extractor) {
        this.key = key;
        this.header = header;
        this.extractor = extractor;
    }

    public String getKey() {
        return key;
    }

    public String getHeader() {
        return header;
    }

    /** Never returns null, so writers can treat every cell as a plain string. */
    public String valueOf(JobApplication application) {
        String value = extractor.apply(application);
        return value == null ? "" : value;
    }

    public static List<ExportColumn> defaults() {
        return List.of(values());
    }

    /**
     * Resolves requested column keys, preserving the requested order and dropping duplicates.
     * A null or empty request yields {@link #defaults()}; an unknown key is rejected rather than
     * silently ignored, so a typo cannot quietly produce a file missing data.
     */
    public static List<ExportColumn> resolve(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return defaults();
        }
        LinkedHashSet<ExportColumn> resolved = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            ExportColumn column = BY_KEY.get(key.trim().toLowerCase(Locale.ROOT));
            if (column == null) {
                throw new BadRequestException("Unknown export column: '" + key.trim()
                        + "'. Call GET /api/v1/exports/columns for valid keys.");
            }
            resolved.add(column);
        }
        return resolved.isEmpty() ? defaults() : List.copyOf(resolved);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
