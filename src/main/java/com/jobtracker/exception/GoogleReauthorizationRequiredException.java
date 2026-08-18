package com.jobtracker.exception;

/**
 * The user's Google Drive refresh token is missing, revoked, expired, or otherwise unusable, so
 * the connection cannot be refreshed automatically and the user must reconnect Google Drive.
 */
public class GoogleReauthorizationRequiredException extends UnauthorizedException implements CodedException {

    public static final String CODE = "GOOGLE_REAUTHORIZATION_REQUIRED";

    public GoogleReauthorizationRequiredException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return CODE;
    }
}
