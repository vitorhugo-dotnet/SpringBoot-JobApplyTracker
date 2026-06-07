package com.jobtracker.unit.mcp;

import com.jobtracker.dto.gamification.GamificationEventRequest;
import com.jobtracker.dto.gamification.GamificationEventSummary;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.entity.enums.GamificationEventType;
import com.jobtracker.gamification.GamificationProgressCallback;
import com.jobtracker.mcp.tools.McpGamificationTools;
import com.jobtracker.service.GamificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpGamificationToolsTest {

    @Mock
    private GamificationService gamificationService;

    @Mock
    private McpSyncRequestContext mcpContext;

    @InjectMocks
    private McpGamificationTools tools;

    private static GamificationEventSummary sampleSummary(GamificationEventType type) {
        GamificationProfileResponse profile = new GamificationProfileResponse(
                110, 2, 100, 400, 290, 2, "Job Hunter Iniciante", 3);
        return new GamificationEventSummary(
                type, 10, false, 2, 3, List.of(), "+10 XP por registrar uma nova aplicacao.", profile);
    }

    private static GamificationEventSummary levelUpSummary() {
        GamificationProfileResponse profile = new GamificationProfileResponse(
                100, 2, 100, 400, 300, 0, "Job Hunter Iniciante", 1);
        return new GamificationEventSummary(
                GamificationEventType.APPLICATION_CREATED, 10, true, 2, 1,
                List.of("PERSISTENT"), "+10 XP por registrar uma nova aplicacao e subir de nivel.", profile);
    }

    // --- final response payload ---

    @Test
    void applyGamificationEvent_returnsServiceSummaryUnchanged() {
        GamificationEventSummary expected = sampleSummary(GamificationEventType.APPLICATION_CREATED);
        when(gamificationService.applyEventWithProgress(any(), any())).thenReturn(expected);

        GamificationEventSummary result = tools.applyGamificationEvent(
                mcpContext, "APPLICATION_CREATED", null, null);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void applyGamificationEvent_propagatesXpAndLevelFields() {
        GamificationEventSummary levelUp = levelUpSummary();
        when(gamificationService.applyEventWithProgress(any(), any())).thenReturn(levelUp);

        GamificationEventSummary result = tools.applyGamificationEvent(
                mcpContext, "APPLICATION_CREATED", null, null);

        assertThat(result.xpGained()).isEqualTo(10);
        assertThat(result.leveledUp()).isTrue();
        assertThat(result.newLevel()).isEqualTo(2);
        assertThat(result.newlyUnlockedAchievements()).containsExactly("PERSISTENT");
        assertThat(result.streakDays()).isEqualTo(1);
    }

    @Test
    void applyGamificationEvent_parsesApplicationId() {
        UUID appId = UUID.randomUUID();
        ArgumentCaptor<GamificationEventRequest> captor = ArgumentCaptor.forClass(GamificationEventRequest.class);
        when(gamificationService.applyEventWithProgress(captor.capture(), any()))
                .thenReturn(sampleSummary(GamificationEventType.RECRUITER_DM_SENT));

        tools.applyGamificationEvent(mcpContext, "RECRUITER_DM_SENT", appId.toString(), null);

        assertThat(captor.getValue().applicationId()).isEqualTo(appId);
        assertThat(captor.getValue().eventType()).isEqualTo(GamificationEventType.RECRUITER_DM_SENT);
    }

    // --- progress notifications when context is present ---

    @Test
    @SuppressWarnings("unchecked")
    void applyGamificationEvent_emitsProgressNotificationsViaContext() {
        List<String> capturedMessages = new ArrayList<>();

        // Capture the callback passed to the service and replay its steps
        doAnswer(invocation -> {
            GamificationProgressCallback cb = invocation.getArgument(1);
            cb.onStep(1, 4, "Validating event eligibility");
            cb.onStep(2, 4, "Awarding XP");
            cb.onStep(3, 4, "Checking achievements");
            cb.onStep(4, 4, "Building summary");
            return sampleSummary(GamificationEventType.NOTE_ADDED);
        }).when(gamificationService).applyEventWithProgress(any(), any());

        doAnswer(inv -> {
            Consumer<org.springaicommunity.mcp.context.McpRequestContextTypes.ProgressSpec> specConsumer = inv.getArgument(0);
            // record the message by using a recording spec
            capturedMessages.add("step");
            return null;
        }).when(mcpContext).progress(any(Consumer.class));

        tools.applyGamificationEvent(mcpContext, "NOTE_ADDED", null, null);

        // 4 progress notifications emitted (one per step)
        verify(mcpContext, times(4)).progress(any(Consumer.class));
    }

    // --- fallback when progress is unavailable ---

    @Test
    void applyGamificationEvent_nullContext_doesNotThrow() {
        GamificationEventSummary expected = sampleSummary(GamificationEventType.OFFER_WON);

        // Capture and replay the callback even with null context
        doAnswer(invocation -> {
            GamificationProgressCallback cb = invocation.getArgument(1);
            cb.onStep(1, 4, "step1");
            cb.onStep(2, 4, "step2");
            return expected;
        }).when(gamificationService).applyEventWithProgress(any(), any());

        GamificationEventSummary result = tools.applyGamificationEvent(null, "OFFER_WON", null, null);

        assertThat(result).isEqualTo(expected);
        // no interaction with null mcpContext — no NPE
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyGamificationEvent_progressExceptionDoesNotFailTool() {
        GamificationEventSummary expected = sampleSummary(GamificationEventType.APPLICATION_CREATED);

        doAnswer(invocation -> {
            GamificationProgressCallback cb = invocation.getArgument(1);
            cb.onStep(1, 4, "step");
            return expected;
        }).when(gamificationService).applyEventWithProgress(any(), any());

        // Simulate context that throws on progress (e.g. no progressToken registered)
        doThrow(new RuntimeException("no progress token")).when(mcpContext).progress(any(Consumer.class));

        GamificationEventSummary result = tools.applyGamificationEvent(
                mcpContext, "APPLICATION_CREATED", null, null);

        assertThat(result).isEqualTo(expected);
    }

    // --- request mapping ---

    @Test
    void applyGamificationEvent_nullOptionalParams_acceptedGracefully() {
        ArgumentCaptor<GamificationEventRequest> captor = ArgumentCaptor.forClass(GamificationEventRequest.class);
        when(gamificationService.applyEventWithProgress(captor.capture(), any()))
                .thenReturn(sampleSummary(GamificationEventType.INTERVIEW_PROGRESS));

        tools.applyGamificationEvent(mcpContext, "INTERVIEW_PROGRESS", null, null);

        GamificationEventRequest req = captor.getValue();
        assertThat(req.eventType()).isEqualTo(GamificationEventType.INTERVIEW_PROGRESS);
        assertThat(req.applicationId()).isNull();
        assertThat(req.occurredAt()).isNull();
    }
}
