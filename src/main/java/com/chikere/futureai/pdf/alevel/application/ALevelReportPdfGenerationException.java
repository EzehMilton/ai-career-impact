package com.chikere.futureai.pdf.alevel.application;

public class ALevelReportPdfGenerationException extends RuntimeException {
    public ALevelReportPdfGenerationException(String message) {
        super(message);
    }

    public ALevelReportPdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
