package com.jobtracker.repository;

import com.jobtracker.entity.ExportExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExportExecutionRepository extends JpaRepository<ExportExecution, UUID> {

    Optional<ExportExecution> findByIdAndUserId(UUID id, UUID userId);

    Page<ExportExecution> findAllByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    /**
     * Detaches history rows from a schedule that is about to be deleted. The run stays in the
     * history (with the schedule name it was created with) instead of disappearing with it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE ExportExecution e SET e.schedule = null WHERE e.schedule.id = :scheduleId")
    int detachFromSchedule(@Param("scheduleId") UUID scheduleId);
}
