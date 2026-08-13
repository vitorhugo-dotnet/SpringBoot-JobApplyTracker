package com.jobtracker.service;

import com.jobtracker.dto.application.*;
import com.jobtracker.entity.ApplicationStatusEntity;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.ApplicationMapper;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ApplicationSpecifications;
import com.jobtracker.repository.ApplicationStatusRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final String TO_SEND_LATER_STATUS = ApplicationSpecifications.TO_SEND_LATER_STATUS;

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
    public ApplicationResponse patch(UUID id, ApplicationPatchRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        String previousStatus = app.getStatus();
        boolean previousInterviewScheduled = app.isInterviewScheduled();
        String previousNote = app.getNote();
        applyPatchToEntity(request, app);
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

    @Transactional
    public ApplicationResponse restore(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setArchived(false);
        app.setArchivedAt(null);
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getAll(ApplicationFilter filter, int page, int size, String sort) {
        UUID userId = securityUtils.getCurrentUserId();

        Sort sortObj = buildSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Specification<JobApplication> spec = ApplicationSpecifications.forFilter(userId, filter);

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

    private void applyPatchToEntity(ApplicationPatchRequest request, JobApplication app) {
        if (request.hasVacancyName()) {
            app.setVacancyName(normalizeOptionalText(request.getVacancyName()));
        }
        if (request.hasRecruiterName()) {
            app.setRecruiterName(request.getRecruiterName());
        }
        if (request.hasOrganization()) {
            app.setOrganization(request.getOrganization());
        }
        if (request.hasVacancyLink()) {
            app.setVacancyLink(request.getVacancyLink());
        }
        if (request.hasRhAcceptedConnection()) {
            app.setRhAcceptedConnection(request.getRhAcceptedConnection());
        }
        if (request.hasInterviewScheduled()) {
            app.setInterviewScheduled(request.getInterviewScheduled());
        }
        if (request.hasNextStepDateTime()) {
            app.setNextStepDateTime(request.getNextStepDateTime());
        }
        if (request.hasRecruiterDmReminderEnabled()) {
            app.setRecruiterDmReminderEnabled(request.getRecruiterDmReminderEnabled());
        }
        if (request.hasNote()) {
            app.setNote(normalizeOptionalText(request.getNote()));
        }
        if (request.hasPlatform()) {
            app.setPlatform(request.getPlatform());
        }
        if (request.hasInterviewCount()) {
            app.setInterviewCount(request.getInterviewCount());
        }
        // Status and application date are reconciled together because the draft ("to send later")
        // flag derives from both. Leave them untouched when neither is part of the patch.
        if (request.hasStatus() || request.hasApplicationDate()) {
            applyStatusAndDatePatch(request, app);
        }
        if (request.hasArchived()) {
            applyArchivedChange(app, request.getArchived());
        }
    }

    private void applyStatusAndDatePatch(ApplicationPatchRequest request, JobApplication app) {
        String status = request.hasStatus() ? request.getStatus() : app.getStatus();
        LocalDate applicationDate = request.hasApplicationDate()
                ? request.getApplicationDate() : app.getApplicationDate();

        boolean isSendLater = status == null || status.isBlank()
                || TO_SEND_LATER_STATUS.equalsIgnoreCase(status);
        if (!isSendLater && applicationDate == null) {
            throw new BadRequestException(
                    "applicationDate is required when status is provided. Set status to null for 'To Send Later'.");
        }

        if (isSendLater) {
            // Clears the application date and flags the record as a draft.
            applyStatusChange(app, null);
            return;
        }
        app.setToSendLater(false);
        app.setApplicationDate(applicationDate);
        // Only a status the caller actually sent needs validating; the stored one is already valid.
        applyStatusChange(app, request.hasStatus() ? validateStatus(status) : status);
    }

    /** Archiving keeps the timestamp of the first archival so repeated calls are idempotent. */
    private void applyArchivedChange(JobApplication app, boolean archived) {
        if (!archived) {
            app.setArchived(false);
            app.setArchivedAt(null);
            return;
        }
        if (!app.isArchived()) {
            app.setArchived(true);
            app.setArchivedAt(LocalDateTime.now());
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

}
