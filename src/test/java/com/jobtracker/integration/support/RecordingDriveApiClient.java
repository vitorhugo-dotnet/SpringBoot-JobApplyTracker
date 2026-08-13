package com.jobtracker.integration.support;

import com.jobtracker.service.GoogleDriveApiClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory Google Drive client for export tests: it records folder creation and uploads so a test
 * can assert what would have landed in the user's Drive.
 */
public class RecordingDriveApiClient implements GoogleDriveApiClient {

    public record Upload(String folderName, String fileName, String mimeType, byte[] content) {}

    public final List<Upload> uploads = new ArrayList<>();
    private final Map<String, DriveFileMetadata> foldersByName = new HashMap<>();

    public void reset() {
        uploads.clear();
        foldersByName.clear();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?state=" + state;
    }

    @Override
    public OAuthTokens exchangeAuthorizationCode(String code) {
        return new OAuthTokens("drive-access", "drive-refresh", LocalDateTime.now().plusHours(1), "scope");
    }

    @Override
    public OAuthTokens refreshAccessToken(String refreshToken) {
        return new OAuthTokens("drive-access", refreshToken, LocalDateTime.now().plusHours(1), "scope");
    }

    @Override
    public GoogleDriveAccountProfile getCurrentAccount(String accessToken) {
        return new GoogleDriveAccountProfile("acct-1", "owner@gmail.com", "Owner");
    }

    @Override
    public DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        return new DriveFileMetadata(fileId, fileId, GOOGLE_FOLDER_MIME_TYPE, null);
    }

    @Override
    public Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName) {
        return Optional.ofNullable(foldersByName.get(folderName));
    }

    @Override
    public DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName) {
        DriveFileMetadata folder = new DriveFileMetadata(
                "folder-" + folderName, folderName, GOOGLE_FOLDER_MIME_TYPE, null);
        foldersByName.put(folderName, folder);
        return folder;
    }

    @Override
    public DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName) {
        return new DriveFileMetadata("copy-" + sourceFileId, newName, GOOGLE_DOC_MIME_TYPE, null);
    }

    @Override
    public String readGoogleDocText(String accessToken, String documentId) {
        return "";
    }

    @Override
    public void replaceGoogleDocPlaceholders(String accessToken, String documentId, Map<String, String> values) {
    }

    @Override
    public DriveFileMetadata exportGoogleDocAsPdf(String accessToken, String documentId, String targetFolderId, String pdfName) {
        return new DriveFileMetadata("pdf-" + documentId, pdfName, PDF_MIME_TYPE, null);
    }

    @Override
    public byte[] downloadFileBytes(String accessToken, String fileId) {
        return new byte[0];
    }

    @Override
    public DriveFileMetadata uploadFile(String accessToken, String parentFolderId, String fileName,
                                        String mimeType, byte[] content) {
        String folderName = foldersByName.values().stream()
                .filter(folder -> folder.id().equals(parentFolderId))
                .map(DriveFileMetadata::name)
                .findFirst()
                .orElse(parentFolderId);
        uploads.add(new Upload(folderName, fileName, mimeType, content));
        return new DriveFileMetadata("file-" + fileName, fileName, mimeType,
                "https://drive.google.com/file/d/file-" + fileName + "/view");
    }
}
