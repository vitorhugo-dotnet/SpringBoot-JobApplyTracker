package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.entity.ExportExecution;
import com.jobtracker.entity.ExportSchedule;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportExecutionStatus;
import com.jobtracker.entity.enums.ExportTrigger;
import com.jobtracker.integration.support.RecordingDriveApiClient;
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
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.export.ScheduledExportExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the polling loop itself: a due schedule must be picked up, executed against the user's
 * Drive and re-armed for its next occurrence.
 *
 * <p>The poller is enabled but its interval is pushed far out, so the only tick in the test is the
 * one the test triggers — no timing races.
 */
@Import(ExportSchedulerIT.SchedulerDriveTestConfig.class)
@TestPropertySource(properties = {
        "app.export.scheduler-enabled=true",
        "app.export.scheduler-interval-ms=3600000",
        "app.export.scheduler-initial-delay-ms=3600000"
})
class ExportSchedulerIT extends AbstractIntegrationTest {

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

    private String accessToken;
    private User owner;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        RegisterRequest register = new RegisterRequest(
                "Scheduler Owner", "scheduler-owner@example.com", "pass1234", "pass1234", true);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();
        accessToken = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class).accessToken();
        owner = userRepository.findByEmail("scheduler-owner@example.com").orElseThrow();

        connectGoogleDrive();
        createApplication("Scheduled Vacancy");
    }

    /**
     * Runs before and after every test: these classes create users, applications and a Google Drive
     * connection, and the in-memory database is shared with every other test class, so leaving rows
     * behind would break whichever class clears the users table next.
     */
    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    private void clearDatabase() {
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
    }

    @Test
    void poller_shouldRunADueScheduleAndRearmIt() throws Exception {
        UUID scheduleId = createDueSchedule();

        scheduledExportExecutor.runDueSchedules();

        ExportExecution execution = exportExecutionRepository.findAll().getFirst();
        assertThat(execution.getTrigger()).isEqualTo(ExportTrigger.SCHEDULED);
        assertThat(execution.getStatus()).isEqualTo(ExportExecutionStatus.SUCCESS);
        assertThat(execution.getRecordCount()).isEqualTo(1);
        assertThat(execution.getScheduleName()).isEqualTo("Daily applications backup");

        RecordingDriveApiClient drive = (RecordingDriveApiClient) googleDriveApiClient;
        assertThat(drive.uploads).hasSize(1);
        assertThat(drive.uploads.getFirst().folderName()).isEqualTo("Applywell Exports");
        assertThat(drive.uploads.getFirst().fileName()).startsWith("applywell-applications-");

        ExportSchedule schedule = exportScheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(schedule.isRunning()).isFalse();
        assertThat(schedule.getNextRunAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void poller_shouldNotRunTheSameScheduleTwiceInARow() throws Exception {
        createDueSchedule();

        scheduledExportExecutor.runDueSchedules();
        // The schedule is now armed for tomorrow, so a second tick must find nothing to do.
        scheduledExportExecutor.runDueSchedules();

        assertThat(exportExecutionRepository.count()).isEqualTo(1);
        assertThat(((RecordingDriveApiClient) googleDriveApiClient).uploads).hasSize(1);
    }

    @Test
    void poller_shouldSkipDisabledSchedules() throws Exception {
        UUID scheduleId = createDueSchedule();
        ExportSchedule schedule = exportScheduleRepository.findById(scheduleId).orElseThrow();
        schedule.setEnabled(false);
        exportScheduleRepository.save(schedule);

        scheduledExportExecutor.runDueSchedules();

        assertThat(exportExecutionRepository.count()).isZero();
    }

    /** Creates a schedule through the API and back-dates its next run so the poller picks it up. */
    private UUID createDueSchedule() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/export-schedules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Daily applications backup","format":"CSV","frequency":"DAILY",
                                 "time":"20:00","timezone":"America/Sao_Paulo","destination":"GOOGLE_DRIVE"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        UUID scheduleId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        ExportSchedule schedule = exportScheduleRepository.findById(scheduleId).orElseThrow();
        schedule.setNextRunAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        exportScheduleRepository.save(schedule);
        return scheduleId;
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

    private void createApplication(String vacancyName) throws Exception {
        ApplicationRequest request = new ApplicationRequest(
                vacancyName, "Recruiter", "ACME", "https://jobs.example.com/1",
                LocalDate.of(2026, 7, 16), false, false, null, "RH", false, "Note", null, null);
        mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @TestConfiguration
    static class SchedulerDriveTestConfig {
        @Bean
        @Primary
        GoogleDriveApiClient googleDriveApiClient() {
            return new RecordingDriveApiClient();
        }
    }
}
