package com.jobtracker.exception;

/**
 * A Google Drive access-token refresh failed transiently (network/provider issue, not a rejected
 * grant). The stored refresh token is not considered revoked; the caller should retry later.
 */
public class GoogleAccessTokenRefreshFailedException extends ServiceUnavailableException implements CodedException {

    public static final String CODE = "GOOGLE_ACCESS_TOKEN_REFRESH_FAILED";

    public GoogleAccessTokenRefreshFailedException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return CODE;
    }
}
