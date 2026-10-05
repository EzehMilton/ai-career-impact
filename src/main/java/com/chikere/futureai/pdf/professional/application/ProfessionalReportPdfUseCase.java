package com.chikere.futureai.pdf.professional.application;

import java.util.UUID;

public interface ProfessionalReportPdfUseCase {
    PurchasedProfessionalReportPdf getOrCreate(UUID assessmentId);
}
