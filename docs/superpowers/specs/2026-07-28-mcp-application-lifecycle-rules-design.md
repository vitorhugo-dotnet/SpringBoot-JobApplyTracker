# MCP Application Lifecycle Rules Design

## Problem

The MCP currently describes `Archive-Application` only as hiding a record from the default active list. That wording does not explain the business boundary between the hiring-process status and the record's visibility, so an assistant can incorrectly infer that a terminal status such as `Rejected` should also archive the application.

## Goal

Make the lifecycle semantics explicit and enforce the least-destructive mutation rule: rejection or approval changes only the application status, while archive remains an independent soft-delete operation performed only from explicit or clearly established user intent.

## Design

### 1. Dedicated lifecycle resource

Add `resource://job-apply-tracker/application-lifecycle-rules` as the authoritative Markdown reference for application status, archival, restoration, deletion, ambiguity handling, and examples.

The resource must state that:

- status and `archived` are independent concepts;
- `Rejected` and `Approved` never imply archival;
- rejection phrases, including `dar baixa` when accompanied by a rejection message, map to `Update-Application-Status` with `Rejected` and keep `archived = false`;
- archival is a soft-delete for withdrawal, abandoned/incompatible vacancies, duplicates, tests, invalid or obsolete records, or an explicit archive request;
- ambiguous archival intent must not cause archival;
- the assistant must apply the least destructive mutation;
- permanent deletion requires explicit user intent;
- archive-state changes must not be implemented by deleting and recreating the record.

The MCP server instructions will contain a concise mandatory summary and point clients to this resource.

### 2. Tool-level guardrails

Strengthen `Update-Application-Status`, `Archive-Application`, and `Delete-Application` descriptions so the rules remain visible even when a client caches or omits server-level instructions/resources.

`Update-Application-Status` must explicitly say that terminal status updates do not archive. `Archive-Application` must describe soft-delete semantics and forbid inference from rejection or approval. `Delete-Application` must require explicit permanent-deletion intent.

### 3. Restore capability

Add a dedicated `Restore-Application` MCP tool backed by `ApplicationService.restore(UUID)`. Restoring sets `archived = false` and clears `archivedAt`, preserving the current status and all other application data.

A separate tool is preferred over adding `archived` to `Update-Application` because it keeps full-record updates independent from visibility mutations and avoids accidental archive-state changes when assistants send replacement payloads.

### 4. Tests

Add focused tests for:

- lifecycle resource wording and examples;
- tool descriptions containing the mandatory safeguards;
- `Restore-Application` delegation;
- service restoration clearing both archive fields while preserving the current status;
- existing status updates leaving archive state unchanged.

## Non-goals

- Changing the meaning or stored values of existing statuses.
- Automatically archiving or restoring based on status transitions.
- Adding a REST endpoint for restore in this change.
- Changing default application-list filtering.
