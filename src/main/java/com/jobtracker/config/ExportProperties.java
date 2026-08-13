package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** Tunables for the application export module ({@code app.export.*}). */
@Component
public class ExportProperties {

    private final int maxRecords;
    private final int pageSize;
    private final String defaultTimezone;
    private final int maxSchedulesPerUser;
    private final boolean schedulerEnabled;
    private final int lockTimeoutMinutes;
    private final String driveFolderName;

    public ExportProperties(
            @Value("${app.export.max-records:10000}") int maxRecords,
            @Value("${app.export.page-size:500}") int pageSize,
            @Value("${app.export.default-timezone:America/Sao_Paulo}") String defaultTimezone,
            @Value("${app.export.max-schedules-per-user:10}") int maxSchedulesPerUser,
            @Value("${app.export.scheduler-enabled:true}") boolean schedulerEnabled,
            @Value("${app.export.lock-timeout-minutes:30}") int lockTimeoutMinutes,
            @Value("${app.export.drive-folder-name:Applywell Exports}") String driveFolderName) {
        this.maxRecords = maxRecords;
        this.pageSize = pageSize;
        this.defaultTimezone = defaultTimezone;
        this.maxSchedulesPerUser = maxSchedulesPerUser;
        this.schedulerEnabled = schedulerEnabled;
        this.lockTimeoutMinutes = lockTimeoutMinutes;
        this.driveFolderName = driveFolderName;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** Timezone used when a schedule does not declare one. */
    public ZoneId getDefaultZone() {
        return ZoneId.of(defaultTimezone);
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public int getMaxSchedulesPerUser() {
        return maxSchedulesPerUser;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    /** How long a claimed execution lock stays valid before another instance may take it over. */
    public int getLockTimeoutMinutes() {
        return lockTimeoutMinutes;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }
}
