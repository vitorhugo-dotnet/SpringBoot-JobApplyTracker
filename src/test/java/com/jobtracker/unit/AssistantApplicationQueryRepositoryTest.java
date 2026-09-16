package com.jobtracker.unit;

import com.jobtracker.dto.assistant.AssistantApplicationView;
import com.jobtracker.repository.assistant.AssistantApplicationQueryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AssistantApplicationQueryRepositoryTest {
    @Test
    void filterOnlySearchSkipsTextPredicates() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssistantApplicationQueryRepository repository = new AssistantApplicationQueryRepository(jdbc);

        assertThatCode(() -> repository.search(UUID.randomUUID(), null, "CWI", null, null,
                null, null, false, 10, false)).doesNotThrowAnyException();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<AssistantApplicationView>>any());

        assertThat(sql.getValue())
                .contains("LOWER(organization) = LOWER(:organization)")
                .contains("ORDER BY application_date DESC")
                .doesNotContain("MATCH(")
                .doesNotContain("LIKE :likeQuery");
    }
}
