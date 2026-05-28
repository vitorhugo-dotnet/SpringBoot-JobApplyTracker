package com.jobtracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String CONTROLLER_PACKAGE = "com.jobtracker.controller";

    @Bean
    public OpenAPI openAPI(@Value("${app.api.base-url:https://jobapply-api.hugojava.dev}") String apiBaseUrl) {
        return new OpenAPI()
                .servers(List.of(new Server().url(apiBaseUrl).description("Production")))
                .info(new Info()
                        .title("JobApply API")
                        .description("API for tracking job applications")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi applicationsOpenApi() {
        return GroupedOpenApi.builder()
                .group("applications")
                .displayName("Application API")
                .packagesToScan(CONTROLLER_PACKAGE)
                .pathsToMatch("/api/v1/applications/**", "/api/v1/applications")
                .build();
    }

    @Bean
    public GroupedOpenApi googleDriveOpenApi() {
        return GroupedOpenApi.builder()
                .group("google-drive")
                .displayName("Google Drive API")
                .packagesToScan(CONTROLLER_PACKAGE)
                .pathsToMatch("/api/v1/google-drive/**", "/api/v1/google-drive")
                .build();
    }
}
