package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.cv.professional.application.ProfessionalCvDocument;
import com.chikere.futureai.cv.professional.application.ProfessionalCvDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Component
final class ProfessionalCvUploadValidator {
    private static final Map<String, ProfessionalCvDocumentType> TYPES = Map.of(
            ProfessionalCvDocumentType.PDF.mediaType(), ProfessionalCvDocumentType.PDF,
            ProfessionalCvDocumentType.DOCX.mediaType(), ProfessionalCvDocumentType.DOCX);
    private final long maximumBytes;

    ProfessionalCvUploadValidator(@Value("${app.professional.cv.maximum-bytes:5242880}") long maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    ProfessionalCvDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new ProfessionalCvUploadValidationException("Choose a PDF or DOCX CV to upload.", 400);
        }
        if (file.getSize() > maximumBytes) {
            throw new ProfessionalCvUploadValidationException("The CV is too large. Upload a file no larger than 5 MiB.", 413);
        }
        String filename = sanitiseFilename(file.getOriginalFilename());
        ProfessionalCvDocumentType type = file.getContentType() == null ? null : TYPES.get(file.getContentType());
        if (type == null || !filename.toLowerCase(Locale.ROOT).endsWith(type.extension())) {
            throw new ProfessionalCvUploadValidationException("Upload a genuine PDF or DOCX file.", 400);
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasSignature(type, bytes)) {
                throw new ProfessionalCvUploadValidationException("The file content does not match its PDF or DOCX type.", 400);
            }
            return new ProfessionalCvDocument(filename, type, bytes);
        }
        catch (IOException exception) {
            throw new ProfessionalCvUploadValidationException("The CV could not be read. Please try another file.", 400);
        }
    }

    static String sanitiseFilename(String supplied) {
        String value = supplied == null ? "cv" : supplied.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_")
                .replaceAll("\\s+", " ").strip();
        if (value.isBlank() || value.equals(".") || value.equals("..")) value = "cv";
        if (value.length() > 120) value = value.substring(value.length() - 120);
        return value;
    }

    private boolean hasSignature(ProfessionalCvDocumentType type, byte[] bytes) {
        return switch (type) {
            case PDF -> bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                    && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case DOCX -> bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                    && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                    && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
        };
    }
}
