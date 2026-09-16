# Passkey Login Without Email Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow discoverable passkey authentication to start without an email while preserving email/password validation.

**Architecture:** The backend owns the WebAuthn ceremony and will start authentication without a username, producing request options without an `allowCredentials` restriction. The frontend becomes a thin caller that starts this ceremony directly. Verification remains server-side and resolves the authenticated user from the validated assertion result.

**Tech Stack:** Spring Boot, Java 21+, Yubico java-webauthn-server 2.9.0, React, TypeScript, React Hook Form, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-16-passkey-login-without-email-design.md`

## Global Constraints

- Regular email/password login must continue requiring email and password.
- Passkey login must not validate or require email in the frontend.
- Passkey options must use a username-less discoverable-credential assertion.
- No database migration.
- Backend must be compatible before the frontend stops sending email.

---

### Task 1: Backend contract and discoverable assertion

**Files:**
- Modify: `src/test/java/com/jobtracker/integration/AuthControllerIT.java`
- Modify: `src/main/java/com/jobtracker/controller/AuthController.java`
- Modify: `src/main/java/com/jobtracker/service/PasskeyAuthService.java`
- Delete: `src/main/java/com/jobtracker/dto/auth/PasskeyLoginOptionsRequest.java`

**Interfaces:**
- Produces: `POST /api/v1/auth/passkey/login/options` accepting `{}` and returning `PasskeyOptionsResponse`.
- Produces: `PasskeyAuthService.loginOptions()` with no request parameter.

- [ ] **Step 1: Write the failing integration test**

Replace the email-dependent fallback test with a test that calls `/api/v1/auth/passkey/login/options` using `{}` and expects `200`, `passkeyAvailable=true`, a non-empty `challengeId`, and a `publicKey.challenge`.

- [ ] **Step 2: Verify RED in GitHub Actions**

Push only the test change and confirm the backend workflow fails because the current controller requires a valid email body.

- [ ] **Step 3: Implement the minimal backend change**

Remove `PasskeyLoginOptionsRequest` from the controller and service contract. Start the assertion using:

```java
AssertionRequest assertionRequest = relyingParty.startAssertion(
        StartAssertionOptions.builder()
                .timeout(webAuthnProperties.challengeTimeoutSeconds() * 1000L)
                .build()
);
```

Persist the authentication challenge with `user = null` and return the generated request options.

- [ ] **Step 4: Verify GREEN in GitHub Actions**

Confirm the new integration test and the existing backend suite pass.

### Task 2: Frontend passkey flow without email

**Files:**
- Modify: `tests/auth.spec.ts`
- Modify: `src/api/passkey.ts`
- Modify: `src/pages/auth/Login.tsx`
- Modify as needed: `tests/support/*` only for WebAuthn/network test fixtures.

**Interfaces:**
- Consumes: backend `POST /api/v1/auth/passkey/login/options` accepting `{}`.
- Produces: `loginWithPasskey(): Promise<AuthResponse | null>`.

- [ ] **Step 1: Write the failing Playwright test**

Add an auth test that leaves Email empty, clicks `Sign in with a passkey`, and proves the passkey options request is initiated rather than showing the old `Enter your email address...` validation error.

- [ ] **Step 2: Verify RED in GitHub Actions**

Push only the frontend test change and confirm the workflow fails because the current UI blocks before the passkey request.

- [ ] **Step 3: Implement the minimal frontend change**

Change `loginWithPasskey(email: string)` to `loginWithPasskey()`, POST `{}` to `/auth/passkey/login/options`, and remove `getValues`, `emailRef`, and passkey-specific email validation from `Login.tsx`. Keep `register('email', { required: 'Email is required' })` for the password form.

- [ ] **Step 4: Verify GREEN in GitHub Actions**

Confirm auth tests and the complete frontend workflow pass.

### Task 3: PR verification and cross-repo linkage

**Files:** none.

**Interfaces:**
- Backend PR documents the API contract change.
- Frontend PR closes `React-JobApplyTracker#109` and references the backend dependency.

- [ ] **Step 1: Inspect both PR diffs**

Confirm no unrelated refactors or generated artifacts are included.

- [ ] **Step 2: Confirm final workflow status**

Check GitHub Actions for both PR head SHAs and require passing workflows before reporting completion.

- [ ] **Step 3: Link the PRs**

Backend PR: `Related to vitorhugo-dotnet/React-JobApplyTracker#109`.

Frontend PR: `Fixes #109` and include a dependency link to the backend PR.
