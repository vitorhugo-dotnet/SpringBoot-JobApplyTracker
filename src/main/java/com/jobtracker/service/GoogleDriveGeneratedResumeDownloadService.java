package com.jobtracker.service;

import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class GoogleDriveGeneratedResumeDownloadService {

    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveCredentialService credentialService;
    private final GoogleDriveBaseResumeRepository baseResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public GoogleDriveGeneratedResumeDownloadService(GoogleDriveApiClient googleDriveApiClient,
                                                     GoogleDriveCredentialService credentialService,
                                                     GoogleDriveBaseResumeRepository baseResumeRepository,
                                                     ApplicationRepository applicationRepository,
                                                     SecurityUtils securityUtils) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.credentialService = credentialService;
        this.baseResumeRepository = baseResumeRepository;
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public DownloadedFile downloadAsDocx(UUID applicationId) {
        return downloadApplication(applicationId, DOCX_MIME_TYPE, "docx");
    }

    @Transactional
    public DownloadedFile downloadAsPdf(UUID applicationId) {
        return downloadApplication(applicationId, PDF_MIME_TYPE, "pdf");
    }

    @Transactional
    public DownloadedFile downloadBaseResumeAsDocx(UUID baseResumeId) {
        return downloadBaseResume(baseResumeId, DOCX_MIME_TYPE, "docx");
    }

    @Transactional
    public DownloadedFile downloadBaseResumeAsPdf(UUID baseResumeId) {
        return downloadBaseResume(baseResumeId, PDF_MIME_TYPE, "pdf");
    }

    private DownloadedFile downloadApplication(UUID applicationId, String exportMimeType, String extension) {
        UUID userId = securityUtils.getCurrentUserId();
        credentialService.getValidCredentials(userId);
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (!StringUtils.hasText(application.getDriveResumeFileId())) {
            throw new BadRequestException("Generate a resume first before downloading it");
        }

        byte[] content = credentialService.call(userId,
                token -> googleDriveApiClient.exportDocument(token, application.getDriveResumeFileId(), exportMimeType));
        String fileName = buildDownloadFileName(
                firstNonBlank(application.getDriveResumeFileName(), application.getVacancyName(), application.getOrganization(), "application-resume"),
                extension
        );

        return new DownloadedFile(fileName, exportMimeType, content);
    }

    private DownloadedFile downloadBaseResume(UUID baseResumeId, String exportMimeType, String extension) {
        UUID userId = securityUtils.getCurrentUserId();
        credentialService.getValidCredentials(userId);

        GoogleDriveBaseResume baseResume = baseResumeRepository
                .findByIdAndConnectionUserId(baseResumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + baseResumeId));

        if (baseResume.isReadOnly() && DOCX_MIME_TYPE.equals(exportMimeType)) {
            throw new BadRequestException("Cannot download a PDF resume as DOCX");
        }

        byte[] content;
        if (baseResume.isReadOnly()) {
            content = credentialService.call(userId,
                    token -> googleDriveApiClient.downloadFileBytes(token, baseResume.getGoogleFileId()));
        } else {
            content = credentialService.call(userId,
                    token -> googleDriveApiClient.exportDocument(token, baseResume.getGoogleFileId(), exportMimeType));
        }
        String fileName = buildDownloadFileName(baseResume.getDocumentName(), extension);

        return new DownloadedFile(fileName, exportMimeType, content);
    }

    private String buildDownloadFileName(String baseName, String extension) {
        String sanitized = sanitizeFileName(firstNonBlank(baseName, "resume"));
        int maxLength = Math.max(1, 220 - (extension.length() + 1));
        String truncated = sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength).trim();
        return truncated + "." + extension;
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[\\/:*?\"<>|]+", "-").replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "application-resume";
    }

    public record DownloadedFile(String fileName, String contentType, byte[] content) {}
}
