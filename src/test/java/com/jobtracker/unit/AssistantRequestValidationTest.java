package com.jobtracker.unit;

import com.jobtracker.config.AssistantProperties;
import com.jobtracker.controller.AssistantController;
import com.jobtracker.exception.GlobalExceptionHandler;
import com.jobtracker.service.assistant.AssistantProviderErrorMapper;
import com.jobtracker.service.assistant.AssistantService;
import com.jobtracker.service.assistant.AssistantService.AssistantStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AssistantRequestValidationTest {

    private AssistantService assistant;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assistant = mock(AssistantService.class);
        AssistantProviderErrorMapper errorMapper = mock(AssistantProviderErrorMapper.class);
        AssistantProperties properties = new AssistantProperties();

        mockMvc = standaloneSetup(new AssistantController(assistant, properties, errorMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingConversationIdReturnsStructuredProblemBeforeSseStarts() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(
                                MediaType.TEXT_EVENT_STREAM,
                                MediaType.APPLICATION_PROBLEM_JSON,
                                MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.conversationId").value("Conversation ID is required"));

        verifyNoInteractions(assistant);
    }

    @Test
    void malformedConversationIdReturnsStructuredBadRequestBeforeSseStarts() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(
                                MediaType.TEXT_EVENT_STREAM,
                                MediaType.APPLICATION_PROBLEM_JSON,
                                MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "definitely-not-a-uuid",
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));

        verifyNoInteractions(assistant);
    }

    @Test
    void blankMessageReturnsStructuredProblemBeforeSseStarts() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(
                                MediaType.TEXT_EVENT_STREAM,
                                MediaType.APPLICATION_PROBLEM_JSON,
                                MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "5c970c0e-d6b9-4e3c-8120-f8d496d654a5",
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.message").value("Message is required"));

        verifyNoInteractions(assistant);
    }

    @Test
    void validRequestStillNegotiatesSse() throws Exception {
        when(assistant.stream(any(UUID.class), eq("hello")))
                .thenReturn(new AssistantStream(Flux.just("hello"), Set::of));

        MvcResult result = mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(
                                MediaType.TEXT_EVENT_STREAM,
                                MediaType.APPLICATION_PROBLEM_JSON,
                                MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "5c970c0e-d6b9-4e3c-8120-f8d496d654a5",
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000);
        assertThat(result.getResponse().getContentAsString()).contains("event:token");
    }
}
