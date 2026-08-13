package com.jobtracker.entity.enums;

/** What started an export attempt. */
public enum ExportTrigger {

    /** Direct download through {@code POST /api/v1/exports/applications}. */
    MANUAL,

    /** Fired by the scheduler when a schedule became due. */
    SCHEDULED,

    /** Fired on demand through {@code POST /api/v1/export-schedules/{id}/run-now}. */
    RUN_NOW
}
