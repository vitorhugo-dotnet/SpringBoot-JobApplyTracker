package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {
    private boolean enabled;
    private int maxMessageLength = 4000;
    private int defaultSearchResults = 10;
    private int maxSearchResults = 20;
    private Duration streamTimeout = Duration.ofSeconds(60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public void setMaxMessageLength(int value) { this.maxMessageLength = value; }
    public int getDefaultSearchResults() { return defaultSearchResults; }
    public void setDefaultSearchResults(int value) { this.defaultSearchResults = value; }
    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int value) { this.maxSearchResults = value; }
    public Duration getStreamTimeout() { return streamTimeout; }
    public void setStreamTimeout(Duration value) { this.streamTimeout = value; }

    public int clampLimit(Integer requested) {
        int value = requested == null ? defaultSearchResults : requested;
        return Math.max(1, Math.min(value, maxSearchResults));
    }
}
