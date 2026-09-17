package com.jobtracker.unit;

import com.jobtracker.config.AssistantProperties;
import com.jobtracker.dto.assistant.AssistantChatRequest;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.assistant.AssistantApplicationQueryService;
import com.jobtracker.service.assistant.AssistantService;
import com.jobtracker.service.assistant.AssistantService.AssistantStream;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantConversationMemoryTest {

    @Test
    void chatRequestRequiresConversationId() {
        RecordComponent[] components = AssistantChatRequest.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
                .containsExactly("conversationId", "message");

        RecordComponent conversationId = Arrays.stream(components)
                .filter(component -> component.getName().equals("conversationId"))
                .findFirst()
                .orElseThrow();

        assertThat(conversationId.getType()).isEqualTo(UUID.class);
        assertThat(conversationId.getAnnotation(NotNull.class)).isNotNull();
    }

    @Test
    void sameConversationIncludesPreviousTurn() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = recordingModel(prompts);
        SecurityUtils security = mock(SecurityUtils.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(security.getCurrentUserId()).thenReturn(userId);

        AssistantService service = service(model, security);

        consume(invokeStream(service, conversationId, "Quantas vagas já me candidatei no BTG?"));
        consume(invokeStream(service, conversationId, "Eu deixei alguma nota explicando o motivo que fui rejeitado?"));

        assertThat(promptTexts(prompts.get(1)))
                .contains("Quantas vagas já me candidatei no BTG?")
                .contains("assistant-response-1")
                .contains("Eu deixei alguma nota explicando o motivo que fui rejeitado?");
    }

    @Test
    void differentConversationDoesNotSharePreviousTurn() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = recordingModel(prompts);
        SecurityUtils security = mock(SecurityUtils.class);
        UUID userId = UUID.randomUUID();
        when(security.getCurrentUserId()).thenReturn(userId);

        AssistantService service = service(model, security);

        consume(invokeStream(service, UUID.randomUUID(), "Contexto secreto da conversa A"));
        consume(invokeStream(service, UUID.randomUUID(), "Pergunta da conversa B"));

        assertThat(promptTexts(prompts.get(1)))
                .doesNotContain("Contexto secreto da conversa A")
                .doesNotContain("assistant-response-1")
                .contains("Pergunta da conversa B");
    }

    @Test
    void sameConversationIdFromDifferentUsersDoesNotShareContext() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = recordingModel(prompts);
        SecurityUtils security = mock(SecurityUtils.class);
        UUID conversationId = UUID.randomUUID();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        when(security.getCurrentUserId()).thenReturn(firstUser, secondUser);

        AssistantService service = service(model, security);

        consume(invokeStream(service, conversationId, "Contexto exclusivo do primeiro usuário"));
        consume(invokeStream(service, conversationId, "Pergunta do segundo usuário"));

        assertThat(promptTexts(prompts.get(1)))
                .doesNotContain("Contexto exclusivo do primeiro usuário")
                .doesNotContain("assistant-response-1")
                .contains("Pergunta do segundo usuário");
    }

    @Test
    void memoryWindowIsBoundedByDefault() throws Exception {
        Method getter = Arrays.stream(AssistantProperties.class.getMethods())
                .filter(method -> method.getName().equals("getMemoryMaxMessages"))
                .findFirst()
                .orElse(null);

        assertThat(getter).as("AssistantProperties#getMemoryMaxMessages").isNotNull();
        assertThat(getter.invoke(new AssistantProperties())).isEqualTo(20);
    }

    @Test
    void systemPromptPreservesRelevantFiltersForFollowUps() throws Exception {
        Field field = AssistantService.class.getDeclaredField("SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        assertThat(prompt)
                .contains("Resolve follow-up questions using the current conversation context.")
                .contains("organization")
                .contains("application")
                .contains("recruiter")
                .contains("status")
                .contains("platform")
                .contains("date range");
    }

    private ChatModel recordingModel(List<Prompt> prompts) {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            String response = "assistant-response-" + prompts.size();
            return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
        });
        return model;
    }

    private AssistantService service(ChatModel model, SecurityUtils security) {
        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(true);

        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("chatModel", model);

        return new AssistantService(
                factory.getBeanProvider(ChatModel.class),
                mock(AssistantApplicationQueryService.class),
                mock(DashboardService.class),
                security,
                properties,
                new SimpleMeterRegistry());
    }

    private AssistantStream invokeStream(AssistantService service, UUID conversationId, String message) {
        Method method = Arrays.stream(AssistantService.class.getMethods())
                .filter(candidate -> candidate.getName().equals("stream"))
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{UUID.class, String.class}))
                .findFirst()
                .orElse(null);

        assertThat(method).as("AssistantService#stream(UUID, String)").isNotNull();

        try {
            return (AssistantStream) method.invoke(service, conversationId, message);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to invoke conversation-aware assistant stream", exception);
        }
    }

    private void consume(AssistantStream stream) {
        stream.content().collectList().block();
    }

    private List<String> promptTexts(Prompt prompt) {
        return prompt.getInstructions().stream()
                .map(Message::getText)
                .toList();
    }
}
