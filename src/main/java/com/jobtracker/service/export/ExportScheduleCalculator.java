package com.jobtracker.service.export;

import com.jobtracker.entity.ExportSchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Turns a validated recurrence (frequency + local time + day) into the next execution instant.
 *
 * <p>The instant is computed in the schedule's own timezone and returned in <strong>UTC</strong>,
 * which is how it is persisted and compared. Using {@link ZonedDateTime} means daylight-saving
 * transitions are handled by the JDK: a local time that does not exist on a spring-forward day is
 * moved to the next valid instant instead of being skipped.
 */
public final class ExportScheduleCalculator {

    private ExportScheduleCalculator() {
    }

    /**
     * @param from the moment to compute from (typically "now")
     * @return the first instant strictly after {@code from} that matches the recurrence, in UTC
     */
    public static LocalDateTime nextRunAtUtc(ExportSchedule schedule, Instant from) {
        ZoneId zone = ZoneId.of(schedule.getTimezone());
        LocalDate startDate = from.atZone(zone).toLocalDate();

        ZonedDateTime candidate = candidateFor(schedule, startDate, zone);
        int guard = 0;
        while (!candidate.toInstant().isAfter(from)) {
            // Walking one day forward is enough: the weekly/monthly branches snap the date back onto
            // the configured day, so this converges in a couple of iterations at most.
            startDate = startDate.plusDays(1);
            candidate = candidateFor(schedule, startDate, zone);
            if (++guard > 400) {
                // Unreachable for the supported frequencies; refuse to spin rather than hang.
                throw new IllegalStateException("Could not compute the next run for schedule " + schedule.getId());
            }
        }
        return LocalDateTime.ofInstant(candidate.toInstant(), ZoneOffset.UTC);
    }

    private static ZonedDateTime candidateFor(ExportSchedule schedule, LocalDate date, ZoneId zone) {
        LocalDate target = switch (schedule.getFrequency()) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(
                    java.time.DayOfWeek.of(schedule.getDayOfWeek())));
            case MONTHLY -> {
                LocalDate sameMonth = date.withDayOfMonth(schedule.getDayOfMonth());
                yield sameMonth.isBefore(date) ? sameMonth.plusMonths(1) : sameMonth;
            }
        };
        return target.atTime(schedule.getTimeOfDay()).atZone(zone);
    }
}
