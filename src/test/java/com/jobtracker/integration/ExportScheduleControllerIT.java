package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportExecutionStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ExportExecutionRepository;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.integration.support.RecordingDriveApiClient;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.export.ScheduledExportExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ExportScheduleControllerIT.ExportDriveTestConfig.class)
class ExportScheduleControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private ExportExecutionRepository exportExecutionRepository;
    @Autowired private ExportScheduleRepository exportScheduleRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private ScheduledExportExecutor scheduledExportExecutor;
    @Autowired private GoogleDriveApiClient googleDriveApiClient;

    private String ownerToken;
    private String otherToken;
    private User owner;

    private static final String DAILY_SCHEDULE = """
            {"name":"Daily applications backup","format":"XLSX","frequency":"DAILY","time":"20:00",
             "timezone":"America/Sao_Paulo","enabled":true,"destination":"GOOGLE_DRIVE",
             "filters":{"archived":null}}""";

    @BeforeEach
    void setUp() throws Exception {
        ((RecordingDriveApiClient) googleDriveApiClient).reset();
        exportExecutionRepository.deleteAll();
        exportScheduleRepository.deleteAll();
        googleDriveConnectionRepository.deleteAll();
        userAchievementRepository.deleteAll();
        interviewEventRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        userGamificationRepository.deleteAll();
        applicationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        ownerToken = register("Schedule Owner", "schedule-owner@example.com");
        otherToken = register("Other User", "schedule-other@example.com");
        owner = userRepository.findByEmail("schedule-owner@example.com").orElseThrow();
    }

    @Test
    void createSchedule_shouldPersistTheRecurrenceAndComputeTheNextRun() throws Exception {
        mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAILY_SCHEDULE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Daily applications backup"))
                .andExpect(jsonPath("$.format").value("XLSX"))
                .andExpect(jsonPath("$.frequency").value("DAILY"))
                .andExpect(jsonPath("$.time").value("20:00"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.destination").value("GOOGLE_DRIVE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.nextRunAt").exists());
    }

    @Test
    void createSchedule_shouldReject_whenWeeklyHasNoDayOfWeek() throws Exception {
        mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Weekly","format":"CSV","frequency":"WEEKLY","time":"08:00",
                                 "destination":"GOOGLE_DRIVE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSchedule_shouldReject_whenTimezoneIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Broken","format":"CSV","frequency":"DAILY","time":"08:00",
                                 "timezone":"Mars/Olympus","destination":"GOOGLE_DRIVE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSchedule_shouldReject_whenDestinationIsNotImplementedYet() throws Exception {
        mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mail me","format":"CSV","frequency":"DAILY","time":"08:00",
                                 "destination":"EMAIL"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSchedules_shouldOnlyReturnTheCallersSchedules() throws Exception {
        createSchedule(ownerToken);

        mockMvc.perform(get("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateSchedule_shouldReplaceTheRecurrence() throws Exception {
        String id = createSchedule(ownerToken);

        mockMvc.perform(put("/api/v1/export-schedules/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Weekly backup","format":"CSV","frequency":"WEEKLY","time":"07:30",
                                 "dayOfWeek":1,"timezone":"America/Sao_Paulo","destination":"GOOGLE_DRIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekly backup"))
                .andExpect(jsonPath("$.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.dayOfWeek").value(1))
                .andExpect(jsonPath("$.format").value("CSV"));
    }

    @Test
    void updateSchedule_shouldReturn404_forAnotherUsersSchedule() throws Exception {
        String id = createSchedule(ownerToken);

        mockMvc.perform(put("/api/v1/export-schedules/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAILY_SCHEDULE))
                .andExpect(status().isNotFound());
    }

    @Test
    void setEnabled_shouldClearAndRestoreTheNextRun() throws Exception {
        String id = createSchedule(ownerToken);

        mockMvc.perform(patch("/api/v1/export-schedules/{id}/enabled", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.nextRunAt").doesNotExist());

        mockMvc.perform(patch("/api/v1/export-schedules/{id}/enabled", id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.nextRunAt").exists());
    }

    @Test
    void deleteSchedule_shouldRemoveItButKeepTheHistory() throws Exception {
        String id = createSchedule(ownerToken);
        createApplication(ownerToken, "Kept Vacancy");
        runScheduleSynchronously(UUID.fromString(id));

        mockMvc.perform(delete("/api/v1/export-schedules/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/export-schedules/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/exports/history")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.executions[0].scheduleName").value("Daily applications backup"));
    }

    @Test
    void scheduledRun_shouldUploadTheFileToTheUsersGoogleDrive() throws Exception {
        connectGoogleDrive();
        createApplication(ownerToken, "Exported Vacancy");
        String id = createSchedule(ownerToken);

        runScheduleSynchronously(UUID.fromString(id));

        ExportExecution execution = exportExecutionRepository.findAll().getFirst();
        assertThat(execution.getStatus()).isEqualTo(ExportExecutionStatus.SUCCESS);
        assertThat(execution.getRecordCount()).isEqualTo(1);
        assertThat(execution.getFileName()).startsWith("applywell-applications-");
        assertThat(execution.getFileUrl()).isNotBlank();

        RecordingDriveApiClient drive = (RecordingDriveApiClient) googleDriveApiClient;
        assertThat(drive.uploads).hasSize(1);
        assertThat(drive.uploads.getFirst().folderName()).isEqualTo("Applywell Exports");
        assertThat(drive.uploads.getFirst().content()).isNotEmpty();

        // The lock is released and the schedule is armed again for the next occurrence.
        var schedule = exportScheduleRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(schedule.isRunning()).isFalse();
        assertThat(schedule.getLastRunAt()).isNotNull();
        assertThat(schedule.getNextRunAt()).isNotNull();
    }

    @Test
    void scheduledRun_shouldRecordASanitizedFailure_whenDriveIsNotConnected() throws Exception {
        createApplication(ownerToken, "Exported Vacancy");
        String id = createSchedule(ownerToken);

        runScheduleSynchronously(UUID.fromString(id));

        ExportExecution execution = exportExecutionRepository.findAll().getFirst();
        assertThat(execution.getStatus()).isEqualTo(ExportExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).isNotBlank();
        assertThat(execution.getErrorMessage()).doesNotContain("\n");
        assertThat(execution.getErrorMessage().length()).isLessThanOrEqualTo(500);

        // A failed run still schedules the next one instead of silently disabling the backup.
        var schedule = exportScheduleRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(schedule.isRunning()).isFalse();
        assertThat(schedule.getNextRunAt()).isNotNull();
    }

    @Test
    void runNow_shouldAcceptTheRequestAndRecordAnExecution() throws Exception {
        connectGoogleDrive();
        createApplication(ownerToken, "Exported Vacancy");
        String id = createSchedule(ownerToken);

        mockMvc.perform(post("/api/v1/export-schedules/{id}/run-now", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trigger").value("RUN_NOW"))
                .andExpect(jsonPath("$.scheduleId").value(id));

        assertThat(exportExecutionRepository.count()).isEqualTo(1);
    }

    @Test
    void runNow_shouldReturn409_whenAnExecutionIsAlreadyRunning() throws Exception {
        String id = createSchedule(ownerToken);
        // Hold the execution lock exactly like an in-flight run (or another instance) would.
        assertThat(scheduledExportExecutor.claim(UUID.fromString(id))).isTrue();

        mockMvc.perform(post("/api/v1/export-schedules/{id}/run-now", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void claim_shouldOnlySucceedOnceWhileTheLockIsHeld() throws Exception {
        String id = createSchedule(ownerToken);
        UUID scheduleId = UUID.fromString(id);

        assertThat(scheduledExportExecutor.claim(scheduleId)).isTrue();
        assertThat(scheduledExportExecutor.claim(scheduleId)).isFalse();

        scheduledExportExecutor.releaseLock(scheduleId, LocalDateTime.now());
        assertThat(scheduledExportExecutor.claim(scheduleId)).isTrue();
    }

    @Test
    void runNow_shouldReturn404_forAnotherUsersSchedule() throws Exception {
        String id = createSchedule(ownerToken);

        mockMvc.perform(post("/api/v1/export-schedules/{id}/run-now", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    /** Claims the lock and runs the export on this thread, exactly as the poller would. */
    private void runScheduleSynchronously(UUID scheduleId) {
        assertThat(scheduledExportExecutor.claim(scheduleId)).isTrue();
        var snapshot = scheduledExportExecutor.loadSnapshot(scheduleId).orElseThrow();
        var execution = exportExecutionRepository.save(pendingExecution(snapshot));
        scheduledExportExecutor.executeClaimed(scheduleId, execution.getId());
    }

    private ExportExecution pendingExecution(ScheduledExportExecutor.ScheduleSnapshot snapshot) {
        ExportExecution execution = new ExportExecution();
        execution.setUser(snapshot.user());
        execution.setSchedule(snapshot.entity());
        execution.setScheduleName(snapshot.entity().getName());
        execution.setTrigger(com.jobtracker.entity.enums.ExportTrigger.SCHEDULED);
        execution.setFormat(snapshot.format());
        execution.setDestination(snapshot.destination());
        execution.setStatus(ExportExecutionStatus.PENDING);
        execution.setStartedAt(LocalDateTime.now());
        return execution;
    }

    private void connectGoogleDrive() {
        GoogleDriveConnection connection = new GoogleDriveConnection();
        connection.setUser(owner);
        connection.setGoogleAccountId("acct-1");
        connection.setGoogleEmail("owner@gmail.com");
        connection.setGoogleDisplayName("Owner");
        connection.setAccessToken("drive-access");
        connection.setRefreshToken("drive-refresh");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setGrantedScopes("https://www.googleapis.com/auth/drive");
        connection.setConnectedAt(LocalDateTime.now());
        connection.setRootFolderId("root-folder");
        connection.setRootFolderName("Applywell");
        googleDriveConnectionRepository.save(connection);
    }

    private String createSchedule(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAILY_SCHEDULE))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void createApplication(String token, String vacancyName) throws Exception {
        ApplicationRequest request = new ApplicationRequest(
                vacancyName, "Recruiter", "ACME", "https://jobs.example.com/1",
                LocalDate.of(2026, 7, 16), false, false, null, "RH", false, "Note", null, null);
        mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String register(String name, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(name, email, "pass1234", "pass1234", true);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }

    @TestConfiguration
    static class ExportDriveTestConfig {
        @Bean
        @Primary
        GoogleDriveApiClient googleDriveApiClient() {
            return new RecordingDriveApiClient();
        }
    }
}
