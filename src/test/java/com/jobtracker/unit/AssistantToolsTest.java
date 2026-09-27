package com.jobtracker.unit;

import com.jobtracker.config.AssistantProperties;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.assistant.AssistantApplicationQueryRepository;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.assistant.AssistantApplicationQueryService;
import com.jobtracker.service.assistant.AssistantTools;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AssistantToolsTest {
    @Test
    void contextualOrganizationFollowUpCanSearchWithoutRepeatingTextQuery() {
        UUID userId = UUID.randomUUID();
        AssistantApplicationQueryRepository repository = mock(AssistantApplicationQueryRepository.class);
        AssistantApplicationQueryService queryService = new AssistantApplicationQueryService(
                repository, mock(ApplicationRepository.class), new AssistantProperties());
        AssistantTools tools = new AssistantTools(userId, queryService, mock(DashboardService.class));

        AssistantTools.SearchInput input = new AssistantTools.SearchInput(
                null, "CWI", null, null, null, null, false, null);

        assertThatCode(() -> tools.searchApplications(input)).doesNotThrowAnyException();

        verify(repository).search(userId, null, "CWI", null, null,
                null, null, false, 10, false);
        assertThat(tools.sources()).contains("APPLICATION_SEARCH");
    }
}
