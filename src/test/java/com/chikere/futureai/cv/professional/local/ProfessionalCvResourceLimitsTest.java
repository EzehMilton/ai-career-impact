package com.chikere.futureai.cv.professional.local;

import com.chikere.futureai.cv.professional.application.ProfessionalCvDocument;
import com.chikere.futureai.cv.professional.application.ProfessionalCvDocumentType;
import com.chikere.futureai.cv.professional.application.ProfessionalCvParsingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What extraction does when a document is hostile rather than merely unhelpful.
 *
 * <p>Upload size is bounded long before this point, so the risk these tests cover is the one that
 * size alone does not catch: a small file that is expensive to read.</p>
 */
class ProfessionalCvResourceLimitsTest {

    private final PdfDocxProfessionalCvParser parser = new PdfDocxProfessionalCvParser(
            new DeterministicProfessionalCvStructurer(), ProfessionalCvParserLimits.defaults());

    @Test
    void anOrdinaryPdfAndDocxStillParse() {
        String cv = """
                Jane Doe
                Senior Software Engineer
                EXPERIENCE
                Senior Software Engineer, Example Ltd
                Built and maintained payment services.
                SKILLS
                Java, SQL
                """;
        assertThatCode(() -> parser.parse(document(ProfessionalCvDocumentType.PDF,
                ProfessionalCvTestDocuments.pdf(cv)))).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(document(ProfessionalCvDocumentType.DOCX,
                ProfessionalCvTestDocuments.docx(cv)))).doesNotThrowAnyException();
    }

    @Test
    void aDocxThatIsNotReallyAZipIsRefusedWithoutLeakingTheCause() {
        byte[] notAZip = "PK and then nothing that is actually a zip".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(document(ProfessionalCvDocumentType.DOCX, notAZip)))
                .isInstanceOf(ProfessionalCvParsingException.class)
                .hasMessage("CV could not be parsed");
    }

    @Test
    void aZipThatIsWellFormedButIsNotAWordDocumentIsRefused() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("not-a-word-document.txt"));
            zip.write("hello".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThatThrownBy(() -> parser.parse(document(ProfessionalCvDocumentType.DOCX, output.toByteArray())))
                .isInstanceOf(ProfessionalCvParsingException.class)
                .hasMessage("CV could not be parsed");
    }

    @Test
    void aHighlyCompressibleDocxCannotInflateBeyondTheConfiguredEntryCeiling() throws IOException {
        // A classic small-file-large-expansion shape: one entry of highly repetitive bytes. POI is
        // configured to refuse an entry that inflates past the ceiling, so this is rejected during
        // inflation rather than after a few hundred megabytes have been allocated.
        var limits = new ProfessionalCvParserLimits(100_000, 50, 64 * 1024, 0.01);
        var strictParser = new PdfDocxProfessionalCvParser(
                new DeterministicProfessionalCvStructurer(), limits);
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(new byte[8 * 1024 * 1024]); // compresses to almost nothing
            zip.closeEntry();
        }
        byte[] bomb = output.toByteArray();
        assertThat(bomb.length).as("the upload itself is small").isLessThan(100_000);

        try {
            assertThatThrownBy(() -> strictParser.parse(document(ProfessionalCvDocumentType.DOCX, bomb)))
                    .isInstanceOf(ProfessionalCvParsingException.class);
        }
        finally {
            restoreShippedLimits();
        }
    }

    @Test
    void aPdfWithMorePagesThanWeWillReadIsRefusedBeforeExtraction() throws IOException {
        var limits = new ProfessionalCvParserLimits(100_000, 2, 16L * 1024 * 1024, 0.01);
        var strictParser = new PdfDocxProfessionalCvParser(
                new DeterministicProfessionalCvStructurer(), limits);
        byte[] manyPages = pdfWithPages(40);

        try {
            assertThatThrownBy(() -> strictParser.parse(
                    document(ProfessionalCvDocumentType.PDF, manyPages)))
                    .isInstanceOf(ProfessionalCvParsingException.class);
            // The same document is fine for a parser configured to read that many pages, so the
            // refusal is the page ceiling and not something wrong with the file.
            assertThatCode(() -> parser.parse(document(ProfessionalCvDocumentType.PDF, manyPages)))
                    .doesNotThrowAnyException();
        }
        finally {
            restoreShippedLimits();
        }
    }

    private byte[] pdfWithPages(int pages) throws IOException {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
                var output = new ByteArrayOutputStream()) {
            for (int page = 0; page < pages; page++) {
                document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void extractedTextIsRejectedOnceItPassesTheCapRatherThanBeingTruncated() {
        var limits = new ProfessionalCvParserLimits(1_000, 50, 16L * 1024 * 1024, 0.01);
        var strictParser = new PdfDocxProfessionalCvParser(
                new DeterministicProfessionalCvStructurer(), limits);
        String wordy = "Experience\n"
                + "Delivered a great many things for a great many people.\n".repeat(80);

        try {
            assertThatThrownBy(() -> strictParser.parse(document(ProfessionalCvDocumentType.DOCX,
                    ProfessionalCvTestDocuments.docx(wordy))))
                    .isInstanceOf(ProfessionalCvParsingException.class);
        }
        finally {
            restoreShippedLimits();
        }
    }

    @Test
    void theLimitsThemselvesRefuseNonsensicalConfiguration() {
        assertThatThrownBy(() -> new ProfessionalCvParserLimits(10, 50, 1024, 0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalCvParserLimits(100_000, 0, 1024, 0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalCvParserLimits(100_000, 50, 0, 0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalCvParserLimits(100_000, 50, 1024, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Apache POI's ZIP guards are JVM-wide statics rather than per-document settings, so a test
     * that tightens them has to put them back or it changes how every later test behaves.
     */
    private void restoreShippedLimits() {
        new PdfDocxProfessionalCvParser(new DeterministicProfessionalCvStructurer(),
                ProfessionalCvParserLimits.defaults());
    }

    private ProfessionalCvDocument document(ProfessionalCvDocumentType type, byte[] bytes) {
        return new ProfessionalCvDocument("cv" + type.extension(), type, bytes);
    }
}
