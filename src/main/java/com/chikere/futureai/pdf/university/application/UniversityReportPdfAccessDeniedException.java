package com.chikere.futureai.pdf.university.application;

public class UniversityReportPdfAccessDeniedException extends RuntimeException {
    public UniversityReportPdfAccessDeniedException() {
        super("an active university report entitlement is required");
    }
}
