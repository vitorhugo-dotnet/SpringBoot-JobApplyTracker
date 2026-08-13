package com.jobtracker.service.export;

import com.jobtracker.config.ExportProperties;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Stores scheduled exports in the user's own Google Drive, inside an exports folder under the
 * root folder they configured for the integration.
 */
@Component
public class GoogleDriveExportDestination implements ExportDestination {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveExportDestination.class);

    private final GoogleDriveService googleDriveService;
    private final GoogleDriveApiClient googleDriveApiClient;
    private final ExportProperties properties;

    public GoogleDriveExportDestination(GoogleDriveService googleDriveService,
                                        GoogleDriveApiClient googleDriveApiClient,
                                        ExportProperties properties) {
        this.googleDriveService = googleDriveService;
        this.googleDriveApiClient = googleDriveApiClient;
        this.properties = properties;
    }

    @Override
    public ExportDestinationType type() {
        return ExportDestinationType.GOOGLE_DRIVE;
    }

    @Override
    public StoredExportFile store(User user, ExportFile file) {
        GoogleDriveConnection connection = googleDriveService.getConnectionWithFreshAccessToken(user.getId());
        if (!StringUtils.hasText(connection.getRootFolderId())) {
            throw new BadRequestException(
                    "Configure a Google Drive root folder before scheduling exports to Google Drive");
        }

        String accessToken = connection.getAccessToken();
        String exportsFolderName = properties.getDriveFolderName();
        GoogleDriveApiClient.DriveFileMetadata exportsFolder = googleDriveApiClient
                .findFolderByName(accessToken, connection.getRootFolderId(), exportsFolderName)
                .orElseGet(() -> googleDriveApiClient.createFolder(
                        accessToken, connection.getRootFolderId(), exportsFolderName));

        GoogleDriveApiClient.DriveFileMetadata uploaded = googleDriveApiClient.uploadFile(
                accessToken, exportsFolder.id(), file.fileName(), file.format().getContentType(), file.content());

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
