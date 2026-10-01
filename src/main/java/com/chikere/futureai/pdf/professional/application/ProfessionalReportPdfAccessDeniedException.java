package com.chikere.futureai.pdf.professional.application;

public final class ProfessionalReportPdfAccessDeniedException extends RuntimeException {
    public ProfessionalReportPdfAccessDeniedException() {
        super("an active purchased report entitlement is required");
    }
}
