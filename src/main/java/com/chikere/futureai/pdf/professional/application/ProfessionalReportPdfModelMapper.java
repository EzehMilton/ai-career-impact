package com.chikere.futureai.pdf.professional.application;

import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;

public final class ProfessionalReportPdfModelMapper {
    public ProfessionalReportPdfModel from(PurchasedProfessionalReportSnapshot snapshot) {
        return new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE,
                snapshot.reportVersion(), snapshot.frozenAt(), snapshot.report());
    }
}
