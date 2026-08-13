package com.jobtracker.entity.enums;

/**
 * Recurrence supported by scheduled exports.
 *
 * <p>The user never supplies a cron expression: a validated domain configuration
 * (frequency + time + day) is persisted and the next execution instant is computed internally.
 */
public enum ExportFrequency {

    /** Every day at the configured time. */
    DAILY,

    /** Every week on the configured ISO day-of-week (1 = Monday … 7 = Sunday). */
    WEEKLY,

    /** Every month on the configured day-of-month (1–28). */
    MONTHLY
}
