package com.jobtracker.unit;

import com.jobtracker.config.GptFallbackAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GptFallbackAuthPropertiesTest {

    @Test
    void shouldExposeFallbackAuthDefaultsAndConfigurationState() {
        GptFallbackAuthProperties properties = new GptFallbackAuthProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getToken()).isEmpty();
        assertThat(properties.isConfigured()).isFalse();

        properties.setEnabled(true);
        properties.setToken("fallback-token");

        assertThat(properties.isConfigured()).isTrue();
    }
}
