package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeGenerationApplicationFlowTest {

    private static final String TEST_SCOPES = "https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/documents.readonly";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BASE_RESUME_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock private GoogleDriveApiClient googleDriveApiClient;
    @Mock private GoogleDriveConnectionRepository connectionRepository;
    @Mock private GoogleDriveBaseResumeRepository baseResumeRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private SecurityUtils securityUtils;

    private ResumeGenerationService service;
    private User user;
    private GoogleDriveConnection connection;
    private GoogleDriveBaseResume baseResume;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        service = new ResumeGenerationService(
                googleDriveApiClient,
                new GoogleDriveProperties("client", "secret", "cb", "frontend", "auth", "token", TEST_SCOPES),
                connectionRepository,
                baseResumeRepository,
                applicationRepository,
                securityUtils
        );

        user = new User();
        user.setId(USER_ID);

        connection = new GoogleDriveConnection();
        connection.setUser(user);
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setRootFolderId("root-folder-id");

        baseResume = new GoogleDriveBaseResume();
        baseResume.setId(BASE_RESUME_ID);
        baseResume.setConnection(connection);
        baseResume.setGoogleFileId("base-doc-id");
        baseResume.setDocumentName("Base Resume");
        baseResume.setLanguage("PT");
        baseResume.setTemplate(true);
        baseResume.setReadOnly(false);

        application = new JobApplication();
        application.setId(APPLICATION_ID);
        application.setUser(user);
        application.setVacancyName("Backend Engineer");
        application.setOrganization("Acme");
        application.setApplicationDate(LocalDate.now());
        application.setDriveVacancyFolderId("vacancy-folder-id");
    }

    private void mockFullGenerationFlow() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.getFileMetadata("access-token", "root-folder-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "root-folder-id", "Root", GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/root-folder-id"));
        when(googleDriveApiClient.getFileMetadata("access-token", "vacancy-folder-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "vacancy-folder-id", "Vacancy", GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/vacancy-folder-id"));

        AtomicInteger callCount = new AtomicInteger();
        when(googleDriveApiClient.copyGoogleDoc(eq("access-token"), eq("base-doc-id"), eq("vacancy-folder-id"), any()))
                .thenAnswer(invocation -> new GoogleDriveApiClient.DriveFileMetadata(
                        "copied-doc-id-" + callCount.incrementAndGet(), "Copied Resume",
                        GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE,
                        "https://docs.google.com/document/d/copied-doc-id/edit"));
        when(googleDriveApiClient.readGoogleDocText(eq("access-token"), anyString()))
                .thenReturn("Resumo: {{RESUMO}}");
        when(googleDriveApiClient.exportGoogleDocAsPdf(eq("access-token"), anyString(), eq("vacancy-folder-id"), any()))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "pdf-id", "Resume.pdf", "application/pdf",
                        "https://drive.google.com/file/d/pdf-id/view"));
    }

    @Test
    void generateApplicationResume_success_withExplicitBaseResumeId() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        mockFullGenerationFlow();

        var response = service.generateApplicationResume(
                APPLICATION_ID, BASE_RESUME_ID, null, Map.of("RESUMO", "Senior Java Engineer"));

        assertThat(response.workflowCompleted()).isTrue();
        assertThat(response.documentUrl()).isNotBlank();
        assertThat(response.pdfUrl()).isNotBlank();
        assertThat(response.baseResumeId()).isEqualTo(BASE_RESUME_ID);
        assertThat(application.getDriveResumeTemplateId()).isEqualTo(BASE_RESUME_ID);
        assertThat(application.getDriveResumePdfUrl()).isEqualTo(response.pdfUrl());
    }

    @Test
    void generateApplicationResume_success_withLanguageAutoSelection() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findAllByConnectionUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(baseResume));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        mockFullGenerationFlow();

        var response = service.generateApplicationResume(
                APPLICATION_ID, null, "pt", Map.of("RESUMO", "Senior Java Engineer"));

        assertThat(response.workflowCompleted()).isTrue();
        assertThat(response.baseResumeId()).isEqualTo(BASE_RESUME_ID);
    }

    @Test
    void generateApplicationResume_missingApplication_throwsNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateApplicationResume(APPLICATION_ID, BASE_RESUME_ID, null, Map.of("RESUMO", "x")))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(googleDriveApiClient);
    }

    @Test
    void generateApplicationResume_missingPlaceholderValues_throwsBadRequest() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.readGoogleDocText("access-token", "base-doc-id")).thenReturn("Resumo: {{RESUMO}}");

        assertThatThrownBy(() -> service.generateApplicationResume(APPLICATION_ID, BASE_RESUME_ID, null, Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("RESUMO");

        verify(googleDriveApiClient, never()).copyGoogleDoc(anyString(), anyString(), anyString(), anyString());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void generateApplicationResume_readOnlyTemplate_throwsBadRequest() {
        baseResume.setReadOnly(true);
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.generateApplicationResume(APPLICATION_ID, BASE_RESUME_ID, null, Map.of("RESUMO", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void generateApplicationResume_ambiguousLanguage_throwsBadRequest() {
        GoogleDriveBaseResume secondTemplate = new GoogleDriveBaseResume();
        secondTemplate.setId(OTHER_TEMPLATE_ID);
        secondTemplate.setConnection(connection);
        secondTemplate.setGoogleFileId("other-doc-id");
        secondTemplate.setDocumentName("Other Base Resume");
        secondTemplate.setLanguage("PT");
        secondTemplate.setTemplate(true);
        secondTemplate.setReadOnly(false);

        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findAllByConnectionUserIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(baseResume, secondTemplate));

        assertThatThrownBy(() -> service.generateApplicationResume(APPLICATION_ID, null, "PT", Map.of("RESUMO", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Multiple templates");
    }

    @Test
    void generateApplicationResume_noTemplateForLanguage_throwsBadRequest() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findAllByConnectionUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(baseResume));

        assertThatThrownBy(() -> service.generateApplicationResume(APPLICATION_ID, null, "EN", Map.of("RESUMO", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No reusable template");
    }

    @Test
    void generateApplicationResume_repeatedCalls_regenerateAndReplaceLinkedResume() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        mockFullGenerationFlow();

        var first = service.generateApplicationResume(APPLICATION_ID, BASE_RESUME_ID, null, Map.of("RESUMO", "First"));
        var second = service.generateApplicationResume(APPLICATION_ID, BASE_RESUME_ID, null, Map.of("RESUMO", "Second"));

        assertThat(first.copiedFileId()).isNotEqualTo(second.copiedFileId());
        assertThat(application.getDriveResumeFileId()).isEqualTo(second.copiedFileId());
        assertThat(application.getDriveResumeDocumentUrl()).isEqualTo(second.documentUrl());
    }
}
