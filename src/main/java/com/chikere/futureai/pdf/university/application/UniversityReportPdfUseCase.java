package com.chikere.futureai.pdf.university.application;

import java.util.UUID;

public interface UniversityReportPdfUseCase {
    PurchasedUniversityReportPdf getOrCreate(UUID assessmentId);
}
