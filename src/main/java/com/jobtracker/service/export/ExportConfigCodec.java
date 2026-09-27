package com.jobtracker.service.export;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.jobtracker.dto.export.ExportFilters;
import com.jobtracker.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Reads and writes the filter/column configuration a schedule stores as JSON.
 *
 * <p>Kept separate from the services so that both the CRUD service and the background executor can
 * use it without depending on each other.
 */
@Component
public class ExportConfigCodec {

    private final ObjectMapper objectMapper;

    public ExportConfigCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BadRequestException("Could not store the export configuration");
        }
    }

    /** Unreadable stored JSON degrades to "no filters" rather than breaking a scheduled backup. */
    public ExportFilters readFilters(String json) {
        if (!StringUtils.hasText(json)) {
            return ExportFilters.empty();
        }
        try {
            return objectMapper.readValue(json, ExportFilters.class);
        } catch (JacksonException e) {
            return ExportFilters.empty();
        }
    }

    public List<String> readColumns(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
