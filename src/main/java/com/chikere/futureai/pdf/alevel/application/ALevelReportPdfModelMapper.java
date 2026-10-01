package com.chikere.futureai.pdf.alevel.application;

import com.chikere.futureai.payment.alevel.application.PurchasedALevelReportSnapshot;

public final class ALevelReportPdfModelMapper {
    public ALevelReportPdfModel from(PurchasedALevelReportSnapshot snapshot) {
        return new ALevelReportPdfModel(ALevelReportPdfVersions.PDF_TEMPLATE,
                snapshot.reportVersion(), snapshot.frozenAt(), snapshot.report());
    }
}
