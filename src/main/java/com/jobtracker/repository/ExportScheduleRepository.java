package com.jobtracker.repository;

import com.jobtracker.entity.ExportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExportScheduleRepository extends JpaRepository<ExportSchedule, UUID> {

    Optional<ExportSchedule> findByIdAndUserId(UUID id, UUID userId);

    List<ExportSchedule> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    /**
     * IDs of enabled schedules that are due (all timestamps in UTC). Schedules whose lock is held
     * are skipped unless the lock is stale, which lets a crashed instance's work be picked up.
     */
    @Query("SELECT s.id FROM ExportSchedule s WHERE s.enabled = true AND s.nextRunAt IS NOT NULL "
            + "AND s.nextRunAt <= :now AND (s.running = false OR s.runningSince IS NULL OR s.runningSince < :staleBefore) "
            + "ORDER BY s.nextRunAt ASC")
    List<UUID> findDueScheduleIds(@Param("now") LocalDateTime now,
                                  @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * Claims the execution lock for a schedule. Returns 1 when this caller won the race and 0 when
     * another execution (possibly on another instance) already holds it — which is what prevents
     * two concurrent runs of the same schedule.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE ExportSchedule s SET s.running = true, s.runningSince = :now WHERE s.id = :id "
            + "AND (s.running = false OR s.runningSince IS NULL OR s.runningSince < :staleBefore)")
    int claimForExecution(@Param("id") UUID id,
                          @Param("now") LocalDateTime now,
                          @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE ExportSchedule s SET s.running = false, s.runningSince = null, "
            + "s.lastRunAt = :lastRunAt, s.nextRunAt = :nextRunAt WHERE s.id = :id")
    int releaseLock(@Param("id") UUID id,
                    @Param("lastRunAt") LocalDateTime lastRunAt,
                    @Param("nextRunAt") LocalDateTime nextRunAt);
}
