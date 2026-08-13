package com.jobtracker.repository;

import com.jobtracker.dto.application.ApplicationFilter;
import com.jobtracker.dto.export.ExportFilters;
import com.jobtracker.entity.JobApplication;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for the job-application query predicates.
 *
 * <p>Both the listing endpoint and the export module build their queries here, so an export can
 * never see rows the listing would hide — every specification is anchored on the owning user.
 */
public final class ApplicationSpecifications {

    /** Pseudo-status the API uses for drafts, persisted as a {@code NULL} status. */
    public static final String TO_SEND_LATER_STATUS = "TO_SEND_LATER";

    private ApplicationSpecifications() {
    }

    /** Predicates used by {@code GET /api/v1/applications}. Archived defaults to {@code false}. */
    public static Specification<JobApplication> forFilter(UUID userId, ApplicationFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.equal(root.get("archived"),
                    filter.archived() != null ? filter.archived() : Boolean.FALSE));

            addSearch(predicates, cb, root, filter.search());

            if (StringUtils.hasText(filter.status())) {
                predicates.add(statusPredicate(cb, root, filter.status()));
            }

            addLike(predicates, cb, root, "vacancyName", filter.vacancyName());
            addLike(predicates, cb, root, "recruiterName", filter.recruiterName());
            addLike(predicates, cb, root, "organization", filter.organization());
            addLike(predicates, cb, root, "note", filter.note());
            addLike(predicates, cb, root, "platform", filter.platform());

            addApplicationDateRange(predicates, cb, root,
                    filter.applicationDateFrom(), filter.applicationDateTo());

            if (filter.nextStepDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("nextStepDateTime"),
                        filter.nextStepDateFrom().atStartOfDay()));
            }
            if (filter.nextStepDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("nextStepDateTime"),
                        filter.nextStepDateTo().atTime(LocalTime.MAX)));
            }

            if (filter.interviewScheduled() != null) {
                predicates.add(cb.equal(root.get("interviewScheduled"), filter.interviewScheduled()));
            }
            if (filter.recruiterDmReminderEnabled() != null) {
                predicates.add(cb.equal(root.get("recruiterDmReminderEnabled"), filter.recruiterDmReminderEnabled()));
            }
            if (filter.rhAcceptedConnection() != null) {
                predicates.add(cb.equal(root.get("rhAcceptedConnection"), filter.rhAcceptedConnection()));
            }
            if (filter.toSendLater() != null) {
                predicates.add(cb.equal(root.get("toSendLater"), filter.toSendLater()));
            }

            if (filter.interviewCountMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("interviewCount"), filter.interviewCountMin()));
            }
            if (filter.interviewCountMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("interviewCount"), filter.interviewCountMax()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Predicates used by the export module. The only intentional difference from
     * {@link #forFilter} is the archive default: an export with {@code archived = null} covers both
     * active and archived applications, because an export is a backup of everything the user owns.
     */
    public static Specification<JobApplication> forExport(UUID userId, ExportFilters filters) {
        ExportFilters safeFilters = filters != null ? filters : ExportFilters.empty();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (safeFilters.archived() != null) {
                predicates.add(cb.equal(root.get("archived"), safeFilters.archived()));
            }

            addSearch(predicates, cb, root, safeFilters.search());

            List<String> statuses = safeFilters.status() == null ? List.of() : safeFilters.status().stream()
                    .filter(StringUtils::hasText)
                    .toList();
            if (!statuses.isEmpty()) {
                Predicate[] statusPredicates = statuses.stream()
                        .map(status -> statusPredicate(cb, root, status))
                        .toArray(Predicate[]::new);
                predicates.add(cb.or(statusPredicates));
            }

            addLike(predicates, cb, root, "organization", safeFilters.organization());
            addLike(predicates, cb, root, "platform", safeFilters.platform());

            addApplicationDateRange(predicates, cb, root,
                    safeFilters.applicationDateFrom(), safeFilters.applicationDateTo());

            if (safeFilters.interviewScheduled() != null) {
                predicates.add(cb.equal(root.get("interviewScheduled"), safeFilters.interviewScheduled()));
            }
            if (safeFilters.toSendLater() != null) {
                predicates.add(cb.equal(root.get("toSendLater"), safeFilters.toSendLater()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate statusPredicate(CriteriaBuilder cb, Root<JobApplication> root, String status) {
        return TO_SEND_LATER_STATUS.equalsIgnoreCase(status)
                ? cb.isNull(root.get("status"))
                : cb.equal(root.get("status"), status);
    }

    /** Global free-text search: match the query against every meaningful text column. */
    private static void addSearch(List<Predicate> predicates, CriteriaBuilder cb,
                                  Root<JobApplication> root, String search) {
        if (!StringUtils.hasText(search)) {
            return;
        }
        String like = "%" + search.trim().toLowerCase() + "%";
        predicates.add(cb.or(
                cb.like(cb.lower(root.get("vacancyName")), like),
                cb.like(cb.lower(root.get("recruiterName")), like),
                cb.like(cb.lower(root.get("organization")), like),
                cb.like(cb.lower(root.get("note")), like),
                cb.like(cb.lower(root.get("platform")), like),
                cb.like(cb.lower(root.get("status")), like)
        ));
    }

    private static void addApplicationDateRange(List<Predicate> predicates, CriteriaBuilder cb,
                                                Root<JobApplication> root,
                                                java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("applicationDate"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("applicationDate"), to));
        }
    }

    private static void addLike(List<Predicate> predicates, CriteriaBuilder cb,
                                Root<JobApplication> root, String field, String value) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.trim().toLowerCase() + "%"));
        }
    }
}
