# Spring Boot 4.1 / Spring AI 2.0 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the backend to stable Spring Boot 4.1.x and Spring AI 2.0.x, retaining Java 21 and existing application behavior.

**Architecture:** Keep the current service, API, security, and MCP architecture. Upgrade managed dependencies and Boot-native starters, then adapt only the source/configuration surfaces that fail against the new APIs. Audit third-party libraries and deployment settings after the migration compiles.

**Tech Stack:** Java 21, Maven, Spring Boot 4.1.x, Spring Security 7.x, Spring AI 2.0.x, Jackson 3, MariaDB/JPA/Flyway.

**Spec:** `docs/superpowers/specs/2026-09-27-spring-boot-4-migration-design.md`

## Global Constraints

- Keep Java 21.
- Use stable Spring Boot 4.1.x and Spring AI 2.0.x; do not use Boot 4.2 milestones or release candidates.
- Do not retain `spring-boot-starter-classic` or Jackson 2 compatibility as the final application architecture.
- Preserve current application API, OAuth/OIDC/PKCE, MCP, OpenAPI, Actuator, Prometheus, and OpenTelemetry behavior.
- Keep MariaDB, JPA, and Flyway; do not require a paid AI provider in CI.
- Remove explicit versions already managed by Boot or Spring AI; keep third-party overrides only when compatibility requires them.

## Review Focus

- JSON responses and persisted token/claim serialization preserve current wire formats — cover with existing serialization and JWT tests.
- Authorization server endpoints, OAuth2/OIDC/PKCE, and refresh-token extensions still behave as before — cover with existing auth integration tests.
- MCP initializes and exposes expected resources/tools over WebMVC — cover with MCP auth/resource and assistant tests.
- OpenAPI groups/security schemes and Swagger UI still resolve — cover with `OpenApiDocumentationIT`.
- Actuator, rate limiting, Prometheus, and tracing configuration remain active — inspect dependency tree and startup/property binding; add focused checks only if existing coverage does not expose regressions.

---

### Task 1: Upgrade managed versions and Boot 4 modules

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: existing application dependency graph and the design's stable-version constraints.
- Produces: a Boot 4.1.x / Spring AI 2.0.x Maven model with Boot 4-native starters and correctly managed Spring Security and Authorization Server artifacts.

- [ ] Set the Boot parent to the latest stable 4.1.x release and Spring AI BOM to the latest stable 2.0.x release available at implementation time; keep `java.version` at 21.
- [ ] Replace the MVC, OAuth2 client/resource-server, AOP, Flyway, and test starters/modules with their Boot 4-native equivalents required by current code. Do not use classic compatibility starters.
- [ ] Remove the standalone Spring Authorization Server 1.5.x override and use the Security 7 generation managed by the Boot 4.1 BOM where available.
- [ ] Upgrade springdoc to stable 3.1.x and replace `resilience4j-spring-boot3` with the current stable `resilience4j-spring-boot4` artifact.
- [ ] Resolve the dependency model with `./mvnw -q -DskipTests dependency:resolve` and inspect the effective dependency tree for unavailable artifacts and duplicate Spring/Jackson generations.

### Task 2: Migrate Jackson 3 application usage

**Files:**
- Modify: Java source and tests identified by Jackson imports/usages, including `src/main/java/com/jobtracker/util/SecurityUtils.java`, `service/export/ExportConfigCodec.java`, Jackson-using DTO/controller/service files, and their focused tests.
- Modify: `pom.xml` only if a direct Jackson dependency is required after the Boot BOM migration.

**Interfaces:**
- Consumes: Jackson 3 APIs managed by Boot 4; current JSON contracts and JWT serialization behavior.
- Produces: application JSON configuration and serialization using Jackson 3, with no default Jackson 2 compatibility layer.

- [ ] Search all production and test sources for `com.fasterxml.jackson`, Jackson mapper/customizer APIs, and serialization assertions; classify imports still provided by third-party Jackson 2 dependencies.
- [ ] Update application Jackson package imports, mapper configuration, exception handling, and tests to Jackson 3 equivalents while preserving external JSON shapes.
- [ ] Keep a Jackson 2 compatibility module only if a concrete third-party dependency requires it; isolate it to that boundary and document the reason in the POM.
- [ ] Run focused mapper, export codec, JWT, controller serialization, and Spring context tests.

### Task 3: Migrate Security 7 and authorization-server configuration

**Files:**
- Modify: `src/main/java/com/jobtracker/config/SecurityConfig.java`, `AuthorizationServerConfig.java`, `CimdRegisteredClientRepository.java`, `PublicClientRefreshToken*.java`, `OAuth*MetadataController.java`, `DynamicClientRegistrationController.java`, and other compile-identified security classes.
- Modify: related auth integration tests under `src/test/java/com/jobtracker/integration/`.

**Interfaces:**
- Consumes: Security 7 and Boot-managed authorization-server APIs.
- Produces: existing OAuth2/OIDC/PKCE endpoints, JWT resource-server validation, client registration, and custom refresh-token behavior on Boot 4.

- [ ] Fix compile errors from Security 7 API and package changes with minimal source edits; preserve filter-chain ordering and authorization rules.
- [ ] Adapt authorization-server extensions to the supported Security 7 APIs without retaining the old separately versioned server implementation.
- [ ] Run targeted authorization-server, PKCE/OIDC, resource-server, passkey, and rate-limit integration tests.

### Task 4: Migrate Spring AI 2 and MCP integrations

**Files:**
- Modify: `src/main/java/com/jobtracker/service/assistant/**`, `config/AssistantToolCallingConfiguration.java`, `mcp/**`, and AI configuration in `src/main/resources/application.yml`.
- Modify: assistant/MCP tests under `src/test/java/com/jobtracker/unit/` and `integration/`.

**Interfaces:**
- Consumes: Spring AI 2.0 chat/tool APIs, Google GenAI starter, and Spring AI-managed MCP WebMVC transport.
- Produces: existing SSE assistant behavior, tool registration/calls, MCP resources/prompts/tools, and provider-disabled test startup.

- [ ] Follow Spring AI 2 upgrade notes for MCP artifact/package changes, WebMVC Streamable HTTP transport, Google GenAI model configuration, and chat/tool API changes.
- [ ] Adapt assistant streaming/error handling and tool registration with no externally visible API change.
- [ ] Preserve provider-disabled test profile behavior and use stubs/mocks for provider-specific tests.
- [ ] Run focused assistant service/controller/tool and MCP auth/resource tests.

### Task 5: Audit direct dependencies, configuration, docs, and end-to-end verification

**Files:**
- Modify: `pom.xml`, `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, and deployment/documentation files only where version or configuration changes require it.
- Test: focused integration tests and the issue-required Maven verification commands.

**Interfaces:**
- Consumes: compiling Boot 4 application and selected Boot/Spring AI BOMs.
- Produces: audited compatible dependency set and verified Boot 4 runtime configuration.

- [ ] Audit all explicit direct dependency versions and Maven plugins in `pom.xml`; update stable compatible versions and remove BOM-managed overrides. Retain pinned versions only with a compatibility reason.
- [ ] Review Flyway, Jackson, mail/web, Actuator/Micrometer/OTLP, Resilience4j, and Spring AI properties across application/test/deployment configuration; remove renamed or obsolete keys.
- [ ] Update README/deployment guidance only where Java, dependency, or configuration requirements changed.
- [ ] Run the focused tests for serialization, security, assistant/MCP, OpenAPI, and configuration.
- [ ] Run `./mvnw clean verify` and `./mvnw dependency:tree`; confirm Java 21 build, AOT execution, no accidental duplicate Spring/Jackson generations, and successful normal-profile application startup if local services/configuration permit.

## Self-Review

- Spec coverage: dependency baseline, starters, Jackson, Security/Authorization Server, Spring AI/MCP, OpenAPI, Resilience4j, dependency audit, configuration, docs, and verification each have an owning task.
- Step scan: each checkbox identifies one bounded migration or verification action and its responsible files.
- Type/API consistency: Maven model outputs feed the Jackson, Security, and Spring AI source migration tasks; final audit runs only after those compile.
- Review focus: the five highest-risk behavior areas are paired with existing tests or an explicit targeted inspection.
- Proportion: the plan groups by migration boundary and avoids enumerating every Jackson-using file before compiler/search evidence identifies which ones actually change.
