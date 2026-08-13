package com.jobtracker.entity.enums;

/**
 * Where a scheduled export file is delivered.
 *
 * <p>Only {@link #GOOGLE_DRIVE} is implemented for now; the remaining constants are declared so
 * that persisted rows, the API contract and the UI are already shaped for them. Adding support
 * means providing one {@link com.jobtracker.service.export.ExportDestination} bean.
 */
public enum ExportDestinationType {

    GOOGLE_DRIVE,
    EMAIL,
    OBJECT_STORAGE,
    WEBHOOK
}
