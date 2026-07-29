# MCP Application Lifecycle Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent MCP clients from confusing terminal application statuses with archival and add a reversible archive workflow.

**Architecture:** Publish a dedicated lifecycle-rules MCP resource, reference it from the server instructions, reinforce the same constraints in mutation-tool descriptions, and expose restoration as a focused service method and MCP tool. Existing status updates remain independent from archive state.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring AI Community MCP annotations, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- `status` and `archived` represent independent concepts.
- Updating status to `Rejected` or `Approved` must not archive the application.
- Ambiguous archive intent must result in no archive mutation.
- Use the least destructive mutation that satisfies the request.
- Permanent deletion requires explicit user intent.
- Restoring preserves status and all non-archive fields.
- Do not add a REST restore endpoint in this change.

---

### Task 1: Publish lifecycle rules as an MCP resource

**Files:**
- Modify: `src/main/java/com/jobtracker/mcp/McpResourcesConfig.java`
- Create: `src/main/java/com/jobtracker/mcp/resources/McpApplicationLifecycleRulesResource.java`
- Create: `src/test/java/com/jobtracker/unit/mcp/McpApplicationLifecycleRulesResourceTest.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `McpResourcesConfig.URI_APPLICATION_LIFECYCLE_RULES`
- Produces: `String McpApplicationLifecycleRulesResource.applicationLifecycleRules(McpSyncServerExchange exchange)`

- [ ] **Step 1: Write the failing resource test**

Create a test that calls `applicationLifecycleRules(null)` and asserts the returned Markdown contains the independence rule, rejection mapping, `dar baixa` example, explicit archive semantics, least-destructive mutation rule, restoration behavior, and permanent-delete safeguard.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
mvn -Dtest=McpApplicationLifecycleRulesResourceTest test
```

Expected: compilation failure because `McpApplicationLifecycleRulesResource` does not exist.

- [ ] **Step 3: Add the URI constant and resource implementation**

Add:

```java
public static final String URI_APPLICATION_LIFECYCLE_RULES =
        "resource://job-apply-tracker/application-lifecycle-rules";
```

Implement the resource following the existing `McpApplicationCreationRulesResource` pattern with `text/markdown`, assistant audience, priority `1.0d`, and `LAST_MODIFIED = "2026-07-28"`.

- [ ] **Step 4: Extend the global MCP instructions**

Append a concise mandatory lifecycle summary to `spring.ai.mcp.server.instructions` and direct clients to `resource://job-apply-tracker/application-lifecycle-rules` for the full policy.

- [ ] **Step 5: Run the focused test and verify it passes**

```bash
mvn -Dtest=McpApplicationLifecycleRulesResourceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jobtracker/mcp/McpResourcesConfig.java \
  src/main/java/com/jobtracker/mcp/resources/McpApplicationLifecycleRulesResource.java \
  src/test/java/com/jobtracker/unit/mcp/McpApplicationLifecycleRulesResourceTest.java \
  src/main/resources/application.yml
git commit -m "docs(mcp): define application lifecycle rules"
```

---

### Task 2: Reinforce mutation semantics in MCP tool descriptions

**Files:**
- Modify: `src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java`
- Modify: `src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java`

**Interfaces:**
- Changes metadata only for `Update-Application-Status`, `Archive-Application`, and `Delete-Application`.

- [ ] **Step 1: Write failing annotation-description tests**

Use reflection to assert:

- `Update-Application-Status` says terminal statuses do not archive and rejection intent uses status-only mutation;
- `Archive-Application` describes soft-delete, forbids inference from `Rejected`/`Approved`, and requires explicit or clearly established archive intent;
- `Delete-Application` requires explicit permanent-deletion intent.

- [ ] **Step 2: Run the focused tests and verify they fail**

```bash
mvn -Dtest=McpApplicationToolsTest test
```

Expected: the new description assertions fail against the current short descriptions.

- [ ] **Step 3: Update the tool descriptions**

Keep the wording concise but duplicate the safety-critical rules at tool level so clients remain protected even when server instructions or resources are cached.

- [ ] **Step 4: Run the focused tests and verify they pass**

```bash
mvn -Dtest=McpApplicationToolsTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java \
  src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java
git commit -m "fix(mcp): separate status updates from archival"
```

---

### Task 3: Add reversible archive support

**Files:**
- Modify: `src/main/java/com/jobtracker/service/ApplicationService.java`
- Modify: `src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java`
- Modify: `src/test/java/com/jobtracker/unit/ApplicationServiceTest.java`
- Modify: `src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java`

**Interfaces:**
- Produces: `ApplicationResponse ApplicationService.restore(UUID id)`
- Produces: `void McpApplicationTools.restoreApplication(McpSyncRequestContext ctx, String id)` named `Restore-Application`

- [ ] **Step 1: Write failing service tests**

Add `restore_shouldRestoreApplicationWithoutChangingStatus` that starts with `archived = true`, a non-null `archivedAt`, and status `Rejected`; calls `restore(APP_UUID)`; then asserts `archived` is false, `archivedAt` is null, and status remains `Rejected`.

Add `updateStatus_shouldNotChangeArchiveState` that starts archived, updates the status, and asserts both archive fields remain unchanged.

- [ ] **Step 2: Write the failing MCP delegation test**

Add `restoreApplication_delegatesToService`, stubbing `applicationService.restore(id)` and verifying delegation.

- [ ] **Step 3: Run focused tests and verify they fail**

```bash
mvn -Dtest=ApplicationServiceTest,McpApplicationToolsTest test
```

Expected: compilation failure because `restore(UUID)` and `restoreApplication(...)` do not exist.

- [ ] **Step 4: Implement `ApplicationService.restore(UUID)`**

Load the application using the current authenticated user's ID, set `archived` to `false`, clear `archivedAt`, save, and map the response. Do not modify status or any other field.

- [ ] **Step 5: Add the `Restore-Application` MCP tool**

Expose a non-destructive, idempotent mutation tool whose description says it restores a soft-deleted record, preserves status, and must not be simulated by delete/recreate.

- [ ] **Step 6: Run focused tests and verify they pass**

```bash
mvn -Dtest=ApplicationServiceTest,McpApplicationToolsTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/jobtracker/service/ApplicationService.java \
  src/main/java/com/jobtracker/mcp/tools/McpApplicationTools.java \
  src/test/java/com/jobtracker/unit/ApplicationServiceTest.java \
  src/test/java/com/jobtracker/unit/mcp/McpApplicationToolsTest.java
git commit -m "feat(mcp): add application restore tool"
```

---

### Task 4: Verify the complete change

**Files:**
- Review all files changed in Tasks 1-3.

- [ ] **Step 1: Run the full unit test suite**

```bash
mvn test
```

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 2: Inspect the final diff**

Confirm the diff contains no automatic archive-on-status behavior, no REST endpoint, no deletion/recreation workaround, and no unrelated refactoring.

- [ ] **Step 3: Open the pull request**

Use title:

```text
fix(mcp): clarify application lifecycle and add restore support
```

The PR body must summarize the status/archive separation, lifecycle resource, tool-level guardrails, restore capability, and test coverage.
