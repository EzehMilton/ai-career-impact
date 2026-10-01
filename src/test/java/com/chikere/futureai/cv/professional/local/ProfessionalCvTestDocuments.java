package com.chikere.futureai.cv.professional.local;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ProfessionalCvTestDocuments {
    private ProfessionalCvTestDocuments() {}

    public static byte[] pdf(String text) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(50, 750);
                for (String line : text.split("\\R", -1)) {
                    content.showText(line.replace("•", "-").replace("–", "-"));
                    content.newLineAtOffset(0, -14);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static byte[] docx(String text) {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            for (String line : text.split("\\R", -1)) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
