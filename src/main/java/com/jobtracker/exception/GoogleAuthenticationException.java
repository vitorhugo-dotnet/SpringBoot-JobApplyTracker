package com.jobtracker.exception;

/**
 * A Google Drive/Docs API call was rejected with 401 despite the caller's access token looking
 * fresh (not yet at its stored expiry). Distinct from a generic {@link BadRequestException} so
 * {@code GoogleDriveCredentialService} can catch it specifically and retry once after a token
 * refresh, instead of relying on message string matching.
 */
public class GoogleAuthenticationException extends BadRequestException {
    public GoogleAuthenticationException(String message) {
        super(message);
    }
}
