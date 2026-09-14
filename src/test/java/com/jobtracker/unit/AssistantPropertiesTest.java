package com.jobtracker.unit;

import com.jobtracker.config.AssistantProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPropertiesTest {
    @Test
    void clampsModelProvidedLimitsAtBothBounds() {
        AssistantProperties properties = new AssistantProperties();
        properties.setDefaultSearchResults(10);
        properties.setMaxSearchResults(20);

        assertThat(properties.clampLimit(null)).isEqualTo(10);
        assertThat(properties.clampLimit(0)).isEqualTo(1);
        assertThat(properties.clampLimit(500)).isEqualTo(20);
    }
}
