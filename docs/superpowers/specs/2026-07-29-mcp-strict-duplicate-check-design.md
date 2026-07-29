# MCP Strict Duplicate Check Design

## Problem

The MCP currently requires application registration and mentions duplicate detection, but the wording is not strict enough to ensure that clients search thoroughly before calling `Create-Application`.

A client can perform one weak or empty search, treat the absence of results as proof that no application exists, and create a duplicate. This is especially risky because `vacancyLink` is optional and `Search-Applications` does not search the link field directly.

## Goal

Strengthen MCP instructions so every client must actively search for confirmed and possible duplicates before creating an application.

This change is instruction-only. It does not add backend duplicate enforcement, database constraints, URL normalization, new tools, or service logic.

## Mandatory Duplicate-Check Workflow

Before every `Create-Application` call, the client must complete all applicable checks below.

1. Extract the available identifying fields:
   - vacancy URL;
   - vacancy title;
   - organization;
   - recruiter name.
2. Search active applications separately using the available title, organization, and recruiter terms.
3. Search archived applications as well. Archived records must participate in duplicate detection and must not be ignored merely because they are hidden from the default active list.
4. Inspect returned records and compare all available identifying fields, including `vacancyLink` when present.
5. Do not treat one empty or weak search result as sufficient evidence that no duplicate exists.
6. Classify matching records as:
   - **confirmed duplicate** when the URL matches, or when title, organization, and recruiter identify the same vacancy with high confidence;
   - **possible duplicate** when title and organization match but the remaining information is missing, different, or inconclusive.
7. For a confirmed duplicate, reuse the existing record and do not call `Create-Application`.
8. For a possible duplicate, do not create immediately. Show the matching records and ask the user to confirm whether the vacancy is distinct.
9. Call `Create-Application` only after explicitly concluding that no confirmed or possible duplicate remains unresolved.

When the matching record is archived, the client must identify it as archived and prefer `Restore-Application` when the user intends to continue using the same application record.

## Instruction Locations

Repeat the safety-critical rules in three places so clients still receive them when server instructions or resources are cached or partially loaded:

1. `McpApplicationCreationRulesResource`
2. the `intake_vacancy` prompt in `McpPromptsConfig`
3. the `Create-Application` tool description in `McpApplicationTools`

The wording must explicitly include these concepts:

- search before creating;
- search active and archived applications;
- search by multiple identifying fields rather than relying only on the URL;
- an empty search result is not sufficient when the search was incomplete or weak;
- possible duplicates require user confirmation;
- `Create-Application` is allowed only after duplicate checks are complete.

## Testing

Add focused tests that inspect the resource text, prompt text, and tool annotation description.

Tests must assert the presence of language equivalent to:

- `must search before creating`;
- `active and archived`;
- `possible duplicate`;
- `an empty search result is not sufficient`;
- `do not call Create-Application until` the duplicate check is complete.

Tests should also verify that the instructions do not claim URL equality is the only duplicate criterion.

## Non-goals

- No database unique constraint.
- No duplicate-detection service.
- No URL-normalization implementation.
- No new MCP tool.
- No change to `ApplicationService.create`.
- No automatic blocking performed by the backend.
- No changes to application archive semantics.

## Success Criteria

The final MCP metadata and prompt must make the required order unambiguous:

`extract identifiers -> search active applications -> search archived applications -> inspect likely matches -> resolve possible duplicates -> Create-Application only when safe`

A client following the instructions must not be able to justify creation based on a single empty search when other identifying fields or archived records have not been checked.
