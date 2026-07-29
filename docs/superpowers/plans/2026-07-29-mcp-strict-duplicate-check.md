# MCP Strict Duplicate Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCP clients search active and archived applications for confirmed or possible duplicates before every `Create-Application` call.

**Architecture:** This is an instruction-only change. Strengthen the same mandatory duplicate-check workflow in the application-creation resource, the `intake_vacancy` prompt, and the `Create-Application` tool description, then lock the wording with focused reflection/text tests.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring AI Community MCP annotations, JUnit 5, AssertJ, Maven.

## Global Constraints

- Do not add backend duplicate enforcement.
- Do not add database constraints or migrations.
- Do not normalize URLs.
- Do not add MCP tools or service methods.
- Do not change `ApplicationService.create`.
- Duplicate checks must include active and archived applications.
- Duplicate checks must use all available identifiers: URL, vacancy title, organization, and recruiter.
- A single empty or weak search is not sufficient evidence that no duplicate exists.
- Confirmed duplicates reuse the existing record.
- Possible duplicates require user confirmation before creation.

---

### Task 1: Define strict duplicate-check expectations in tests

**Files:**
- Modify: `src/test/java/com/jobtracker/unit/mcp/McpApplicationCreationRulesResourceTest.java`
- Modify: `src/test/java/com/jobtracker/unit/mcp/McpPromptsConfigTest.java`
- Modify: `src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java`

**Interfaces:**
- Consumes: `String McpApplicationCreationRulesResource.applicationCreationRules(McpSyncServerExchange exchange)`
- Consumes: `GetPromptResult McpPromptsConfig.intakeVacancyPrompt(String vacancyContent)`
- Consumes: `@McpTool.description()` on `McpApplicationTools.createApplication(...)`
- Produces: executable wording requirements for the three MCP instruction surfaces

- [ ] **Step 1: Replace the resource assertion with strict multi-field duplicate-check expectations**

Update `applicationCreationRules_mandatesRegistrationForEveryApplicationRelatedAction` so the assertion is:

```java
assertThat(text)
        .containsIgnoringCase("must search before creating")
        .containsIgnoringCase("active and archived")
        .containsIgnoringCase("vacancy URL")
        .containsIgnoringCase("vacancy title")
        .containsIgnoringCase("organization")
        .containsIgnoringCase("recruiter")
        .containsIgnoringCase("possible duplicate")
        .containsIgnoringCase("an empty search result is not sufficient")
        .containsIgnoringCase("do not call Create-Application until");
```

Also assert the old URL-only rule is absent:

```java
assertThat(text)
        .doesNotContain("duplicate only when the URL is identical")
        .doesNotContain("Reposts or new vacancy URLs must be registered as separate applications");
```

- [ ] **Step 2: Replace the prompt test with strict active-and-archived workflow expectations**

Rename the test to `intakeVacancyPrompt_enforcesStrictDuplicateCheckBeforeCreation` and assert:

```java
assertThat(text)
        .containsIgnoringCase("must search before creating")
        .containsIgnoringCase("active applications")
        .containsIgnoringCase("archived applications")
        .containsIgnoringCase("vacancy title")
        .containsIgnoringCase("organization")
        .containsIgnoringCase("recruiter")
        .containsIgnoringCase("possible duplicate")
        .containsIgnoringCase("an empty search result is not sufficient")
        .containsIgnoringCase("do not call Create-Application until");
```

Also assert:

```java
assertThat(text)
        .doesNotContain("Search by exact vacancy URL → Create-Application when absent")
        .doesNotContain("Similar vacancy names, recruiters, organizations, salaries, or technology stacks are NOT sufficient");
```

- [ ] **Step 3: Strengthen the `Create-Application` description test**

Replace the final URL-only assertion in `createApplicationTool_descriptionMandatesAutomaticRegistration` with:

```java
assertThat(description)
        .contains("must be called automatically")
        .contains("applying")
        .contains("generating a resume")
        .contains("contacting a recruiter")
        .contains("evaluating job fit")
        .contains("preparing application materials")
        .containsIgnoringCase("must search before creating")
        .containsIgnoringCase("active and archived")
        .containsIgnoringCase("possible duplicate")
        .containsIgnoringCase("an empty search result is not sufficient")
        .containsIgnoringCase("do not call Create-Application until");
```

- [ ] **Step 4: Run focused tests and verify they fail against the current wording**

Run:

```bash
mvn -Dtest=McpApplicationCreationRulesResourceTest,McpPromptsConfigTest,McpApplicationToolsTest test
```

Expected: the three new duplicate-check assertions fail because current instructions prioritize exact URL matching and do not require archived searches or possible-duplicate confirmation.

- [ ] **Step 5: Commit the failing tests**

```bash
git add src/test/java/com/jobtracker/unit/mcp/McpApplicationCreationRulesResourceTest.java \
  src/test/java/com/jobtracker/unit/mcp/McpPromptsConfigTest.java \
  src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java
git commit -m "test(mcp): require strict duplicate checks before creation"
```

---

### Task 2: Strengthen the application-creation resource

**Files:**
- Modify: `src/main/java/com/jobtracker/mcp/resources/McpApplicationCreationRulesResource.java`
- Test: `src/test/java/com/jobtracker/unit/mcp/McpApplicationCreationRulesResourceTest.java`

**Interfaces:**
- Produces: authoritative Markdown duplicate-check workflow from `applicationCreationRules(...)`
- Consumed by: MCP clients reading `resource://job-apply-tracker/application-creation-rules`

- [ ] **Step 1: Replace the URL-only duplicate section**

Replace the current duplicate paragraphs with:

```text
MANDATORY DUPLICATE CHECK: you MUST search before creating any application.
Extract every available identifier: vacancy URL, vacancy title, organization, and recruiter.
Search active applications separately using the available title, organization, and recruiter terms.
Then search archived applications as well; archived records MUST participate in duplicate detection.
Inspect all returned records and compare every available identifier, including vacancyLink when present.

An empty search result is not sufficient when only one weak, incomplete, or unrelated query was executed.
Do not rely only on vacancyLink because it is optional. Run multiple searches using the available identifiers.

Treat a matching record as a confirmed duplicate when the URL matches or when title, organization, and recruiter identify the same vacancy with high confidence. Reuse the existing record and do not call Create-Application.
Treat a matching record as a possible duplicate when title and organization match but other information is missing, different, or inconclusive. Show the matching records and ask the user to confirm whether the vacancy is distinct.

Do not call Create-Application until searches of active and archived applications are complete and no confirmed or possible duplicate remains unresolved. When the matching record is archived, identify it as archived and prefer Restore-Application when the user wants to continue the same application record.
```

Keep the existing registration-before-resume rule and field defaults unchanged.

- [ ] **Step 2: Update the resource freshness date**

Set:

```java
private static final String LAST_MODIFIED = "2026-07-29";
```

- [ ] **Step 3: Run the resource test**

```bash
mvn -Dtest=McpApplicationCreationRulesResourceTest test
```

Expected: PASS.

- [ ] **Step 4: Commit the resource change**

```bash
git add src/main/java/com/jobtracker/mcp/resources/McpApplicationCreationRulesResource.java
git commit -m "docs(mcp): require strict duplicate checks before creation"
```

---

### Task 3: Strengthen the vacancy-intake prompt

**Files:**
- Modify: `src/main/java/com/jobtracker/mcp/McpPromptsConfig.java`
- Test: `src/test/java/com/jobtracker/unit/mcp/McpPromptsConfigTest.java`

**Interfaces:**
- Produces: mandatory execution order inside `intakeVacancyPrompt(String vacancyContent)`
- Consumed by: MCP clients invoking the `intake_vacancy` prompt

- [ ] **Step 1: Replace the registration workflow summary**

Use this summary:

```text
MANDATORY REGISTRATION WORKFLOW:
Extract identifiers → Search active applications → Search archived applications → Inspect confirmed and possible duplicates → Resolve possible duplicates with the user → Create-Application only when safe → Continue requested workflow
```

- [ ] **Step 2: Replace Step 2 with an explicit duplicate-check algorithm**

Use wording equivalent to:

```text
STEP 2 - Check for confirmed and possible duplicates (mandatory, do not skip)
Call List-Statuses to obtain valid status values.
You MUST search before creating. Extract and use every available identifier: vacancy URL, vacancy title, organization, and recruiter.
Search active applications separately using available title, organization, and recruiter terms. Then repeat the search for archived applications by setting archived=true in List-Applications where applicable.
Inspect the returned records and compare all available identifiers, including vacancyLink when present. Do not rely only on vacancyLink because it is optional.
An empty search result is not sufficient when the search was weak, incomplete, or used only one identifier.
A confirmed duplicate is an existing record with the same URL, or a record whose title, organization, and recruiter identify the same vacancy with high confidence. Reuse it and do not call Create-Application.
A possible duplicate is a record whose title and organization match while other information is missing, different, or inconclusive. Show the matching records and ask the user to confirm whether the vacancy is distinct.
Do not call Create-Application until active and archived searches are complete and no confirmed or possible duplicate remains unresolved.
If the matching record is archived, report that state and prefer Restore-Application when the user intends to continue the same record.
```

Leave subsequent CV/base-information steps unchanged.

- [ ] **Step 3: Run the prompt test**

```bash
mvn -Dtest=McpPromptsConfigTest test
```

Expected: PASS.

- [ ] **Step 4: Commit the prompt change**

```bash
git add src/main/java/com/jobtracker/mcp/McpPromptsConfig.java
git commit -m "docs(mcp): make vacancy duplicate checks mandatory"
```

---

### Task 4: Strengthen the `Create-Application` tool description

**Files:**
- Modify: `src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java`
- Test: `src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java`

**Interfaces:**
- Produces: safety-critical `@McpTool.description()` metadata for `Create-Application`
- Consumed by: MCP clients even when prompt/resource instructions are cached or omitted

- [ ] **Step 1: Replace the description with compact mandatory guardrails**

Use:

```java
description = "Create a new job application record. IMPORTANT: Call List-Statuses first to get valid status values. Never use a status value from memory. "
        + "This tool must be called automatically whenever the user provides a vacancy in the context of applying, generating a resume, contacting a recruiter, evaluating job fit, or preparing application materials. "
        + "Before calling it, you MUST search before creating: search active and archived applications using every available identifier, including vacancy URL, vacancy title, organization, and recruiter. "
        + "Do not rely only on the URL because vacancyLink is optional. An empty search result is not sufficient when the search was incomplete or weak. "
        + "Reuse confirmed duplicates. For a possible duplicate, show the matching records and obtain user confirmation that the vacancy is distinct. "
        + "Do not call Create-Application until all duplicate checks are complete and no confirmed or possible duplicate remains unresolved."
```

Do not change method parameters, annotations, or delegation logic.

- [ ] **Step 2: Run the tool test**

```bash
mvn -Dtest=McpApplicationToolsTest test
```

Expected: PASS.

- [ ] **Step 3: Commit the tool metadata change**

```bash
git add src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java
git commit -m "fix(mcp): guard application creation against duplicates"
```

---

### Task 5: Verify and open the pull request

**Files:**
- Review: all files changed on `fix/mcp-strict-duplicate-check`

**Interfaces:**
- Consumes: completed Tasks 1-4
- Produces: reviewed pull request targeting `main`

- [ ] **Step 1: Run the focused MCP tests**

```bash
mvn -Dtest=McpApplicationCreationRulesResourceTest,McpPromptsConfigTest,McpApplicationToolsTest test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run the complete test suite**

```bash
mvn test
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Inspect the final diff**

Confirm the branch changes only:

```text
docs/superpowers/specs/2026-07-29-mcp-strict-duplicate-check-design.md
docs/superpowers/plans/2026-07-29-mcp-strict-duplicate-check.md
src/main/java/com/jobtracker/mcp/resources/McpApplicationCreationRulesResource.java
src/main/java/com/jobtracker/mcp/McpPromptsConfig.java
src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java
src/test/java/com/jobtracker/unit/mcp/McpApplicationCreationRulesResourceTest.java
src/test/java/com/jobtracker/unit/mcp/McpPromptsConfigTest.java
src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java
```

Reject any database migration, repository/service duplicate logic, URL-normalization utility, or new MCP tool.

- [ ] **Step 4: Open the pull request**

Title:

```text
fix(mcp): require duplicate checks before application creation
```

Body:

```markdown
## Summary

- requires MCP clients to search active and archived applications before `Create-Application`
- uses URL, vacancy title, organization, and recruiter instead of relying only on an optional link
- distinguishes confirmed duplicates from possible duplicates that require user confirmation
- repeats the guardrails in the creation resource, intake prompt, and tool description
- adds focused tests for the mandatory wording

## Scope

Instruction and metadata changes only. No database constraint, URL normalization, new MCP tool, or backend duplicate-enforcement logic.

## Tests

- `mvn -Dtest=McpApplicationCreationRulesResourceTest,McpPromptsConfigTest,McpApplicationToolsTest test`
- `mvn test`
```
