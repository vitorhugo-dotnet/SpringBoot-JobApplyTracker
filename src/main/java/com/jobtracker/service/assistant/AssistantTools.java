package com.jobtracker.service.assistant;

import com.jobtracker.dto.assistant.AssistantApplicationView;
import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.repository.assistant.AssistantApplicationQueryRepository.ApplicationStats;
import com.jobtracker.service.DashboardService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AssistantTools {
    private final UUID userId;
    private final AssistantApplicationQueryService queries;
    private final DashboardService dashboard;
    private final Set<String> sources = ConcurrentHashMap.newKeySet();

    public AssistantTools(UUID userId, AssistantApplicationQueryService queries, DashboardService dashboard) {
        this.userId = userId;
        this.queries = queries;
        this.dashboard = dashboard;
    }

    @Tool(description = "Search the authenticated user's applications using ranked text search and optional structured filters. Never accepts a user ID.")
    public List<AssistantApplicationView> searchApplications(SearchInput input) {
        sources.add("APPLICATION_SEARCH");
        return queries.search(userId, input.query(), input.organization(), input.status(), input.platform(),
                input.applicationDateFrom(), input.applicationDateTo(), input.archived(), input.limit());
    }

    @Tool(description = "Get one sanitized application by ID, scoped to the authenticated user.")
    public AssistantApplicationView getApplication(GetInput input) {
        sources.add("APPLICATION_DETAIL");
        return queries.get(userId, input.applicationId());
    }

    @Tool(description = "Count applications and interviews using explicit filters. Prefer this for aggregate questions.")
    public ApplicationStats getApplicationStats(StatsInput input) {
        sources.add("APPLICATION_STATS");
        return queries.stats(userId, input.query(), input.organization(), input.status(), input.platform(),
                input.applicationDateFrom(), input.applicationDateTo(), input.archived());
    }

    @Tool(description = "Get a bounded deterministic chronological list of the user's applications.")
    public List<AssistantApplicationView> getApplicationTimeline(TimelineInput input) {
        sources.add("APPLICATION_TIMELINE");
        return queries.timeline(userId, input.organization(), input.status(), input.applicationDateFrom(),
                input.applicationDateTo(), input.archived(), input.oldestFirst(), input.limit());
    }

    @Tool(description = "Get the existing ApplyWell dashboard summary for the authenticated user.")
    public DashboardSummaryResponse getDashboardSummary() {
        sources.add("DASHBOARD_SUMMARY");
        return dashboard.getSummary(userId);
    }

    public Set<String> sources() { return Set.copyOf(sources); }

    public record SearchInput(
            @ToolParam(description = "Optional free-text search query.", required = false) String query,
            @ToolParam(description = "Optional exact organization filter.", required = false) String organization,
            @ToolParam(description = "Optional application status filter.", required = false) String status,
            @ToolParam(description = "Optional application platform filter.", required = false) String platform,
            @ToolParam(description = "Optional inclusive application date lower bound.", required = false) LocalDate applicationDateFrom,
            @ToolParam(description = "Optional inclusive application date upper bound.", required = false) LocalDate applicationDateTo,
            boolean archived,
            @ToolParam(description = "Optional result limit.", required = false) Integer limit) {}
    public record GetInput(UUID applicationId) {}
    public record StatsInput(String query, String organization, String status, String platform,
                             LocalDate applicationDateFrom, LocalDate applicationDateTo,
                             boolean archived) {}
    public record TimelineInput(String organization, String status, LocalDate applicationDateFrom,
                                LocalDate applicationDateTo, boolean archived,
                                boolean oldestFirst, Integer limit) {}
}
