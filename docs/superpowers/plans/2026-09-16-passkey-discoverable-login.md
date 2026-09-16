# Discoverable Passkey Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow WebAuthn/passkey sign-in to start without an email and resolve the account from the discoverable credential.

**Architecture:** The backend starts a username-less Yubico assertion and persists an authentication challenge without a bound user. During verification, Yubico resolves the username from the credential user handle via the existing `CredentialRepository`; the frontend therefore requests login options with an empty body and no longer validates email for the passkey button. Password login keeps its existing form validation.

**Tech Stack:** Spring Boot, Yubico WebAuthn, React, TypeScript, react-hook-form, Playwright.

**Spec:** GitHub issue `React-JobApplyTracker#109` and the approved design in chat on 2026-09-16.

## Global Constraints

- Password sign-in must continue requiring email and password.
- Passkey sign-in must not read or validate the email field.
- No database migration is required; authentication challenges may have a null user.
- Reuse the existing `CredentialRepository` mapping from user handle to username.

---

### Task 1: Backend discoverable assertion

**Files:**
- Modify: `src/main/java/com/jobtracker/controller/AuthController.java`
- Modify: `src/main/java/com/jobtracker/service/PasskeyAuthService.java`
- Delete: `src/main/java/com/jobtracker/dto/auth/PasskeyLoginOptionsRequest.java`
- Test: `src/test/java/com/jobtracker/integration/PasskeyDiscoverableLoginIT.java`

**Interfaces:**
- Produces: `POST /api/v1/auth/passkey/login/options` with no request body, returning `PasskeyOptionsResponse`.

- [x] **Step 1: Write the failing test**
- [ ] **Step 2: Verify current contract rejects the empty-email flow**
- [ ] **Step 3: Remove the login-options request DTO from the controller and service**
- [ ] **Step 4: Start assertion without `username` and persist the challenge with `user = null`**
- [ ] **Step 5: Run backend CI and confirm the integration test passes**

### Task 2: Frontend passkey flow

**Files:**
- Modify: `src/api/passkey.ts`
- Modify: `src/pages/auth/Login.tsx`
- Test: `tests/auth.spec.ts` or a focused passkey auth spec.

**Interfaces:**
- Consumes: username-less `POST /auth/passkey/login/options`.
- Produces: `loginWithPasskey(): Promise<AuthResponse>` and a passkey button independent of form email state.

- [ ] **Step 1: Add a failing test that clicks passkey with an empty email**
- [ ] **Step 2: Change `loginWithPasskey` to send `{}` and take no email argument**
- [ ] **Step 3: Remove passkey-only email validation/focus logic from `Login.tsx`**
- [ ] **Step 4: Keep regular `react-hook-form` email/password validation unchanged**
- [ ] **Step 5: Run frontend CI and confirm auth tests pass**

### Task 3: Pull requests and verification

- [ ] **Step 1: Open backend PR and link it from the frontend PR**
- [ ] **Step 2: Open frontend PR with `Fixes #109`**
- [ ] **Step 3: Review diffs for accidental scope creep**
- [ ] **Step 4: Confirm GitHub Actions status for both PR heads**
