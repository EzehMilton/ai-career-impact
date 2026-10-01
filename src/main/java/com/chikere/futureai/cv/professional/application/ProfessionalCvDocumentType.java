package com.chikere.futureai.cv.professional.application;

public enum ProfessionalCvDocumentType {
    PDF("application/pdf", ".pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");

    private final String mediaType;
    private final String extension;

    ProfessionalCvDocumentType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }
}
