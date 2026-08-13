package com.jobtracker.service.export;

import com.jobtracker.config.ExportProperties;
import com.jobtracker.dto.export.ExportExecutionResponse;
import com.jobtracker.dto.export.ExportScheduleRequest;
import com.jobtracker.dto.export.ExportScheduleResponse;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportFrequency;
import com.jobtracker.entity.enums.ExportTrigger;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ConflictException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ExportExecutionRepository;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD and lifecycle for recurring export configurations.
 *
 * <p>Only validated domain recurrences are persisted — never a user-supplied cron expression — and
 * every lookup is scoped to the authenticated user.
 */
@Service
public class ExportScheduleService {

    private final ExportScheduleRepository scheduleRepository;
    private final ExportExecutionRepository executionRepository;
    private final ScheduledExportExecutor executor;
    private final ExportExecutionService executionService;
    private final ExportProperties properties;
    private final SecurityUtils securityUtils;
    private final ExportConfigCodec configCodec;
    private final Set<ExportDestinationType> supportedDestinations;

    public ExportScheduleService(ExportScheduleRepository scheduleRepository,
                                 ExportExecutionRepository executionRepository,
                                 ScheduledExportExecutor executor,
                                 ExportExecutionService executionService,
                                 ExportProperties properties,
                                 SecurityUtils securityUtils,
                                 ExportConfigCodec configCodec,
                                 List<ExportDestination> destinations) {
        this.scheduleRepository = scheduleRepository;
        this.executionRepository = executionRepository;
        this.executor = executor;
        this.executionService = executionService;
        this.properties = properties;
        this.securityUtils = securityUtils;
        this.configCodec = configCodec;
        this.supportedDestinations = destinations.stream()
                .map(ExportDestination::type)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<ExportScheduleResponse> list() {
        return scheduleRepository.findAllByUserIdOrderByCreatedAtAsc(securityUtils.getCurrentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExportScheduleResponse get(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Transactional
    public ExportScheduleResponse create(ExportScheduleRequest request) {
        var user = securityUtils.getCurrentUser();
        long existing = scheduleRepository.countByUserId(user.getId());
        if (existing >= properties.getMaxSchedulesPerUser()) {
            throw new BadRequestException("You can have at most " + properties.getMaxSchedulesPerUser()
                    + " export schedules. Delete one before creating another.");
        }

        ExportSchedule schedule = new ExportSchedule();
        schedule.setUser(user);
        applyRequest(schedule, request);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public ExportScheduleResponse update(UUID id, ExportScheduleRequest request) {
        ExportSchedule schedule = loadOwned(id);
        applyRequest(schedule, request);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public ExportScheduleResponse setEnabled(UUID id, boolean enabled) {
        ExportSchedule schedule = loadOwned(id);
        schedule.setEnabled(enabled);
        schedule.setNextRunAt(enabled ? ExportScheduleCalculator.nextRunAtUtc(schedule, Instant.now()) : null);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(UUID id) {
        ExportSchedule schedule = loadOwned(id);
        executionRepository.detachFromSchedule(schedule.getId());
        scheduleRepository.delete(schedule);
    }

    /**
     * Starts an on-demand run of an existing schedule.
     *
     * <p>The execution lock is claimed synchronously, so a second call while a run is in flight
     * fails fast with a conflict instead of producing a duplicate file. The export itself runs
     * asynchronously; the returned execution starts as {@code PENDING} and the history endpoints
     * report its outcome.
     */
    @Transactional
    public ExportExecutionResponse runNow(UUID id) {
        ExportSchedule schedule = loadOwned(id);
        if (!executor.claim(schedule.getId())) {
            throw new ConflictException("An execution for this schedule is already running");
        }

        ExportExecution execution = executionService.createPending(
                schedule.getUser(), schedule, ExportTrigger.RUN_NOW, schedule.getFormat(), schedule.getDestination());
        executor.executeClaimedAsync(schedule.getId(), execution.getId());
        return ExportExecutionService.toResponse(execution);
    }

    private ExportSchedule loadOwned(UUID id) {
        return scheduleRepository.findByIdAndUserId(id, securityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Export schedule not found with id: " + id));
    }

    private void applyRequest(ExportSchedule schedule, ExportScheduleRequest request) {
        validateRecurrence(request);
        validateDestination(request.destination());

        schedule.setName(request.name().trim());
        schedule.setFormat(request.format());
        schedule.setFrequency(request.frequency());
        schedule.setTimeOfDay(request.time().withSecond(0).withNano(0));
        schedule.setDayOfWeek(request.frequency() == ExportFrequency.WEEKLY ? request.dayOfWeek() : null);
        schedule.setDayOfMonth(request.frequency() == ExportFrequency.MONTHLY ? request.dayOfMonth() : null);
        schedule.setTimezone(resolveTimezone(request.timezone()));
        schedule.setDestination(request.destination());
        schedule.setEnabled(request.enabled() == null || request.enabled());
        schedule.setFiltersJson(configCodec.write(request.filters()));
        // Validates the keys eagerly so a typo fails at configuration time, not at 3 a.m.
        schedule.setColumnsJson(configCodec.write(ExportColumn.resolve(request.columns()).stream()
                .map(ExportColumn::getKey)
                .toList()));
        schedule.setNextRunAt(schedule.isEnabled()
                ? ExportScheduleCalculator.nextRunAtUtc(schedule, Instant.now())
                : null);
    }

    private void validateRecurrence(ExportScheduleRequest request) {
        if (request.frequency() == ExportFrequency.WEEKLY && request.dayOfWeek() == null) {
            throw new BadRequestException("dayOfWeek is required for WEEKLY schedules (1 = Monday … 7 = Sunday)");
        }
        if (request.frequency() == ExportFrequency.MONTHLY && request.dayOfMonth() == null) {
            throw new BadRequestException("dayOfMonth is required for MONTHLY schedules (1–28)");
        }
    }

    private void validateDestination(ExportDestinationType destination) {
        if (!supportedDestinations.contains(destination)) {
            throw new BadRequestException("Destination not supported yet: " + destination
                    + ". Supported destinations: " + supportedDestinations);
        }
    }

    private String resolveTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return properties.getDefaultTimezone();
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Invalid timezone: " + timezone);
        }
    }

    private ExportScheduleResponse toResponse(ExportSchedule schedule) {
        return new ExportScheduleResponse(
                schedule.getId(),
                schedule.getName(),
                schedule.getFormat(),
                schedule.getFrequency(),
                schedule.getTimeOfDay(),
                schedule.getDayOfWeek(),
                schedule.getDayOfMonth(),
                schedule.getTimezone(),
                schedule.isEnabled(),
                schedule.getDestination(),
                configCodec.readFilters(schedule.getFiltersJson()),
                configCodec.readColumns(schedule.getColumnsJson()),
                schedule.getNextRunAt(),
                schedule.getLastRunAt(),
                schedule.isRunning(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
