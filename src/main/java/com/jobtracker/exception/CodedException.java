package com.jobtracker.exception;

/** Implemented by exceptions that carry a machine-readable error code in the API response body. */
public interface CodedException {
    String getCode();
}
