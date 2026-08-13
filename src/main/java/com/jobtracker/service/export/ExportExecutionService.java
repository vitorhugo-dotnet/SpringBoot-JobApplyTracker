package com.jobtracker.service.export;

import com.jobtracker.dto.export.ExportExecutionPageResponse;
import com.jobtracker.dto.export.ExportExecutionResponse;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportExecutionStatus;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportTrigger;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ExportExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes and reads the export audit trail.
 *
 * <p>Every attempt — manual download, scheduled run or run-now — leaves a row here. Failure
 * messages are sanitized before they are stored: control characters are stripped, the text is
 * truncated and long opaque strings (which is what tokens look like) are masked, so the history
 * can be shown to the user without leaking anything sensitive.
 */
@Service
public class ExportExecutionService {

    private static final int MAX_ERROR_LENGTH = 300;
    private static final int MAX_PAGE_SIZE = 100;

    /** Long unbroken token-ish strings are masked rather than stored verbatim. */
    private static final Pattern TOKEN_LIKE = Pattern.compile("[A-Za-z0-9._\\-]{40,}");

    private final ExportExecutionRepository executionRepository;

    public ExportExecutionService(ExportExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExportExecution createPending(User user, ExportSchedule schedule, ExportTrigger trigger,
                                         ExportFormat format, ExportDestinationType destination) {
        ExportExecution execution = new ExportExecution();
        execution.setUser(user);
        execution.setSchedule(schedule);
        execution.setScheduleName(schedule != null ? schedule.getName() : null);
        execution.setTrigger(trigger);
        execution.setFormat(format);
        execution.setDestination(destination);
        execution.setStatus(ExportExecutionStatus.PENDING);
        execution.setStartedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID executionId) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(ExportExecutionStatus.RUNNING);
            executionRepository.save(execution);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(UUID executionId, ExportFile file, ExportDestination.StoredExportFile stored) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(ExportExecutionStatus.SUCCESS);
            execution.setFinishedAt(LocalDateTime.now());
            execution.setRecordCount(file.recordCount());
            execution.setTruncated(file.truncated());
            execution.setFileName(stored != null ? stored.fileName() : file.fileName());
            execution.setFileId(stored != null ? stored.fileId() : null);
            execution.setFileUrl(stored != null ? stored.fileUrl() : null);
            executionRepository.save(execution);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID executionId, Throwable error) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(ExportExecutionStatus.FAILED);
            execution.setFinishedAt(LocalDateTime.now());
            execution.setErrorMessage(sanitizeError(error));
            executionRepository.save(execution);
        });
    }

    /** Records a completed manual download in one shot — it never has a PENDING phase. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordManualSuccess(User user, ExportFile file, LocalDateTime startedAt) {
        ExportExecution execution = new ExportExecution();
        execution.setUser(user);
        execution.setTrigger(ExportTrigger.MANUAL);
        execution.setFormat(file.format());
        execution.setStatus(ExportExecutionStatus.SUCCESS);
        execution.setStartedAt(startedAt);
        execution.setFinishedAt(LocalDateTime.now());
        execution.setRecordCount(file.recordCount());
        execution.setTruncated(file.truncated());
        execution.setFileName(file.fileName());
        executionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordManualFailure(User user, ExportFormat format, LocalDateTime startedAt, Throwable error) {
        ExportExecution execution = new ExportExecution();
        execution.setUser(user);
        execution.setTrigger(ExportTrigger.MANUAL);
        execution.setFormat(format);
        execution.setStatus(ExportExecutionStatus.FAILED);
        execution.setStartedAt(startedAt);
        execution.setFinishedAt(LocalDateTime.now());
        execution.setErrorMessage(sanitizeError(error));
        executionRepository.save(execution);
    }

    @Transactional(readOnly = true)
    public ExportExecutionPageResponse history(UUID userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<ExportExecution> result = executionRepository.findAllByUserIdOrderByStartedAtDesc(
                userId, PageRequest.of(Math.max(page, 0), safeSize));
        return new ExportExecutionPageResponse(
                result.getContent().stream().map(ExportExecutionService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExportExecutionResponse get(UUID userId, UUID executionId) {
        return executionRepository.findByIdAndUserId(executionId, userId)
                .map(ExportExecutionService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Export execution not found with id: " + executionId));
    }

    public static ExportExecutionResponse toResponse(ExportExecution execution) {
        return new ExportExecutionResponse(
                execution.getId(),
                execution.getSchedule() != null ? execution.getSchedule().getId() : null,
                execution.getScheduleName(),
                execution.getTrigger(),
                execution.getFormat(),
                execution.getDestination(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getRecordCount(),
                execution.isTruncated(),
                execution.getFileName(),
                execution.getFileUrl(),
                execution.getErrorMessage());
    }

    static String sanitizeError(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("[\\p{Cntrl}]+", " ").trim();
        String masked = TOKEN_LIKE.matcher(singleLine).replaceAll("***");
        return masked.length() <= MAX_ERROR_LENGTH ? masked : masked.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
