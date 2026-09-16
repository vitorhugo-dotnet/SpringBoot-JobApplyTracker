# Passkey Login Without Email Design

## Goal

Allow users to start WebAuthn/passkey sign-in without entering an email address first, while leaving the regular email/password login flow unchanged.

## Current Behavior

The React login page blocks passkey sign-in when the email field is empty. The frontend passkey API sends `{ email }` to `POST /api/v1/auth/passkey/login/options`. The Spring backend validates that request with `@NotBlank @Email`, resolves the user by email, and starts the assertion with `StartAssertionOptions.username(...)`.

## Proposed Behavior

Use a discoverable-credential WebAuthn assertion. `POST /api/v1/auth/passkey/login/options` will no longer require a username/email. The backend will call `relyingParty.startAssertion(StartAssertionOptions.builder().timeout(...).build())`, which intentionally omits `allowCredentials` and lets the authenticator choose an eligible discoverable credential.

The authentication challenge will be stored without a user association. During verification, `RelyingParty.finishAssertion(...)` resolves and validates the credential through the configured `CredentialRepository`. The application will then resolve the authenticated user from `AssertionResult.getUsername()` and issue the normal auth tokens.

## Backend Changes

- Remove `PasskeyLoginOptionsRequest` from the login-options endpoint contract.
- Change `PasskeyAuthService.loginOptions(...)` to take no arguments.
- Start an assertion without `username`.
- Persist the authentication challenge with `user = null`; the existing schema already allows a null `user_id`.
- Keep verification and token issuance behavior unchanged except that the challenge is not pre-bound to a user.
- Replace the existing email-based fallback integration test with a test proving `{}`/no email is accepted and returns assertion options.

## Frontend Changes

- Change `loginWithPasskey(email)` to `loginWithPasskey()` and POST an empty object to `/auth/passkey/login/options`.
- Remove the passkey-specific email validation/focus/scroll behavior from `Login.tsx`.
- Keep React Hook Form email validation on the normal password submit path.
- Add coverage proving passkey sign-in starts with the email input empty.

## Compatibility and Security

This design follows Yubico's documented username-less passkey flow: omitting the username from `StartAssertionOptions` causes the request to omit `allowCredentials`, requiring a discoverable credential. `finishAssertion` still validates the credential and returns the authenticated username. The repository already implements `lookupAll(credentialId)` and user-handle/username lookup required for discoverable authentication.

No database migration is required.

## Rollout

The backend PR must be deployed before or together with the frontend PR because the frontend will stop sending the email field to the passkey options endpoint.
