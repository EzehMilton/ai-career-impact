package com.chikere.futureai.cv.professional.web;

final class ProfessionalCvUploadValidationException extends RuntimeException {
    private final int status;

    ProfessionalCvUploadValidationException(String message, int status) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
