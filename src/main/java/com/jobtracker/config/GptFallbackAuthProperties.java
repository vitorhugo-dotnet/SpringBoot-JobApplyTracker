package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gpt-fallback-auth")
public class GptFallbackAuthProperties {

    private boolean enabled = false;
    private String token = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isConfigured() {
        return enabled && token != null && !token.isBlank();
    }
}
