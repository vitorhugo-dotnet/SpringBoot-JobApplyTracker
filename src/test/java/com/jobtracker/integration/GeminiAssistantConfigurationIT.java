package com.jobtracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=test-only-not-a-real-key",
        "spring.ai.google.genai.chat.options.model=gemini-2.5-flash"
})
@ActiveProfiles("test")
class GeminiAssistantConfigurationIT {

    @Autowired
    private ChatModel chatModel;

    @Test
    void configuresGoogleAiStudioAsTheAssistantChatProvider() {
        assertThat(chatModel.getClass().getSimpleName()).isEqualTo("GoogleGenAiChatModel");
    }
}
