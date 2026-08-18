package com.jobtracker.service.export;

import com.jobtracker.config.ExportProperties;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.GoogleDriveCredentialService;
import com.jobtracker.service.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Stores scheduled exports in the user's own Google Drive, inside an exports folder under the
 * root folder they configured for the integration.
 */
@Component
public class GoogleDriveExportDestination implements ExportDestination {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveExportDestination.class);

    private final GoogleDriveService googleDriveService;
    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveCredentialService credentialService;
    private final ExportProperties properties;

    public GoogleDriveExportDestination(GoogleDriveService googleDriveService,
                                        GoogleDriveApiClient googleDriveApiClient,
                                        GoogleDriveCredentialService credentialService,
                                        ExportProperties properties) {
        this.googleDriveService = googleDriveService;
        this.googleDriveApiClient = googleDriveApiClient;
        this.credentialService = credentialService;
        this.properties = properties;
    }

    @Override
    public ExportDestinationType type() {
        return ExportDestinationType.GOOGLE_DRIVE;
    }

    @Override
    public StoredExportFile store(User user, ExportFile file) {
        UUID userId = user.getId();
        GoogleDriveConnection connection = googleDriveService.getConnectionWithFreshAccessToken(userId);
        if (!StringUtils.hasText(connection.getRootFolderId())) {
            throw new BadRequestException(
                    "Configure a Google Drive root folder before scheduling exports to Google Drive");
        }

        String rootFolderId = connection.getRootFolderId();
        String exportsFolderName = properties.getDriveFolderName();
        GoogleDriveApiClient.DriveFileMetadata exportsFolder = credentialService.call(userId, token -> googleDriveApiClient
                .findFolderByName(token, rootFolderId, exportsFolderName)
                .orElseGet(() -> googleDriveApiClient.createFolder(token, rootFolderId, exportsFolderName)));

        GoogleDriveApiClient.DriveFileMetadata uploaded = credentialService.call(userId, token -> googleDriveApiClient.uploadFile(
                token, exportsFolder.id(), file.fileName(), file.format().getContentType(), file.content()));

        log.info("event=EXPORT_STORED destination=GOOGLE_DRIVE userId={} fileId={} records={}",
                user.getId(), uploaded.id(), file.recordCount());

        return new StoredExportFile(uploaded.id(), uploaded.name(), resolveLink(uploaded));
    }

    private String resolveLink(GoogleDriveApiClient.DriveFileMetadata file) {
        return StringUtils.hasText(file.webViewLink())
                ? file.webViewLink()
                : "https://drive.google.com/file/d/" + file.id() + "/view";
    }
}
