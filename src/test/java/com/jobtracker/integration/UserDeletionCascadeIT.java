package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportExecutionStatus;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportFrequency;
import com.jobtracker.entity.enums.ExportTrigger;
import com.jobtracker.repository.ExportExecutionRepository;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting a user must take its dependent rows with it.
 *
 * <p>The migrations declare {@code ON DELETE CASCADE} on these foreign keys, but tests run against
 * a schema generated from the entity mappings — so a missing {@code @OnDelete} makes the generated
 * schema stricter than production and any test that clears the {@code users} table starts failing
 * as soon as another test class leaves a child row behind. This test pins the two schemas together.
 */
class UserDeletionCascadeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private GoogleDriveConnectionRepository connectionRepository;
    @Autowired private GoogleDriveBaseResumeRepository baseResumeRepository;
    @Autowired private ExportScheduleRepository exportScheduleRepository;
    @Autowired private ExportExecutionRepository exportExecutionRepository;

    @Test
    void deletingAUser_shouldCascadeToDriveAndExportRows() throws Exception {
        User user = registerUser();

        GoogleDriveConnection connection = connectionRepository.save(driveConnection(user));
        baseResumeRepository.save(baseResume(connection));
        ExportSchedule schedule = exportScheduleRepository.save(exportSchedule(user));
        exportExecutionRepository.save(exportExecution(user, schedule));

        // Registration issues a refresh token, and that table has no cascade; every integration
        // test clears it the same way. Only this user's tokens are removed, so rows belonging to
        // other test classes are left untouched.
        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
                .filter(token -> user.getId().equals(token.getUser().getId()))
                .toList());

        assertThatCode(() -> userRepository.delete(user)).doesNotThrowAnyException();

        // Scoped to this user: the shared in-memory database may hold rows from other test classes.
        assertThat(connectionRepository.findByUserId(user.getId())).isEmpty();
        assertThat(baseResumeRepository.findAllByConnectionUserIdOrderByCreatedAtAsc(user.getId())).isEmpty();
        assertThat(exportScheduleRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId())).isEmpty();
        assertThat(exportExecutionRepository
                .findAllByUserIdOrderByStartedAtDesc(user.getId(), PageRequest.of(0, 1))
                .getTotalElements()).isZero();
    }

    private User registerUser() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Cascade User", "cascade-user@example.com", "pass1234", "pass1234", true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail("cascade-user@example.com").orElseThrow();
    }

    private static GoogleDriveConnection driveConnection(User user) {
        GoogleDriveConnection connection = new GoogleDriveConnection();
        connection.setUser(user);
        connection.setGoogleAccountId("acct-cascade");
        connection.setGoogleEmail("cascade@gmail.com");
        connection.setGoogleDisplayName("Cascade");
        connection.setAccessToken("drive-access");
        connection.setRefreshToken("drive-refresh");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setGrantedScopes("https://www.googleapis.com/auth/drive");
        connection.setConnectedAt(LocalDateTime.now());
        return connection;
    }

    private static GoogleDriveBaseResume baseResume(GoogleDriveConnection connection) {
        GoogleDriveBaseResume resume = new GoogleDriveBaseResume();
        resume.setConnection(connection);
        resume.setGoogleFileId("file-cascade");
        resume.setDocumentName("Base resume");
        return resume;
    }

    private static ExportSchedule exportSchedule(User user) {
        ExportSchedule schedule = new ExportSchedule();
        schedule.setUser(user);
        schedule.setName("Cascade backup");
        schedule.setFormat(ExportFormat.CSV);
        schedule.setFrequency(ExportFrequency.DAILY);
        schedule.setTimeOfDay(LocalTime.of(20, 0));
        schedule.setTimezone("America/Sao_Paulo");
        schedule.setDestination(ExportDestinationType.GOOGLE_DRIVE);
        schedule.setEnabled(true);
        return schedule;
    }

    private static ExportExecution exportExecution(User user, ExportSchedule schedule) {
        ExportExecution execution = new ExportExecution();
        execution.setUser(user);
        execution.setSchedule(schedule);
        execution.setScheduleName(schedule.getName());
        execution.setTrigger(ExportTrigger.SCHEDULED);
        execution.setFormat(ExportFormat.CSV);
        execution.setDestination(ExportDestinationType.GOOGLE_DRIVE);
        execution.setStatus(ExportExecutionStatus.SUCCESS);
        execution.setStartedAt(LocalDateTime.now());
        return execution;
    }
}
