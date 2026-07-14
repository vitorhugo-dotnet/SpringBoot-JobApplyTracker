package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.util.SecurityUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeGenerationService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.*?)}}");

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveBaseResumeRepository baseResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public ResumeGenerationService(GoogleDriveApiClient googleDriveApiClient,
                                   GoogleDriveProperties googleDriveProperties,
                                   GoogleDriveConnectionRepository connectionRepository,
                                   GoogleDriveBaseResumeRepository baseResumeRepository,
                                   ApplicationRepository applicationRepository,
                                   SecurityUtils securityUtils) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.googleDriveProperties = googleDriveProperties;
        this.connectionRepository = connectionRepository;
        this.baseResumeRepository = baseResumeRepository;
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public ResumePlaceholderDetectionResponse detectPlaceholders(UUID baseResumeId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        GoogleDriveBaseResume baseResume = getBaseResume(baseResumeId, userId);

        if (baseResume.isReadOnly()) {
            throw new BadRequestException("Cannot detect placeholders in a read-only PDF resume");
        }

        String documentText = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), baseResume.getGoogleFileId());

        return new ResumePlaceholderDetectionResponse(
                baseResume.getId(),
                detectPlaceholders(documentText)
        );
    }

    @Transactional
    public BaseResumeContentResponse getBaseResumeContent(UUID resumeId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        GoogleDriveBaseResume baseResume = getBaseResume(resumeId, userId);

        String content;
        if (baseResume.isReadOnly()) {
            byte[] pdfBytes = googleDriveApiClient.downloadFileBytes(connection.getAccessToken(), baseResume.getGoogleFileId());
            content = extractTextFromPdf(pdfBytes);
        } else {
            content = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), baseResume.getGoogleFileId());
        }

        connectionRepository.save(connection);
        return new BaseResumeContentResponse(
                baseResume.getId(),
                baseResume.getDocumentName(),
                baseResume.getLanguage(),
                baseResume.isTemplate(),
                baseResume.isReadOnly(),
                content
        );
    }

    @Transactional
    public GeneratedResumeContentResponse getGeneratedResumeContent(UUID applicationId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (!StringUtils.hasText(application.getDriveResumeFileId())) {
            throw new BadRequestException("No generated resume found for this application");
        }

        String content = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), application.getDriveResumeFileId());
        connectionRepository.save(connection);

        return new GeneratedResumeContentResponse(
                application.getId(),
                application.getDriveResumeFileId(),
                application.getDriveResumeFileName(),
                content
        );
    }

    @Transactional
    public ResumePlaceholderResponse generateTemplateResume(UUID applicationId, ResumePlaceholderRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId).orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
        GoogleDriveBaseResume baseResume = getBaseResume(request.baseResumeId(), userId);

        if (baseResume.isReadOnly()) {
            throw new BadRequestException("Cannot generate a resume from a read-only PDF resume. Use a Google Docs template instead.");
        }

        if (!StringUtils.hasText(connection.getRootFolderId())) {
            throw new BadRequestException("Configure a Google Drive root folder before generating resumes");
        }

        GoogleDriveApiClient.DriveFileMetadata rootFolder =
                googleDriveApiClient.getFileMetadata(connection.getAccessToken(), connection.getRootFolderId());
        if (!GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE.equals(rootFolder.mimeType())) {
            throw new BadRequestException("Configured root folder is no longer a valid Google Drive folder");
        }
        connection.setRootFolderName(rootFolder.name());

        GoogleDriveApiClient.DriveFileMetadata vacancyFolder = resolveOrCreateVacancyFolder(connection, application, rootFolder.id(), userId);

        String copiedFileName = buildCopiedDocumentName(application, baseResume.getDocumentName());
        GoogleDriveApiClient.DriveFileMetadata copiedFile = googleDriveApiClient.copyGoogleDoc(
                connection.getAccessToken(),
                baseResume.getGoogleFileId(),
                vacancyFolder.id(),
                copiedFileName
        );

        Map<String, String> values = request.values() == null ? Map.of() : request.values();
        googleDriveApiClient.replaceGoogleDocPlaceholders(connection.getAccessToken(), copiedFile.id(), values);

        String copiedDocumentUrl = resolveDocumentLink(copiedFile);
        String copiedText = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), copiedFile.id());
        List<String> remainingPlaceholders = detectPlaceholders(copiedText);
        String pdfName = truncateFileName(sanitizeFileName(stripGoogleDocExtension(copiedFile.name()) + ".pdf"), 220);
        GoogleDriveApiClient.DriveFileMetadata pdfFile = googleDriveApiClient.exportGoogleDocAsPdf(
                connection.getAccessToken(),
                copiedFile.id(),
                vacancyFolder.id(),
                pdfName
        );
        LocalDateTime generatedAt = LocalDateTime.now();
        String pdfUrl = resolveDocumentLink(pdfFile);

        application.setDriveResumeFileId(copiedFile.id());
        application.setDriveResumeFileName(copiedFile.name());
        application.setDriveResumeDocumentUrl(copiedDocumentUrl);
        application.setDriveResumeGeneratedAt(generatedAt);
        application.setDriveResumeTemplateId(baseResume.getId());
        application.setDriveResumePdfFileId(pdfFile.id());
        application.setDriveResumePdfUrl(pdfUrl);

        connectionRepository.save(connection);
        applicationRepository.save(application);

        return new ResumePlaceholderResponse(
                application.getId(),
                baseResume.getId(),
                copiedFile.id(),
                pdfFile.id(),
                copiedDocumentUrl,
                pdfUrl,
                values,
                remainingPlaceholders,
                generatedAt,
                true
        );
    }

    /**
     * Atomic, agent-proof entry point for issue #61: resolves the template, validates every
     * detected placeholder has a value, and generates the resume in a single call so the
     * List-Base-Resumes / Detect-Resume-Placeholders / Generate-Resume sequence cannot be
     * silently skipped or replaced by local file generation.
     */
    @Transactional
    public ResumePlaceholderResponse generateApplicationResume(UUID applicationId, UUID explicitBaseResumeId, String language, Map<String, String> values) {
        UUID userId = securityUtils.getCurrentUserId();
        applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        UUID baseResumeId = explicitBaseResumeId != null ? explicitBaseResumeId : selectBaseResumeIdByLanguage(userId, language);

        List<String> placeholders = detectPlaceholders(baseResumeId).placeholders();
        Map<String, String> safeValues = values == null ? Map.of() : values;
        validateAllPlaceholdersProvided(placeholders, safeValues);

        return generateTemplateResume(applicationId, new ResumePlaceholderRequest(baseResumeId, safeValues));
    }

    private UUID selectBaseResumeIdByLanguage(UUID userId, String language) {
        if (!StringUtils.hasText(language)) {
            throw new BadRequestException("Provide either baseResumeId or language to select a resume template");
        }

        List<GoogleDriveBaseResume> candidates = baseResumeRepository.findAllByConnectionUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(GoogleDriveBaseResume::isTemplate)
                .filter(resume -> !resume.isReadOnly())
                .filter(resume -> language.equalsIgnoreCase(resume.getLanguage()))
                .toList();

        if (candidates.isEmpty()) {
            throw new BadRequestException("No reusable template found for language '" + language + "'. Use List-Base-Resumes and pass baseResumeId explicitly.");
        }
        if (candidates.size() > 1) {
            throw new BadRequestException("Multiple templates found for language '" + language + "'. Pass baseResumeId explicitly to disambiguate.");
        }
        return candidates.get(0).getId();
    }

    private void validateAllPlaceholdersProvided(List<String> placeholders, Map<String, String> values) {
        List<String> missing = placeholders.stream()
                .filter(placeholder -> !StringUtils.hasText(values.get(placeholder)))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing values for placeholders: " + String.join(", ", missing));
        }
    }

    public List<String> detectPlaceholders(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            if (StringUtils.hasText(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        return List.copyOf(placeholders);
    }

    private String extractTextFromPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to extract text from PDF resume: " + ex.getMessage());
        }
    }

    private GoogleDriveBaseResume getBaseResume(UUID baseResumeId, UUID userId) {
        return baseResumeRepository.findByIdAndConnectionUserId(baseResumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + baseResumeId));
    }

    private GoogleDriveConnection getConnectionWithFreshAccessToken() {
        if (!googleDriveProperties.isConfigured()) {
            throw new BadRequestException("Google Drive integration is not configured on the server");
        }
        GoogleDriveConnection connection = connectionRepository.findByUserId(securityUtils.getCurrentUserId())
                .orElseThrow(() -> new BadRequestException("Google Drive is not connected for the current user"));
        return refreshAccessTokenIfNeeded(connection);
    }

    private GoogleDriveConnection refreshAccessTokenIfNeeded(GoogleDriveConnection connection) {
        if (connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            return connection;
        }

        GoogleDriveApiClient.OAuthTokens refreshed = googleDriveApiClient.refreshAccessToken(connection.getRefreshToken());
        connection.setAccessToken(refreshed.accessToken());
        connection.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        if (StringUtils.hasText(refreshed.scope())) {
            connection.setGrantedScopes(refreshed.scope());
        }
        return connectionRepository.save(connection);
    }

    private GoogleDriveApiClient.DriveFileMetadata resolveOrCreateVacancyFolder(
            GoogleDriveConnection connection,
            JobApplication application,
            String rootFolderId,
            UUID userId) {

        if (StringUtils.hasText(application.getDriveVacancyFolderId())) {
            return googleDriveApiClient.getFileMetadata(connection.getAccessToken(), application.getDriveVacancyFolderId());
        }

        String vacancyFolderName = buildVacancyFolderName(application);
        GoogleDriveApiClient.DriveFileMetadata folder = googleDriveApiClient
                .findFolderByName(connection.getAccessToken(), rootFolderId, vacancyFolderName)
                .orElseGet(() -> googleDriveApiClient.createFolder(connection.getAccessToken(), rootFolderId, vacancyFolderName));

        int updated = applicationRepository.setDriveVacancyFolderIdIfAbsent(application.getId(), folder.id());
        if (updated == 0) {
            String winningFolderId = applicationRepository.findByIdAndUserId(application.getId(), userId)
                    .map(JobApplication::getDriveVacancyFolderId)
                    .filter(StringUtils::hasText)
                    .orElse(folder.id());
            if (!winningFolderId.equals(folder.id())) {
                folder = googleDriveApiClient.getFileMetadata(connection.getAccessToken(), winningFolderId);
            }
        }

        return folder;
    }

    private String buildVacancyFolderName(JobApplication application) {
        String suffix = " - APP-" + application.getId();
        String rawBase = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String truncatedBase;
        if (rawBase != null) {
            truncatedBase = truncateFileName(sanitizeFileName(rawBase), 180 - suffix.length());
        } else throw new IllegalStateException("Vacancy name, organization and application id are all blank for application id: " + application.getId());
        return truncatedBase + suffix;
    }

    private String buildCopiedDocumentName(JobApplication application, String baseResumeName) {
        String vacancyName = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String prefix = "APP-" + application.getId() + " - " + vacancyName;
        return truncateFileName(sanitizeFileName(prefix + " - " + baseResumeName), 220);
    }

    private String stripGoogleDocExtension(String value) {
        return value == null ? "resume" : value.replaceFirst("(?i)\\.gdoc$", "");
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", " ").trim();
    }

    private String truncateFileName(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolveDocumentLink(GoogleDriveApiClient.DriveFileMetadata file) {
        return StringUtils.hasText(file.webViewLink())
                ? file.webViewLink()
                : "https://docs.google.com/document/d/" + file.id() + "/edit";
    }

    public record GeneratedResumeContentResponse(UUID applicationId, String resumeFileId, String fileName, String content) {}

}
