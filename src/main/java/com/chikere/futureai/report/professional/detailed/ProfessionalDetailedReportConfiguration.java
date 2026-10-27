package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalDetailedReportConfiguration {

    @Bean
    ProfessionalDetailedReportViewMapper professionalDetailedReportViewMapper() {
        return new ProfessionalDetailedReportViewMapper();
    }

    @Bean
    ProfessionalDetailedReportService professionalDetailedReportService(ProfessionalPurchaseStore purchases,
            ProfessionalAssessmentStore assessments, ProfessionalResponsibilityAnalysisService analyses,
            ProfessionalResponsibilityGuidanceService guidance, ProfessionalPaidNarrativeService narratives,
            ProfessionalDetailedReportStore store, ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalDetailedReportService(purchases, assessments, analyses, guidance, narratives,
                new ProfessionalDetailedReportAssembler(), store, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalDetailedReportPdfService professionalDetailedReportPdfService(ProfessionalPurchaseStore purchases,
            ProfessionalDetailedReportStore reports, ProfessionalDetailedReportUseCase detailedReports,
            ProfessionalDetailedReportPdfStore pdfs,
            ProfessionalDetailedReportViewMapper views, ProfessionalDetailedReportPdfRenderer renderer,
            @Value("${app.professional.report.pdf.maximum-bytes:5242880}") long maximumBytes) {
        return new ProfessionalDetailedReportPdfService(purchases, reports, detailedReports, pdfs, views, renderer,
                maximumBytes,
                Clock.systemUTC());
    }
}
