package com.chikere.futureai.pdf.university.application;

import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;

public final class UniversityReportPdfModelMapper {
    public UniversityReportPdfModel from(PurchasedUniversityReportSnapshot snapshot) {
        return new UniversityReportPdfModel(UniversityReportPdfVersions.PDF_TEMPLATE,
                snapshot.reportVersion(), snapshot.frozenAt(), snapshot.report());
    }
}
