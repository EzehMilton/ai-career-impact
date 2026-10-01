package com.chikere.futureai.pdf.alevel.application;

public class ALevelReportPdfAccessDeniedException extends RuntimeException {
    public ALevelReportPdfAccessDeniedException() {
        super("an active A-level report entitlement is required");
    }
}
