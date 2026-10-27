package com.chikere.futureai.report.professional.detailed;

/** Renders the shared report view as PDF bytes. Presentation only; it decides nothing. */
public interface ProfessionalDetailedReportPdfRenderer {

    byte[] render(ProfessionalDetailedReportView view);
}
