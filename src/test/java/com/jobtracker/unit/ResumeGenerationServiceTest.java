package com.jobtracker.unit;

import com.jobtracker.service.ResumeGenerationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeGenerationServiceTest {

    private final ResumeGenerationService service = new ResumeGenerationService(
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void detectPlaceholdersReturnsUniqueTrimmedNamesInTemplateOrder() {
        List<String> placeholders = service.detectPlaceholders("""
                {{SUMMARY}}
                {{ SKILLS }}
                {{PROJECT_HIGHLIGHT}}
                {{SUMMARY}}
                """);

        assertThat(placeholders).containsExactly("SUMMARY", "SKILLS", "PROJECT_HIGHLIGHT");
    }

    @Test
    void detectPlaceholdersIgnoresEmptyTemplateText() {
        assertThat(service.detectPlaceholders("")).isEmpty();
        assertThat(service.detectPlaceholders((String) null)).isEmpty();
    }
}
