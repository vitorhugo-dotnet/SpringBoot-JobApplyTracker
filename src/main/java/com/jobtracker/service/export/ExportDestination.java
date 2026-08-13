package com.jobtracker.service.export;

import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;

/**
 * Delivers a generated export file somewhere durable.
 *
 * <p>The container's local filesystem is never a destination: files are streamed straight to the
 * client (manual export) or pushed to the user's own storage (scheduled export).
 *
 * <p>Adding e-mail, object storage or webhook delivery later means adding one bean implementing
 * this interface with the matching {@link ExportDestinationType}.
 */
public interface ExportDestination {

    ExportDestinationType type();

    StoredExportFile store(User user, ExportFile file);

    /** Where the file ended up, as far as the destination can describe it. */
    record StoredExportFile(String fileId, String fileName, String fileUrl) {}
}
