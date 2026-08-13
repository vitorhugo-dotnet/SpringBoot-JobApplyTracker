package com.jobtracker.e2e;

import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ExportExecutionRepository;
import com.jobtracker.repository.ExportScheduleRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class ExportE2ETest extends AbstractE2ETest {

    @Autowired private UserRepository userRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private ExportExecutionRepository exportExecutionRepository;
    @Autowired private ExportScheduleRepository exportScheduleRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        exportExecutionRepository.deleteAll();
        exportScheduleRepository.deleteAll();
        userAchievementRepository.deleteAll();
        userGamificationRepository.deleteAll();
        interviewEventRepository.deleteAll();
        applicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        // A Drive connection points at its user, so it must go before the users it references.
        googleDriveConnectionRepository.deleteAll();
        userRepository.deleteAll();

        Response register = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Export E2E User",
                          "email": "exporte2e@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234",
                          "acceptedPrivacyPolicy": true
                        }
                        """)
                .post("/api/v1/auth/register")
                .then().statusCode(201).extract().response();

        accessToken = register.jsonPath().getString("accessToken");
    }

    @Test
    void fullExportFlow_downloadCsvThenScheduleAndInspectHistory() {
        createApplication("Engenheiro de Software Sênior", "Ação Ltda");

        // 1. Manual CSV download — attachment, UTF-8 BOM, accents preserved.
        Response csv = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"format": "CSV", "filters": {"status": ["RH"]}}
                        """)
                .post("/api/v1/exports/applications")
                .then().statusCode(200)
                .header("Content-Disposition", org.hamcrest.Matchers.containsString("applywell-applications-"))
                .header("X-Export-Record-Count", equalTo("1"))
                .extract().response();

        byte[] csvBytes = csv.asByteArray();
        assertThat(csvBytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(new String(csvBytes, StandardCharsets.UTF_8)).contains("Engenheiro de Software Sênior");

        // 2. Same data as XLSX.
        byte[] xlsx = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"format": "XLSX"}
                        """)
                .post("/api/v1/exports/applications")
                .then().statusCode(200)
                .header("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx"))
                .extract().asByteArray();
        // XLSX files are ZIP containers: "PK".
        assertThat(new String(xlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");

        // 3. Create a recurring export.
        String scheduleId = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {
                          "name": "Backup diário das candidaturas",
                          "format": "XLSX",
                          "frequency": "DAILY",
                          "time": "20:00",
                          "timezone": "America/Sao_Paulo",
                          "enabled": true,
                          "filters": {"archived": null},
                          "destination": "GOOGLE_DRIVE"
                        }
                        """)
                .post("/api/v1/export-schedules")
                .then().statusCode(201)
                .body("nextRunAt", notNullValue())
                .body("enabled", equalTo(true))
                .extract().jsonPath().getString("id");

        // 4. Disable then re-enable it.
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"enabled": false}
                        """)
                .patch("/api/v1/export-schedules/{id}/enabled", scheduleId)
                .then().statusCode(200)
                .body("enabled", equalTo(false));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"enabled": true}
                        """)
                .patch("/api/v1/export-schedules/{id}/enabled", scheduleId)
                .then().statusCode(200)
                .body("enabled", equalTo(true));

        // 5. The two manual downloads are in the history.
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/exports/history")
                .then().statusCode(200)
                .body("totalElements", equalTo(2))
                .body("executions[0].trigger", equalTo("MANUAL"))
                .body("executions[0].status", equalTo("SUCCESS"));

        // 6. Delete the schedule; the history survives it.
        given()
                .header("Authorization", "Bearer " + accessToken)
                .delete("/api/v1/export-schedules/{id}", scheduleId)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/export-schedules")
                .then().statusCode(200)
                .body("size()", equalTo(0));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/v1/exports/history")
                .then().statusCode(200)
                .body("totalElements", equalTo(2));
    }

    private void createApplication(String vacancyName, String organization) {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {
                          "vacancyName": "%s",
                          "recruiterName": "Jane Recruiter",
                          "organization": "%s",
                          "vacancyLink": "https://jobs.example.com/se",
                          "applicationDate": "%s",
                          "rhAcceptedConnection": false,
                          "interviewScheduled": false,
                          "status": "RH",
                          "recruiterDmReminderEnabled": false,
                          "note": "Nota com acentuação"
                        }
                        """.formatted(vacancyName, organization, LocalDate.now().minusDays(1)))
                .post("/api/v1/applications")
                .then().statusCode(201);
    }
}
