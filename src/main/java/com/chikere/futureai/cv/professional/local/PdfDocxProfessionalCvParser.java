package com.chikere.futureai.cv.professional.local;

import com.chikere.futureai.cv.professional.application.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Local PDF and DOCX text extraction. Nothing here reaches the network, and no raw CV is retained
 * beyond the call.
 *
 * <p>Both paths stop early rather than building an unbounded string and rejecting it afterwards.
 * A PDF is read a page at a time and abandoned as soon as the text passes the cap, so the peak cost
 * is the cap plus one page. A DOCX is bounded by Apache POI's own ZIP guards, which abort during
 * inflation — before the XML is ever materialised.</p>
 *
 * <p>POI's guards are JVM-wide statics rather than per-document settings. That is POI's API, not a
 * choice made here; they are applied once at construction and are the same for every upload.</p>
 */
@Component
public final class PdfDocxProfessionalCvParser implements ProfessionalCvParser {
    private final ProfessionalCvStructurer structurer;
    private final ProfessionalCvParserLimits limits;

    public PdfDocxProfessionalCvParser(ProfessionalCvStructurer structurer,
            ProfessionalCvParserLimits limits) {
        this.structurer = structurer;
        this.limits = limits;
        ZipSecureFile.setMinInflateRatio(limits.minimumInflateRatio());
        ZipSecureFile.setMaxEntrySize(limits.maximumEntryBytes());
        ZipSecureFile.setMaxTextSize(limits.maximumExtractedCharacters());
    }

    @Override
    public ProfessionalCvParseResult parse(ProfessionalCvDocument document) {
        try {
            String text = switch (document.type()) {
                case PDF -> extractPdf(document.bytes());
                case DOCX -> extractDocx(document.bytes());
            };
            if (text.length() > limits.maximumExtractedCharacters()) {
                throw new ProfessionalCvParsingException("CV contains too much extracted text");
            }
            return structurer.structure(text);
        }
        catch (ProfessionalCvParsingException exception) {
            throw exception;
        }
        catch (IOException | RuntimeException exception) {
            // Includes POI's own refusals for an over-large or suspiciously compressed entry, and
            // PDFBox's refusals for a malformed document. All of them mean the same thing to the
            // person uploading: this file could not be read.
            throw new ProfessionalCvParsingException("CV could not be parsed", exception);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            int pages = pdf.getNumberOfPages();
            if (pages > limits.maximumPages()) {
                throw new ProfessionalCvParsingException("CV has more pages than we will read");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text.append(stripper.getText(pdf));
                if (text.length() > limits.maximumExtractedCharacters()) {
                    throw new ProfessionalCvParsingException("CV contains too much extracted text");
                }
            }
            return text.toString();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (var docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String paragraphs = docx.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().strip())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            String tables = docx.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .map(cell -> cell.getText().strip())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            return paragraphs + (tables.isBlank() ? "" : "\n" + tables);
        }
    }
}
