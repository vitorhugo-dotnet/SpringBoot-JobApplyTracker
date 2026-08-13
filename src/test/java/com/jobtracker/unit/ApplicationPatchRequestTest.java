package com.jobtracker.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobtracker.dto.application.ApplicationPatchRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The patch semantics depend on telling an omitted JSON property apart from an explicit null, so the
 * deserialization behaviour is pinned down here rather than only through the controller.
 */
class ApplicationPatchRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void omittedProperties_areNotMarkedAsProvided() throws Exception {
        ApplicationPatchRequest request = read("{\"archived\": false}");

        assertThat(request.hasArchived()).isTrue();
        assertThat(request.getArchived()).isFalse();
        assertThat(request.hasStatus()).isFalse();
        assertThat(request.hasNote()).isFalse();
        assertThat(request.hasVacancyName()).isFalse();
        assertThat(request.hasApplicationDate()).isFalse();
    }

    @Test
    void explicitNull_onNullableField_isProvidedAndClearsTheValue() throws Exception {
        ApplicationPatchRequest request = read("{\"note\": null}");

        assertThat(request.hasNote()).isTrue();
        assertThat(request.getNote()).isNull();
    }

    @Test
    void explicitNull_onNonNullableField_isIgnored() throws Exception {
        ApplicationPatchRequest request = read("{\"archived\": null, \"interviewScheduled\": null}");

        assertThat(request.hasArchived()).isFalse();
        assertThat(request.hasInterviewScheduled()).isFalse();
    }

    @Test
    void emptyBody_marksNothingAsProvided() throws Exception {
        ApplicationPatchRequest request = read("{}");

        assertThat(request.hasArchived()).isFalse();
        assertThat(request.hasStatus()).isFalse();
        assertThat(request.hasNote()).isFalse();
        assertThat(request.hasApplicationDate()).isFalse();
        assertThat(request.hasInterviewCount()).isFalse();
    }

    @Test
    void allSupportedFields_areDeserialized() throws Exception {
        ApplicationPatchRequest request = read("""
                {
                  "vacancyName": "Backend Engineer",
                  "recruiterName": "Jane Smith",
                  "organization": "TechCorp",
                  "vacancyLink": "https://example.com/jobs/123",
                  "applicationDate": "2024-06-01",
                  "rhAcceptedConnection": true,
                  "interviewScheduled": true,
                  "nextStepDateTime": "2024-06-10T14:00:00",
                  "status": "Rejected",
                  "recruiterDmReminderEnabled": true,
                  "note": "Follow up",
                  "platform": "LinkedIn",
                  "interviewCount": 3,
                  "archived": false
                }
                """);

        assertThat(request.getVacancyName()).isEqualTo("Backend Engineer");
        assertThat(request.getRecruiterName()).isEqualTo("Jane Smith");
        assertThat(request.getOrganization()).isEqualTo("TechCorp");
        assertThat(request.getVacancyLink()).isEqualTo("https://example.com/jobs/123");
        assertThat(request.getApplicationDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(request.getRhAcceptedConnection()).isTrue();
        assertThat(request.getInterviewScheduled()).isTrue();
        assertThat(request.getNextStepDateTime()).isEqualTo("2024-06-10T14:00:00");
        assertThat(request.getStatus()).isEqualTo("Rejected");
        assertThat(request.getRecruiterDmReminderEnabled()).isTrue();
        assertThat(request.getNote()).isEqualTo("Follow up");
        assertThat(request.getPlatform()).isEqualTo("LinkedIn");
        assertThat(request.getInterviewCount()).isEqualTo(3);
        assertThat(request.getArchived()).isFalse();
    }

    private ApplicationPatchRequest read(String json) throws Exception {
        return objectMapper.readValue(json, ApplicationPatchRequest.class);
    }
}
