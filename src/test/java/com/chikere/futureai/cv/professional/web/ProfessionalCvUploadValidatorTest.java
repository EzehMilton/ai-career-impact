package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.cv.professional.application.ProfessionalCvDocumentType;
import com.chikere.futureai.cv.professional.local.ProfessionalCvTestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class ProfessionalCvUploadValidatorTest {
    private final ProfessionalCvUploadValidator validator = new ProfessionalCvUploadValidator(5 * 1024 * 1024);

    @Test
    void acceptsGenuinePdfAndDocxAndSanitisesPathLikeFilename() {
        var pdf = new MockMultipartFile("cv", "../../Alex<script>.pdf", "application/pdf",
                ProfessionalCvTestDocuments.pdf("A sufficiently descriptive synthetic CV body for validation."));
        var docx = new MockMultipartFile("cv", "Alex.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ProfessionalCvTestDocuments.docx("Synthetic CV"));

        assertThat(validator.validate(pdf)).satisfies(document -> {
            assertThat(document.type()).isEqualTo(ProfessionalCvDocumentType.PDF);
            assertThat(document.originalFilename()).isEqualTo("Alex_script_.pdf");
        });
        assertThat(validator.validate(docx).type()).isEqualTo(ProfessionalCvDocumentType.DOCX);
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndSpoofedDocuments() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("cv", "empty.pdf",
                "application/pdf", new byte[0]))).hasMessageContaining("Choose");
        var smallLimit = new ProfessionalCvUploadValidator(4);
        assertThatThrownBy(() -> smallLimit.validate(new MockMultipartFile("cv", "large.pdf",
                "application/pdf", "%PDF-long".getBytes()))).hasMessageContaining("too large");
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("cv", "cv.txt",
                "text/plain", "not supported".getBytes()))).hasMessageContaining("genuine PDF or DOCX");
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("cv", "fake.pdf",
                "application/pdf", "not a pdf".getBytes()))).hasMessageContaining("does not match");
    }
}
