package com.jobtracker.unit;

import com.jobtracker.config.AssistantProperties;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ServiceUnavailableException;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.assistant.AssistantApplicationQueryService;
import com.jobtracker.service.assistant.AssistantService;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AssistantServiceTest {
    @Test
    void rejectsBlankMessagesBeforeCallingAProvider() {
        AssistantService service = service(new AssistantProperties());
        assertThatThrownBy(() -> service.stream(UUID.randomUUID(), " "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void failsSafelyWhenDisabled() {
        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(false);
        AssistantService service = service(properties);
        assertThatThrownBy(() -> service.stream(UUID.randomUUID(), "How many applications?"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private AssistantService service(AssistantProperties properties) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        return new AssistantService(factory.getBeanProvider(ChatModel.class),
                mock(AssistantApplicationQueryService.class), mock(DashboardService.class),
                mock(SecurityUtils.class), properties, new SimpleMeterRegistry());
    }
}
