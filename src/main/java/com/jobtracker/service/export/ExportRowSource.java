package com.jobtracker.service.export;

import com.jobtracker.entity.JobApplication;

import java.util.function.Consumer;

/**
 * Feeds applications to a writer one row at a time.
 *
 * <p>Implementations page through the database instead of materialising the whole result set, so
 * memory use stays flat regardless of how many applications a user owns.
 */
@FunctionalInterface
public interface ExportRowSource {

    /**
     * Pushes every row to {@code consumer}.
     *
     * @return the number of rows fed
     */
    int forEachRow(Consumer<JobApplication> consumer);
}
