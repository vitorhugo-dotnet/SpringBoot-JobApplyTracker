# Passkey Login Without Email Design

## Goal

Allow users to start WebAuthn/passkey sign-in without entering an email address first, while leaving the regular email/password login flow unchanged.

## Current Behavior

The React login page blocks passkey sign-in when the email field is empty. The frontend passkey API sends `{ email }` to `POST /api/v1/auth/passkey/login/options`. The Spring backend validates that request with `@NotBlank @Email`, resolves the user by email, and starts the assertion with `StartAssertionOptions.username(...)`.

## Proposed Behavior

Use a discoverable-credential WebAuthn assertion when no email is supplied. `POST /api/v1/auth/passkey/login/options` keeps accepting the existing request shape, but `email` becomes optional. Without email, the backend calls `relyingParty.startAssertion(StartAssertionOptions.builder().timeout(...).build())`, which intentionally omits `allowCredentials` and lets the authenticator choose an eligible discoverable credential.

For backward compatibility, clients that still send an email keep the existing username-bound flow, including the `passkeyAvailable=false` response when that account has no passkey.

A username-less authentication challenge is stored without a user association. During verification, `RelyingParty.finishAssertion(...)` resolves and validates the credential through the configured `CredentialRepository`. The application then resolves the authenticated user from `AssertionResult.getUsername()` and issues the normal auth tokens.

## Backend Changes

- Make `PasskeyLoginOptionsRequest.email` optional by retaining `@Email` and removing `@NotBlank`.
- Keep `PasskeyAuthService.loginOptions(...)` compatible with the existing request DTO.
- When email is blank/absent, start an assertion without `username` and persist the challenge with `user = null`.
- When email is supplied, preserve the existing username-bound assertion and no-passkey fallback behavior.
- Keep verification and token issuance behavior unchanged.
- Add an integration test proving `{}`/no email is accepted and returns discoverable assertion options without `allowCredentials`.

## Frontend Changes

- Change `loginWithPasskey(email)` to `loginWithPasskey()` and POST an empty object to `/auth/passkey/login/options`.
- Remove the passkey-specific email validation/focus/scroll behavior from `Login.tsx`.
- Keep React Hook Form email validation on the normal password submit path.
- Add coverage proving passkey sign-in starts with the email input empty.

## Compatibility and Security

This design follows Yubico's documented username-less passkey flow: omitting the username from `StartAssertionOptions` causes the request to omit `allowCredentials`, requiring a discoverable credential. `finishAssertion` still validates the credential and returns the authenticated username. The repository already implements `lookupAll(credentialId)` and user-handle/username lookup required for discoverable authentication.

Keeping email optional rather than deleting it avoids breaking older frontend clients during rollout. No database migration is required.

## Rollout

The backend PR should be deployed before or together with the frontend PR. Older clients remain compatible because the backend continues accepting an email when supplied; the new frontend uses the username-less path by sending `{}`.
