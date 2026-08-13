package com.jobtracker.service.export;

import com.jobtracker.config.ExportProperties;
import com.jobtracker.dto.export.ExportFilters;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.ApplicationSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads the applications a user owns and turns them into a file in the requested format.
 *
 * <p>Rows are read page by page (never {@code findAll()} into a list) and the persistence context
 * is cleared between pages, so the memory footprint stays flat no matter how many applications
 * exist. A configurable record ceiling caps very large exports; when it kicks in the file is still
 * produced and flagged as truncated rather than failing outright, so a backup never silently stops
 * working.
 */
@Service
public class ApplicationExportService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationExportService.class);

    private static final String FILE_NAME_PREFIX = "applywell-applications-";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("HHmm");

    /** Stable ordering so paging cannot skip or repeat rows while the export runs. */
    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.ASC, "createdAt")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private final ApplicationRepository applicationRepository;
    private final ExportProperties properties;
    private final Map<ExportFormat, ExportWriter> writers;

    @PersistenceContext
    private EntityManager entityManager;

    public ApplicationExportService(ApplicationRepository applicationRepository,
                                    ExportProperties properties,
                                    List<ExportWriter> writers) {
        this.applicationRepository = applicationRepository;
        this.properties = properties;
        this.writers = writers.stream()
                .collect(Collectors.toUnmodifiableMap(ExportWriter::format, Function.identity()));
    }

    /**
     * Builds an export file for a single user.
     *
     * @param userId owner of every exported row — the specification is anchored on it, so no other
     *               user's data can appear in the file
     */
    @Transactional(readOnly = true)
    public ExportFile export(UUID userId, ExportFormat format, ExportFilters filters, List<String> columnKeys) {
        ExportWriter writer = writers.get(format);
        if (writer == null) {
            throw new BadRequestException("Unsupported export format: " + format);
        }

        List<ExportColumn> columns = ExportColumn.resolve(columnKeys);
        Specification<JobApplication> spec = ApplicationSpecifications.forExport(userId, filters);
        AtomicBoolean truncated = new AtomicBoolean(false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int recordCount;
        try {
            recordCount = writer.write(buffer, columns, rowSource(spec, truncated));
        } catch (IOException e) {
            // The exception message can carry filesystem paths from POI's temp files — keep it out
            // of the user-facing error and out of the audit trail.
            log.error("event=EXPORT_WRITE_FAILED userId={} format={}", userId, format, e);
            throw new BadRequestException("Could not generate the export file");
        }

        String fileName = buildFileName(format, LocalDateTime.now(), false);
        log.info("event=EXPORT_GENERATED userId={} format={} records={} truncated={}",
                userId, format, recordCount, truncated.get());
        return new ExportFile(fileName, format, buffer.toByteArray(), recordCount, truncated.get());
    }

    /**
     * Predictable file name, e.g. {@code applywell-applications-2026-07-16.csv}. Scheduled runs
     * include the time ({@code …-2026-07-16-2000.xlsx}) so repeated runs on the same day do not
     * collide in the destination folder.
     */
    public String buildFileName(ExportFormat format, LocalDateTime timestamp, boolean includeTime) {
        String suffix = includeTime ? "-" + timestamp.format(FILE_TIME) : "";
        return FILE_NAME_PREFIX + timestamp.toLocalDate().format(FILE_DATE) + suffix + "." + format.getFileExtension();
    }

    private ExportRowSource rowSource(Specification<JobApplication> spec, AtomicBoolean truncated) {
        int maxRecords = properties.getMaxRecords();
        int pageSize = Math.max(1, properties.getPageSize());

        return consumer -> {
            int written = 0;
            int pageNumber = 0;
            while (true) {
                Page<JobApplication> page = applicationRepository.findAll(
                        spec, PageRequest.of(pageNumber, pageSize, EXPORT_SORT));

                for (JobApplication application : page.getContent()) {
                    if (written >= maxRecords) {
                        truncated.set(true);
                        return written;
                    }
                    consumer.accept(application);
                    written++;
                }

                // Detach the page so the persistence context does not grow with the export.
                entityManager.clear();

                if (!page.hasNext()) {
                    return written;
                }
                pageNumber++;
            }
        };
    }
}
