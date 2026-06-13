package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.gdrive.BaseInformationContentResponse;
import com.jobtracker.dto.gdrive.BaseInformationRequest;
import com.jobtracker.dto.gdrive.BaseInformationResponse;
import com.jobtracker.entity.GoogleDriveBaseInformation;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.BaseInformationDocType;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.GoogleDriveBaseInformationRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.service.BaseInformationService;
import com.jobtracker.service.DocumentTextExtractor;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseInformationServiceTest {

    private static final String TEST_SCOPES = "https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/documents.readonly";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INFO_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock private GoogleDriveApiClient googleDriveApiClient;
    @Mock private GoogleDriveConnectionRepository connectionRepository;
    @Mock private GoogleDriveBaseInformationRepository baseInformationRepository;
    @Mock private SecurityUtils securityUtils;

    private BaseInformationService service;
    private GoogleDriveConnection connection;

    @BeforeEach
    void setUp() {
        GoogleDriveProperties googleDriveProperties = new GoogleDriveProperties(
                "client-id",
                "client-secret",
                "http://localhost:8080/api/v1/google-drive/oauth/callback",
                "http://localhost:5173/settings/google-drive/callback",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                TEST_SCOPES
        );
        service = new BaseInformationService(
                googleDriveApiClient,
                googleDriveProperties,
                connectionRepository,
                baseInformationRepository,
                new DocumentTextExtractor(),
                securityUtils
        );

        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");

        connection = new GoogleDriveConnection();
        connection.setId(CONNECTION_ID);
        connection.setUser(user);
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setGrantedScopes("https://www.googleapis.com/auth/drive");
    }

    @Test
    void addBaseInformation_classifiesMarkdownAndPersists() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.getFileMetadata("access-token", "info-file-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "info-file-id",
                        "about-me.md",
                        GoogleDriveApiClient.MARKDOWN_MIME_TYPE,
                        "https://drive.google.com/file/d/info-file-id/view"));
        when(baseInformationRepository.findByConnectionIdAndGoogleFileId(CONNECTION_ID, "info-file-id"))
                .thenReturn(Optional.empty());
        when(baseInformationRepository.save(any(GoogleDriveBaseInformation.class)))
                .thenAnswer(invocation -> {
                    GoogleDriveBaseInformation saved = invocation.getArgument(0);
                    saved.setId(INFO_ID);
                    return saved;
                });

        BaseInformationResponse response = service.addBaseInformation(new BaseInformationRequest("info-file-id"));

        assertThat(response.id()).isEqualTo(INFO_ID);
        assertThat(response.name()).isEqualTo("about-me.md");
        assertThat(response.docType()).isEqualTo("MARKDOWN");
    }

    @Test
    void addBaseInformation_rejectsUnsupportedFileTypes() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.getFileMetadata("access-token", "image-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "image-id", "headshot.png", "image/png", null));

        assertThatThrownBy(() -> service.addBaseInformation(new BaseInformationRequest("image-id")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported base information document type");
    }

    @Test
    void getBaseInformationContent_extractsMarkdownFromDownloadedBytes() {
        GoogleDriveBaseInformation info = new GoogleDriveBaseInformation();
        info.setId(INFO_ID);
        info.setConnection(connection);
        info.setGoogleFileId("info-file-id");
        info.setDocumentName("about-me.md");
        info.setDocType(BaseInformationDocType.MARKDOWN);

        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(baseInformationRepository.findByIdAndConnectionUserId(INFO_ID, USER_ID)).thenReturn(Optional.of(info));
        when(googleDriveApiClient.downloadFileBytes("access-token", "info-file-id"))
                .thenReturn("# About Me\nSenior Java Developer".getBytes(StandardCharsets.UTF_8));

        BaseInformationContentResponse response = service.getBaseInformationContent(INFO_ID);

        assertThat(response.id()).isEqualTo(INFO_ID);
        assertThat(response.docType()).isEqualTo("MARKDOWN");
        assertThat(response.content()).contains("Senior Java Developer");
    }

    @Test
    void getBaseInformationContent_readsGoogleDocText() {
        GoogleDriveBaseInformation info = new GoogleDriveBaseInformation();
        info.setId(INFO_ID);
        info.setConnection(connection);
        info.setGoogleFileId("gdoc-id");
        info.setDocumentName("About Me Doc");
        info.setDocType(BaseInformationDocType.GOOGLE_DOC);

        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(baseInformationRepository.findByIdAndConnectionUserId(INFO_ID, USER_ID)).thenReturn(Optional.of(info));
        when(googleDriveApiClient.readGoogleDocText("access-token", "gdoc-id")).thenReturn("Profile from Google Doc");

        BaseInformationContentResponse response = service.getBaseInformationContent(INFO_ID);

        assertThat(response.docType()).isEqualTo("GOOGLE_DOC");
        assertThat(response.content()).isEqualTo("Profile from Google Doc");
    }

    @Test
    void deleteBaseInformation_deletesOwnedDocument() {
        GoogleDriveBaseInformation info = new GoogleDriveBaseInformation();
        info.setId(INFO_ID);
        info.setConnection(connection);
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(baseInformationRepository.findByIdAndConnectionUserId(INFO_ID, USER_ID)).thenReturn(Optional.of(info));

        service.deleteBaseInformation(INFO_ID);

        verify(baseInformationRepository).delete(info);
    }

    @Test
    void deleteBaseInformation_throwsWhenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(baseInformationRepository.findByIdAndConnectionUserId(INFO_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBaseInformation(INFO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
