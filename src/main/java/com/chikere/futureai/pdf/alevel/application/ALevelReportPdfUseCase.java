package com.chikere.futureai.pdf.alevel.application;

import java.util.UUID;

public interface ALevelReportPdfUseCase {
    PurchasedALevelReportPdf getOrCreate(UUID assessmentId);
}
