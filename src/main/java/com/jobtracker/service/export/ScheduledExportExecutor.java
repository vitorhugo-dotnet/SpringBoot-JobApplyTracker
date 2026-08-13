package com.jobtracker.service.export;

import com.jobtracker.config.ExportProperties;
import com.jobtracker.dto.export.ExportFilters;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportTrigger;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs scheduled exports.
 *
 * <p>A polling loop looks for schedules whose next run is due and claims each one with a
 * conditional UPDATE. The claim is the distributed lock: with several application instances (or a
 * concurrent {@code run-now}) exactly one caller wins, so a schedule never runs twice at the same
 * time. A lock older than the configured timeout is considered stale and may be taken over, so a
 * crashed instance does not freeze a schedule forever.
 *
 * <p>All scheduling timestamps are UTC.
 */
@Component
public class ScheduledExportExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledExportExecutor.class);

    private final ExportScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ApplicationExportService applicationExportService;
    private final ExportExecutionService executionService;
    private final ExportConfigCodec configCodec;
    private final ExportProperties properties;
    private final Map<ExportDestinationType, ExportDestination> destinations;

    public ScheduledExportExecutor(ExportScheduleRepository scheduleRepository,
                                   UserRepository userRepository,
                                   ApplicationExportService applicationExportService,
                                   ExportExecutionService executionService,
                                   ExportConfigCodec configCodec,
                                   ExportProperties properties,
                                   List<ExportDestination> destinations) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.applicationExportService = applicationExportService;
        this.executionService = executionService;
        this.configCodec = configCodec;
        this.properties = properties;
        this.destinations = destinations.stream()
                .collect(Collectors.toUnmodifiableMap(ExportDestination::type, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${app.export.scheduler-interval-ms:60000}",
            initialDelayString = "${app.export.scheduler-initial-delay-ms:30000}")
    public void runDueSchedules() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<UUID> dueScheduleIds = scheduleRepository.findDueScheduleIds(now, staleBefore(now));
        if (dueScheduleIds.isEmpty()) {
            return;
        }

        log.info("event=EXPORT_SCHEDULER_TICK dueSchedules={}", dueScheduleIds.size());
        for (UUID scheduleId : dueScheduleIds) {
            if (!claim(scheduleId)) {
                // Another instance (or a run-now) got there first.
                continue;
            }
            Optional<ScheduleSnapshot> snapshot = loadSnapshot(scheduleId);
            if (snapshot.isEmpty()) {
                releaseLock(scheduleId, null);
                continue;
            }
            ExportExecution execution = executionService.createPending(
                    snapshot.get().user(), snapshot.get().entity(), ExportTrigger.SCHEDULED,
                    snapshot.get().format(), snapshot.get().destination());
            execute(snapshot.get(), execution.getId());
        }
    }

    /**
     * Claims the execution lock.
     *
     * @return true when this caller may run the schedule, false when someone else already is
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID scheduleId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return scheduleRepository.claimForExecution(scheduleId, now, staleBefore(now)) > 0;
    }

    /** Runs a schedule whose lock this caller already holds, off the request thread. */
    @Async
    public void executeClaimedAsync(UUID scheduleId, UUID executionId) {
        executeClaimed(scheduleId, executionId);
    }

    /** Same work as {@link #executeClaimedAsync}, on the calling thread. */
    public void executeClaimed(UUID scheduleId, UUID executionId) {
        loadSnapshot(scheduleId).ifPresentOrElse(
                snapshot -> execute(snapshot, executionId),
                () -> releaseLock(scheduleId, null));
    }

    private void execute(ScheduleSnapshot snapshot, UUID executionId) {
        executionService.markRunning(executionId);
        LocalDateTime nextRunAt = ExportScheduleCalculator.nextRunAtUtc(snapshot.entity(), Instant.now());

        try {
            ExportDestination destination = destinations.get(snapshot.destination());
            if (destination == null) {
                throw new IllegalStateException("No destination configured for " + snapshot.destination());
            }

            ExportFile file = applicationExportService
                    .export(snapshot.user().getId(), snapshot.format(), snapshot.filters(), snapshot.columns())
                    .withFileName(applicationExportService.buildFileName(
                            snapshot.format(), LocalDateTime.now(), true));

            ExportDestination.StoredExportFile stored = destination.store(snapshot.user(), file);
            executionService.markSuccess(executionId, file, stored);
            log.info("event=EXPORT_SCHEDULE_SUCCEEDED scheduleId={} executionId={} records={} truncated={}",
                    snapshot.id(), executionId, file.recordCount(), file.truncated());
        } catch (Exception e) {
            // Only the sanitized message reaches the history; the stack trace stays in the logs and
            // the log line carries identifiers only — never exported content.
            executionService.markFailed(executionId, e);
            log.error("event=EXPORT_SCHEDULE_FAILED scheduleId={} executionId={} error={}",
                    snapshot.id(), executionId, e.getClass().getSimpleName(), e);
        } finally {
            // The next run is always scheduled, including after a failure, so one bad night does
            // not silently disable a backup.
            releaseLock(snapshot.id(), nextRunAt);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLock(UUID scheduleId, LocalDateTime nextRunAt) {
        scheduleRepository.releaseLock(scheduleId, LocalDateTime.now(ZoneOffset.UTC), nextRunAt);
    }

    /**
     * Loads everything the run needs while a transaction is open, so the background work never
     * touches a lazy association.
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleSnapshot> loadSnapshot(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).flatMap(schedule ->
                userRepository.findById(schedule.getUser().getId()).map(user -> new ScheduleSnapshot(
                        schedule.getId(),
                        schedule,
                        user,
                        schedule.getFormat(),
                        schedule.getDestination(),
                        configCodec.readFilters(schedule.getFiltersJson()),
                        configCodec.readColumns(schedule.getColumnsJson()))));
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minusMinutes(properties.getLockTimeoutMinutes());
    }

    /** Everything one execution needs, resolved up front. */
    public record ScheduleSnapshot(
            UUID id,
            ExportSchedule entity,
            User user,
            ExportFormat format,
            ExportDestinationType destination,
            ExportFilters filters,
            List<String> columns
    ) {}
}
