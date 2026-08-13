package com.jobtracker.unit.export;

import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.enums.ExportFrequency;
import com.jobtracker.service.export.ExportScheduleCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExportScheduleCalculatorTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void daily_shouldPickTodayWhenTheTimeHasNotPassedYet() {
        ExportSchedule schedule = daily(LocalTime.of(20, 0), "America/Sao_Paulo");
        Instant now = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(toZone(nextRun)).isEqualTo(ZonedDateTime.of(2026, 7, 16, 20, 0, 0, 0, SAO_PAULO));
    }

    @Test
    void daily_shouldRollToTomorrowWhenTheTimeAlreadyPassed() {
        ExportSchedule schedule = daily(LocalTime.of(20, 0), "America/Sao_Paulo");
        Instant now = ZonedDateTime.of(2026, 7, 16, 20, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(toZone(nextRun)).isEqualTo(ZonedDateTime.of(2026, 7, 17, 20, 0, 0, 0, SAO_PAULO));
    }

    @Test
    void daily_shouldBeStoredInUtc() {
        ExportSchedule schedule = daily(LocalTime.of(20, 0), "America/Sao_Paulo");
        Instant now = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        // 20:00 in São Paulo (UTC-3 in July) is 23:00 UTC.
        assertThat(nextRun).isEqualTo(LocalDateTime.of(2026, 7, 16, 23, 0));
    }

    @Test
    void daily_shouldHonourTheScheduleTimezoneNotTheServerOne() {
        ExportSchedule tokyo = daily(LocalTime.of(8, 0), "Asia/Tokyo");
        Instant now = ZonedDateTime.of(2026, 7, 16, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(tokyo, now);

        // 08:00 in Tokyo (UTC+9) is 23:00 UTC the previous day, already past, so it rolls forward.
        assertThat(nextRun).isEqualTo(LocalDateTime.of(2026, 7, 16, 23, 0));
    }

    @Test
    void weekly_shouldPickTheConfiguredDayOfWeek() {
        ExportSchedule schedule = daily(LocalTime.of(7, 30), "America/Sao_Paulo");
        schedule.setFrequency(ExportFrequency.WEEKLY);
        schedule.setDayOfWeek(1); // Monday
        // 2026-07-16 is a Thursday.
        Instant now = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(toZone(nextRun)).isEqualTo(ZonedDateTime.of(2026, 7, 20, 7, 30, 0, 0, SAO_PAULO));
    }

    @Test
    void monthly_shouldRollToNextMonthWhenTheDayAlreadyPassed() {
        ExportSchedule schedule = daily(LocalTime.of(6, 0), "America/Sao_Paulo");
        schedule.setFrequency(ExportFrequency.MONTHLY);
        schedule.setDayOfMonth(1);
        Instant now = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(toZone(nextRun)).isEqualTo(ZonedDateTime.of(2026, 8, 1, 6, 0, 0, 0, SAO_PAULO));
    }

    @Test
    void monthly_shouldPickTheSameMonthWhenTheDayIsStillAhead() {
        ExportSchedule schedule = daily(LocalTime.of(6, 0), "America/Sao_Paulo");
        schedule.setFrequency(ExportFrequency.MONTHLY);
        schedule.setDayOfMonth(28);
        Instant now = ZonedDateTime.of(2026, 7, 16, 9, 0, 0, 0, SAO_PAULO).toInstant();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(toZone(nextRun)).isEqualTo(ZonedDateTime.of(2026, 7, 28, 6, 0, 0, 0, SAO_PAULO));
    }

    @Test
    void nextRun_shouldAlwaysBeStrictlyInTheFuture() {
        ExportSchedule schedule = daily(LocalTime.of(20, 0), "America/Sao_Paulo");
        Instant now = Instant.now();

        LocalDateTime nextRun = ExportScheduleCalculator.nextRunAtUtc(schedule, now);

        assertThat(nextRun.toInstant(ZoneOffset.UTC)).isAfter(now);
    }

    private static ExportSchedule daily(LocalTime time, String timezone) {
        ExportSchedule schedule = new ExportSchedule();
        schedule.setFrequency(ExportFrequency.DAILY);
        schedule.setTimeOfDay(time);
        schedule.setTimezone(timezone);
        return schedule;
    }

    private static ZonedDateTime toZone(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(SAO_PAULO);
    }
}
