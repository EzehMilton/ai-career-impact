package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV2;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeService;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeValidator;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

/**
 * The paid university-report-v2 lifecycle. It exists in every profile; without a configured paid-narrative provider
 * (dev, ordinary tests) the narrative is retryable and the deterministic report is shown provisionally.
 */
@Configuration(proxyBeanMethods = false)
public class UniversityDetailedReportConfiguration {

    @Bean
    UniversityPaidNarrativeService universityPaidNarrativeService(
            ObjectProvider<UniversityPaidNarrativeGenerator> generator, ObjectProvider<AssessmentTrace> trace) {
        AssessmentTrace active = trace.getIfAvailable(AssessmentTrace::disabled);
        return new UniversityPaidNarrativeService(() -> Optional.ofNullable(generator.getIfAvailable()),
                new UniversityPaidNarrativeValidator(active), active);
    }

    @Bean
    UniversityDetailedReportService universityDetailedReportService(UniversityPurchaseStore purchases,
            UniversityAssessmentStore assessments, UniversityModuleAnalysisService moduleAnalyses,
            UniversityModuleAnalysisStore moduleSnapshots, UniversityPaidNarrativeService narratives,
            UniversityDetailedReportStore store, ObjectProvider<AssessmentTrace> trace) {
        return new UniversityDetailedReportService(purchases, assessments, moduleAnalyses, moduleSnapshots,
                new UniversityGuidancePolicyV2(), narratives, new UniversityDetailedReportAssembler(), store,
                Clock.systemUTC(), trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    UniversityDetailedReportPdfService universityDetailedReportPdfService(UniversityDetailedReportUseCase reports,
            UniversityDetailedReportPdfRenderer renderer, ObjectProvider<AssessmentTrace> trace) {
        return new UniversityDetailedReportPdfService(reports, renderer, trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
