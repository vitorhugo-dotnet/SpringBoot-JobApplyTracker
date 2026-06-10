package com.jobtracker.service;

import com.jobtracker.dto.application.*;
import com.jobtracker.entity.ApplicationStatusEntity;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.ApplicationMapper;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ApplicationStatusRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final String TO_SEND_LATER_STATUS = "TO_SEND_LATER";

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "applicationDate", "status",
            "vacancyName", "recruiterName", "nextStepDateTime"
    );

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final InterviewEventRepository interviewEventRepository;
    private final ApplicationMapper applicationMapper;
    private final GamificationService gamificationService;
    private final InterviewMetricsService interviewMetricsService;
    private final SecurityUtils securityUtils;
    private final Tracer tracer;

    public ApplicationService(ApplicationRepository applicationRepository,
                              ApplicationStatusRepository applicationStatusRepository,
                              InterviewEventRepository interviewEventRepository,
                              ApplicationMapper applicationMapper,
                              GamificationService gamificationService,
                              InterviewMetricsService interviewMetricsService,
                              SecurityUtils securityUtils,
                              Tracer tracer) {
        this.applicationRepository = applicationRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.interviewEventRepository = interviewEventRepository;
        this.applicationMapper = applicationMapper;
        this.gamificationService = gamificationService;
        this.interviewMetricsService = interviewMetricsService;
        this.securityUtils = securityUtils;
        this.tracer = tracer;
    }

    @Transactional(readOnly = true)
    public List<String> listStatuses() {
        return applicationStatusRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(ApplicationStatusEntity::getName)
                .toList();
    }

    @Transactional
    public ApplicationResponse create(ApplicationRequest request) {
        Span span = tracer.nextSpan().name("create-application").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            JobApplication app = new JobApplication();
            mapRequestToEntity(request, app);
            app.setUser(securityUtils.getCurrentUser());
            JobApplication saved = applicationRepository.save(app);
            interviewMetricsService.recordStatusTransition(saved, null, saved.getStatus());
            gamificationService.onApplicationCreated(saved);
            return applicationMapper.toResponse(saved);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return applicationMapper.toResponse(app);
    }

    @Transactional
    public ApplicationResponse update(UUID id, ApplicationRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        String previousStatus = app.getStatus();
        boolean previousInterviewScheduled = app.isInterviewScheduled();
        String previousNote = app.getNote();
        mapRequestToEntity(request, app);
        JobApplication saved = applicationRepository.save(app);
        interviewMetricsService.recordStatusTransition(saved, previousStatus, saved.getStatus());
        gamificationService.onApplicationUpdated(saved, previousStatus, previousInterviewScheduled, previousNote);
        return applicationMapper.toResponse(saved);
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID id, UpdateStatusRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        String previousStatus = app.getStatus();
        applyStatusChange(app, validateStatus(request.status()));
        JobApplication saved = applicationRepository.save(app);
        interviewMetricsService.recordStatusTransition(saved, previousStatus, saved.getStatus());
        gamificationService.onApplicationStatusUpdated(saved, previousStatus);
        return applicationMapper.toResponse(saved);
    }

    @Transactional
    public ApplicationResponse updateReminder(UUID id, UpdateReminderRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setRecruiterDmReminderEnabled(request.recruiterDmReminderEnabled());
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationResponse markDmSent(UUID id, MarkDmSentRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        boolean dmAlreadySent = app.getRecruiterDmSentAt() != null;
        if (!dmAlreadySent) {
            app.setRecruiterDmSentAt(LocalDateTime.now());
        }
        JobApplication saved = applicationRepository.save(app);
        gamificationService.onRecruiterDmSent(saved, !dmAlreadySent);
        return applicationMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        interviewEventRepository.deleteByApplication_Id(app.getId());
        applicationRepository.delete(app);
    }

    @Transactional
    public ApplicationResponse archive(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setArchived(true);
        app.setArchivedAt(LocalDateTime.now());
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getAll(ApplicationFilter filter, int page, int size, String sort) {
        UUID userId = securityUtils.getCurrentUserId();

        Sort sortObj = buildSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Specification<JobApplication> spec = buildSpecification(userId, filter);

        Page<JobApplication> resultPage = applicationRepository.findAll(spec, pageable);

        List<ApplicationResponse> content = resultPage.getContent()
                .stream().map(applicationMapper::toResponse).toList();

        return new ApplicationPageResponse(
                content,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getUpcoming() {
        UUID userId = securityUtils.getCurrentUserId();
        LocalDateTime reminderThreshold = LocalDateTime.now().minusHours(6);
        return applicationRepository.findUpcomingByUserId(userId, reminderThreshold)
                .stream().map(applicationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getOverdue() {
        UUID userId = securityUtils.getCurrentUserId();
        LocalDateTime reminderThreshold = LocalDateTime.now().minusHours(6);
        LocalDateTime expireThreshold = LocalDateTime.now().minusDays(2);
        return applicationRepository.findOverdueByUserId(userId, reminderThreshold, expireThreshold)
                .stream().map(applicationMapper::toResponse).toList();
    }

    private void mapRequestToEntity(ApplicationRequest request, JobApplication app) {
        boolean isSendLater = request.status() == null || request.status().isBlank()
                || TO_SEND_LATER_STATUS.equalsIgnoreCase(request.status());
        if (!isSendLater && request.applicationDate() == null) {
            throw new BadRequestException(
                    "applicationDate is required when status is provided. Set status to null for 'To Send Later'.");
        }

        app.setVacancyName(normalizeOptionalText(request.vacancyName()));
        app.setRecruiterName(request.recruiterName());
        app.setOrganization(request.organization());
        app.setVacancyLink(request.vacancyLink());
        app.setToSendLater(isSendLater);
        app.setApplicationDate(isSendLater ? null : request.applicationDate());
        app.setRhAcceptedConnection(Boolean.TRUE.equals(request.rhAcceptedConnection()));
        app.setInterviewScheduled(Boolean.TRUE.equals(request.interviewScheduled()));
        app.setNextStepDateTime(request.nextStepDateTime());
        applyStatusChange(app, isSendLater ? null : validateStatus(request.status()));
        app.setRecruiterDmReminderEnabled(Boolean.TRUE.equals(request.recruiterDmReminderEnabled()));
        app.setNote(normalizeOptionalText(request.note()));
        app.setPlatform(request.platform());
        if (request.interviewCount() != null) {
            app.setInterviewCount(request.interviewCount());
        }
    }

    private void applyStatusChange(JobApplication app, String newStatus) {
        String currentStatus = app.getStatus();
        if (isRejectedOrGhosting(newStatus) && !newStatus.equals(currentStatus)) {
            app.setPreviousStatus(currentStatus);
        }
        if (!isRejectedOrGhosting(newStatus)) {
            app.setPreviousStatus(null);
        }
        app.setStatus(newStatus);
        if (newStatus == null) {
            app.setApplicationDate(null);
            app.setToSendLater(true);
        }
    }

    private static boolean isRejectedOrGhosting(String status) {
        if (status == null) return false;
        return "REJEITADO".equals(status) || "Rejected".equals(status)
                || "GHOSTING".equals(status) || "Ghosting".equals(status);
    }

    private String validateStatus(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return null;
        }
        if (!applicationStatusRepository.existsByName(statusName)) {
            throw new BadRequestException(
                    "Invalid status value: '" + statusName
                    + "'. Call GET /api/v1/applications/statuses for valid options.");
        }
        return statusName;
    }

    private Sort buildSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BadRequestException("Invalid sort field: " + field +
                    ". Allowed fields: " + ALLOWED_SORT_FIELDS);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Specification<JobApplication> buildSpecification(UUID userId, ApplicationFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.equal(root.get("archived"),
                    filter.archived() != null ? filter.archived() : Boolean.FALSE));

            // Global free-text search: match the query against every meaningful text column.
            if (StringUtils.hasText(filter.search())) {
                String like = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("vacancyName")), like),
                        cb.like(cb.lower(root.get("recruiterName")), like),
                        cb.like(cb.lower(root.get("organization")), like),
                        cb.like(cb.lower(root.get("note")), like),
                        cb.like(cb.lower(root.get("platform")), like),
                        cb.like(cb.lower(root.get("status")), like)
                ));
            }

            if (StringUtils.hasText(filter.status())) {
                if (TO_SEND_LATER_STATUS.equalsIgnoreCase(filter.status())) {
                    predicates.add(cb.isNull(root.get("status")));
                } else {
                    predicates.add(cb.equal(root.get("status"), filter.status()));
                }
            }

            addLike(predicates, cb, root, "vacancyName", filter.vacancyName());
            addLike(predicates, cb, root, "recruiterName", filter.recruiterName());
            addLike(predicates, cb, root, "organization", filter.organization());
            addLike(predicates, cb, root, "note", filter.note());
            addLike(predicates, cb, root, "platform", filter.platform());

            if (filter.applicationDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("applicationDate"), filter.applicationDateFrom()));
            }
            if (filter.applicationDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("applicationDate"), filter.applicationDateTo()));
            }

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

    private static void addLike(List<Predicate> predicates, CriteriaBuilder cb,
                                Root<JobApplication> root, String field, String value) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.trim().toLowerCase() + "%"));
        }
    }
}
