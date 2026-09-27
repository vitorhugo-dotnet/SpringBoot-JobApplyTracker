# Spring Boot 4.1 and Spring AI 2.0 Migration Design

## Goal

Migrate the backend from Spring Boot 3.5.0 to stable Spring Boot 4.1.x and Spring AI 2.0.x while retaining Java 21 and the behavior of existing application, security, assistant/MCP, API documentation, and observability features.

## Scope

- Upgrade Spring Boot to the stable 4.1.x release selected for implementation and Spring AI to the latest stable 2.0.x release.
- Use Boot 4-native starters and modules for MVC, OAuth2, AOP, Flyway, testing, and other dependencies where the project needs them.
- Move application Jackson integration to Jackson 3; keep Jackson 2 only where a third-party library requires it, without making its compatibility module the application default.
- Align Spring Security and authorization-server configuration with the Boot 4 / Security 7 generation, preserving OAuth2, OIDC, PKCE, registration, and token behavior.
- Adapt Spring AI assistant, Google GenAI configuration, tool calling, and MCP WebMVC server code to Spring AI 2 APIs and artifacts.
- Upgrade springdoc to a Boot 4-compatible 3.x release and Resilience4j to its Spring Boot 4 integration.
- Audit explicitly versioned direct dependencies and Maven plugins; remove versions already owned by the Boot or Spring AI BOMs and refresh other versions only when compatible.
- Audit application and test configuration, deployment configuration, and dependency documentation for changed properties or requirements.

## Constraints

- Keep Java 21.
- Do not use Spring Boot 4.2 milestones or release candidates.
- Do not retain `spring-boot-starter-classic` or Jackson 2 compatibility as the final application architecture.
- Preserve MariaDB, JPA, and Flyway; do not add AI provider credentials to CI requirements.
- Keep changes limited to compatibility and maintenance work required by this migration.

## Approach

Update dependency management and artifacts first, then migrate compile-time API changes in source, then audit configuration and deployment documentation. Keep the existing application architecture and public API. Resolve compatibility failures at the narrowest responsible boundary, and retain regression coverage around affected security, assistant/MCP, serialization, OpenAPI, and observability behavior.

## Verification

Verify Maven dependency resolution and compilation, then run focused tests for modified behavior and relevant existing security, assistant/MCP, serialization, and OpenAPI flows. Run the issue's full `./mvnw clean verify` and dependency-tree checks when the migration compiles. Review Java 21 compatibility, dependency convergence (especially Spring and Jackson generations), and normal application startup/configuration.

## Current Code Areas Identified

- `pom.xml` owns the Boot parent, Spring AI BOM, explicit library versions, starters, and Maven plugins.
- `src/main/resources/application.yml` and `src/test/resources/application-test.yml` carry AI, actuator, Resilience4j, and test-profile properties.
- Security configuration is centered in `config/SecurityConfig.java`, `config/AuthorizationServerConfig.java`, and adjacent `config/*OAuth*` classes.
- Assistant and MCP APIs are used in `service/assistant/*`, `config/AssistantToolCallingConfiguration.java`, and `mcp/*`.
- Jackson APIs appear in utility, DTO, service, configuration, and related test files; the exact migration set will be determined by compiler/dependency evidence.
- OpenAPI and observability are configured through `config/OpenApiConfig.java`, application properties, and existing integration tests.

## Risks and Mitigations

- Boot 4 modularization can break dependencies that relied on transitive modules. Declare only the Boot 4 modules required by the code and let the Boot BOM manage their versions.
- Jackson 3 package and exception changes may affect multiple serialized DTO and provider paths. Compile and exercise representative serialization and assistant/provider tests after migrating imports.
- Spring Security 7 and authorization-server API changes may affect protocol behavior. Preserve existing authorization-server configuration and use existing OAuth/OIDC/PKCE integration tests as regression checks.
- Spring AI 2 changes MCP transports and tool APIs. Validate both server startup/discovery and assistant tool calling without relying on paid external providers.
- Other explicitly pinned libraries may not yet support the target ecosystem. Keep compatible versions when required and document any justified override rather than forcing unrelated major upgrades.
