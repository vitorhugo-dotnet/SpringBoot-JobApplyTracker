package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ExportExecutionRepository;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportControllerIT extends AbstractIntegrationTest {

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

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        clearDatabase();

        ownerToken = register("Export Owner", "export-owner@example.com");
        otherToken = register("Other User", "export-other@example.com");
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
        exportExecutionRepository.deleteAll();
        exportScheduleRepository.deleteAll();
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
    void exportCsv_shouldReturnAttachmentWithBomAndAccents() throws Exception {
        createApplication(ownerToken, "Analista de Sistemas Sênior", "Ação Ltda", "RH");

        MvcResult result = mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"CSV"}"""))
                .andExpect(status().isOk())
                .andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment;");
        assertThat(disposition).contains("applywell-applications-");
        assertThat(disposition).contains(".csv");
        assertThat(result.getResponse().getHeader("X-Export-Record-Count")).isEqualTo("1");

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body[0] & 0xFF).isEqualTo(0xEF);
        String content = new String(body, StandardCharsets.UTF_8);
        assertThat(content).contains("Analista de Sistemas Sênior");
        assertThat(content).contains("Ação Ltda");
    }

    @Test
    void exportXlsx_shouldReturnAReadableWorkbook() throws Exception {
        createApplication(ownerToken, "Data Engineer", "ACME", "RH");

        MvcResult result = mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"XLSX","columns":["vacancyName","organization"]}"""))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains(".xlsx");

        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Vacancy");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Data Engineer");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("ACME");
        }
    }

    @Test
    void export_shouldNeverIncludeAnotherUsersApplications() throws Exception {
        createApplication(ownerToken, "Owner Vacancy", "Owner Org", "RH");
        createApplication(otherToken, "Secret Vacancy", "Secret Org", "RH");

        String content = exportCsv(ownerToken, """
                {"format":"CSV"}""");

        assertThat(content).contains("Owner Vacancy");
        assertThat(content).doesNotContain("Secret Vacancy");
        assertThat(content).doesNotContain("Secret Org");
    }

    @Test
    void export_shouldApplyStatusAndOrganizationFilters() throws Exception {
        createApplication(ownerToken, "Kept Vacancy", "ACME", "RH");
        createApplication(ownerToken, "Filtered By Status", "ACME", "Rejected");
        createApplication(ownerToken, "Filtered By Org", "Globex", "RH");

        String content = exportCsv(ownerToken, """
                {"format":"CSV","filters":{"status":["RH"],"organization":"ACME"}}""");

        assertThat(content).contains("Kept Vacancy");
        assertThat(content).doesNotContain("Filtered By Status");
        assertThat(content).doesNotContain("Filtered By Org");
    }

    @Test
    void export_shouldApplyApplicationDateRangeFilter() throws Exception {
        createApplication(ownerToken, "In Range", "ACME", "RH", LocalDate.of(2026, 7, 10));
        createApplication(ownerToken, "Out Of Range", "ACME", "RH", LocalDate.of(2026, 6, 1));

        String content = exportCsv(ownerToken, """
                {"format":"CSV","filters":{"applicationDateFrom":"2026-07-01","applicationDateTo":"2026-07-31"}}""");

        assertThat(content).contains("In Range");
        assertThat(content).doesNotContain("Out Of Range");
    }

    @Test
    void export_shouldIncludeArchivedApplicationsOnlyWhenAsked() throws Exception {
        String archivedId = createApplication(ownerToken, "Archived Vacancy", "ACME", "RH");
        createApplication(ownerToken, "Active Vacancy", "ACME", "RH");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/applications/{id}/archive", archivedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        String activeOnly = exportCsv(ownerToken, """
                {"format":"CSV","filters":{"archived":false}}""");
        assertThat(activeOnly).contains("Active Vacancy");
        assertThat(activeOnly).doesNotContain("Archived Vacancy");

        String everything = exportCsv(ownerToken, """
                {"format":"CSV"}""");
        assertThat(everything).contains("Active Vacancy");
        assertThat(everything).contains("Archived Vacancy");
    }

    @Test
    void export_shouldNeutralizeFormulasComingFromUserContent() throws Exception {
        createApplication(ownerToken, "=1+1", "ACME", "RH");

        String content = exportCsv(ownerToken, """
                {"format":"CSV","columns":["vacancyName"]}""");

        assertThat(content).contains("'=1+1");
    }

    @Test
    void export_shouldReject_whenColumnIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"CSV","columns":["passwordHash"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_shouldReject_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/exports/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"CSV"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void columns_shouldListTheExportableFieldsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/exports/columns")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'vacancyName')]").exists())
                .andExpect(jsonPath("$[?(@.key == 'note')]").exists())
                .andExpect(jsonPath("$[?(@.key == 'password')]").doesNotExist());
    }

    @Test
    void history_shouldRecordManualExportsAndStayPerUser() throws Exception {
        createApplication(ownerToken, "Tracked Vacancy", "ACME", "RH");
        exportCsv(ownerToken, """
                {"format":"CSV"}""");

        mockMvc.perform(get("/api/v1/exports/history")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.executions[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.executions[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.executions[0].recordCount").value(1));

        mockMvc.perform(get("/api/v1/exports/history")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void historyById_shouldReturn404_forAnotherUsersExecution() throws Exception {
        createApplication(ownerToken, "Tracked Vacancy", "ACME", "RH");
        exportCsv(ownerToken, """
                {"format":"CSV"}""");

        MvcResult historyResult = mockMvc.perform(get("/api/v1/exports/history")
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        String executionId = objectMapper.readTree(historyResult.getResponse().getContentAsString())
                .get("executions").get(0).get("id").asText();

        mockMvc.perform(get("/api/v1/exports/history/{id}", executionId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/exports/history/{id}", executionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String exportCsv(String token, String payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
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

    private String createApplication(String token, String vacancyName, String organization, String status)
            throws Exception {
        return createApplication(token, vacancyName, organization, status, LocalDate.of(2026, 7, 16));
    }

    private String createApplication(String token, String vacancyName, String organization, String status,
                                     LocalDate applicationDate) throws Exception {
        ApplicationRequest request = new ApplicationRequest(
                vacancyName, "Recruiter", organization, "https://jobs.example.com/1",
                applicationDate, false, false, null, status, false, "Note", null, null);
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> emptyFilters() {
        return Map.of();
    }
}
