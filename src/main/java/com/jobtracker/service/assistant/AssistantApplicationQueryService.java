package com.jobtracker.service.assistant;

import com.jobtracker.config.AssistantProperties;
import com.jobtracker.dto.assistant.AssistantApplicationView;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.assistant.AssistantApplicationQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AssistantApplicationQueryService {
    private static final Set<String> SHORT_TOKENS = Set.of("go", "ai", "c#", "c++", ".net");
    private final AssistantApplicationQueryRepository queries;
    private final ApplicationRepository applications;
    private final AssistantProperties properties;

    public AssistantApplicationQueryService(AssistantApplicationQueryRepository queries,
                                            ApplicationRepository applications,
                                            AssistantProperties properties) {
        this.queries = queries;
        this.applications = applications;
        this.properties = properties;
    }

    public List<AssistantApplicationView> search(UUID userId, String query, String organization,
                                                 String status, String platform, LocalDate from,
                                                 LocalDate to, boolean archived, Integer limit) {
        String normalized = requiredQuery(query);
        return queries.search(userId, normalized, organization, status, platform, from, to,
                archived, properties.clampLimit(limit), usesFallback(normalized));
    }

    public AssistantApplicationView get(UUID userId, UUID id) {
        JobApplication a = applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return new AssistantApplicationView(a.getId(), a.getVacancyName(), a.getOrganization(),
                a.getRecruiterName(), a.getStatus(), a.getApplicationDate(), a.getPlatform(),
                a.getInterviewCount(), a.getNextStepDateTime(), excerpt(a.getNote()),
                a.isArchived(), null);
    }

    public AssistantApplicationQueryRepository.ApplicationStats stats(UUID userId, String query,
            String organization, String status, String platform, LocalDate from, LocalDate to,
            boolean archived) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return queries.stats(userId, normalized, organization, status, platform, from, to,
                archived, normalized != null && usesFallback(normalized));
    }

    public List<AssistantApplicationView> timeline(UUID userId, String organization, String status,
            LocalDate from, LocalDate to, boolean archived, boolean oldestFirst, Integer limit) {
        return queries.timeline(userId, organization, status, from, to, archived, oldestFirst,
                properties.clampLimit(limit));
    }

    private String requiredQuery(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        return query.trim();
    }

    private boolean usesFallback(String query) {
        String normalized = query.toLowerCase();
        return SHORT_TOKENS.contains(normalized) || normalized.length() < 3;
    }

    private String excerpt(String note) {
        if (note == null || note.length() <= 500) return note;
        return note.substring(0, 500) + "…";
    }
}
