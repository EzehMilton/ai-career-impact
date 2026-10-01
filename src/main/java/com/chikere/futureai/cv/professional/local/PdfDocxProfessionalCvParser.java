package com.chikere.futureai.cv.professional.local;

import com.chikere.futureai.cv.professional.application.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
public final class PdfDocxProfessionalCvParser implements ProfessionalCvParser {
    private final ProfessionalCvStructurer structurer;
    private final int maximumExtractedCharacters;

    public PdfDocxProfessionalCvParser(
            ProfessionalCvStructurer structurer,
            @Value("${app.professional.cv.maximum-extracted-characters:100000}") int maximumExtractedCharacters) {
        this.structurer = structurer;
        if (maximumExtractedCharacters < 1_000) {
            throw new IllegalArgumentException("maximumExtractedCharacters must be at least 1000");
        }
        this.maximumExtractedCharacters = maximumExtractedCharacters;
    }

    @Override
    public ProfessionalCvParseResult parse(ProfessionalCvDocument document) {
        try {
            String text = switch (document.type()) {
                case PDF -> extractPdf(document.bytes());
                case DOCX -> extractDocx(document.bytes());
            };
            if (text.length() > maximumExtractedCharacters) {
                throw new ProfessionalCvParsingException("CV contains too much extracted text");
            }
            return structurer.structure(text);
        }
        catch (ProfessionalCvParsingException exception) {
            throw exception;
        }
        catch (IOException | RuntimeException exception) {
            throw new ProfessionalCvParsingException("CV could not be parsed", exception);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
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
