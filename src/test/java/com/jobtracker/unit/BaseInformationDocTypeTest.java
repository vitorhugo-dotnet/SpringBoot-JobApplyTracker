package com.jobtracker.unit;

import com.jobtracker.entity.enums.BaseInformationDocType;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.service.GoogleDriveApiClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseInformationDocTypeTest {

    @Test
    void classifiesGoogleDocByMimeType() {
        assertThat(BaseInformationDocType.fromDriveFile(GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE, "About Me"))
                .isEqualTo(BaseInformationDocType.GOOGLE_DOC);
    }

    @Test
    void classifiesPdfByMimeType() {
        assertThat(BaseInformationDocType.fromDriveFile(GoogleDriveApiClient.PDF_MIME_TYPE, "about-me.pdf"))
                .isEqualTo(BaseInformationDocType.PDF);
    }

    @Test
    void classifiesDocxByMimeType() {
        assertThat(BaseInformationDocType.fromDriveFile(GoogleDriveApiClient.DOCX_MIME_TYPE, "about-me.docx"))
                .isEqualTo(BaseInformationDocType.DOCX);
    }

    @Test
    void classifiesMarkdownByMimeType() {
        assertThat(BaseInformationDocType.fromDriveFile(GoogleDriveApiClient.MARKDOWN_MIME_TYPE, "about-me.md"))
                .isEqualTo(BaseInformationDocType.MARKDOWN);
        assertThat(BaseInformationDocType.fromDriveFile("text/plain", "about-me.txt"))
                .isEqualTo(BaseInformationDocType.MARKDOWN);
    }

    @Test
    void classifiesMarkdownByFileExtensionWhenMimeIsOpaque() {
        assertThat(BaseInformationDocType.fromDriveFile("application/octet-stream", "ABOUT-ME.MD"))
                .isEqualTo(BaseInformationDocType.MARKDOWN);
        assertThat(BaseInformationDocType.fromDriveFile(null, "notes.markdown"))
                .isEqualTo(BaseInformationDocType.MARKDOWN);
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> BaseInformationDocType.fromDriveFile("image/png", "headshot.png"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported base information document type");
    }
}
