package com.jobtracker.util;

import com.jobtracker.exception.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Google Drive file or folder ID out of a raw value that may be a bare ID or any of the
 * common Drive share URL shapes (e.g. {@code /d/<id>}, {@code /folders/<id>}, {@code ?id=<id>}).
 */
public final class GoogleDriveFileIds {

    private static final Pattern PATH_ID_PATTERN = Pattern.compile("/(?:d|folders)/([a-zA-Z0-9_-]{10,})");
    private static final Pattern QUERY_ID_PATTERN = Pattern.compile("[?&]id=([a-zA-Z0-9_-]{10,})");

    private GoogleDriveFileIds() {
    }

    public static String extract(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new BadRequestException("Google file or folder ID is required");
        }

        String trimmed = rawValue.trim();
        if (!trimmed.contains("/")) {
            return trimmed;
        }

        Matcher pathMatcher = PATH_ID_PATTERN.matcher(trimmed);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher queryMatcher = QUERY_ID_PATTERN.matcher(trimmed);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        throw new BadRequestException("Could not extract a Google file or folder ID from the provided value");
    }
}
