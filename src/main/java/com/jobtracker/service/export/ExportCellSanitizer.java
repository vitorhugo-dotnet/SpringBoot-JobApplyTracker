package com.jobtracker.service.export;

import java.util.regex.Pattern;

/**
 * Neutralizes cell values that a spreadsheet application would otherwise evaluate as a formula
 * (CSV/Formula injection: {@code =cmd|…}, {@code +…}, {@code -…}, {@code @…}, and the tab/carriage
 * return variants Excel also treats as formula starts).
 *
 * <p>A leading apostrophe is prepended, which every major spreadsheet reads as "the rest is text".
 * Plain numbers keep their meaning: {@code -5} or {@code -1.5} are left untouched so numeric
 * columns still behave like numbers.
 */
public final class ExportCellSanitizer {

    private static final Pattern PLAIN_NUMBER = Pattern.compile("^[-+]?\\d+([.,]\\d+)?$");

    private ExportCellSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        boolean dangerous = first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
        if (!dangerous) {
            return value;
        }
        if (PLAIN_NUMBER.matcher(value).matches()) {
            return value;
        }
        return "'" + value;
    }
}
