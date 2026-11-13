package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.guidance.alevel.ALevelGuidancePolicyV1;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeService;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeValidator;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * The paid a-level-report-v2 lifecycle. It exists in every profile; without a configured paid-narrative provider
 * (dev, ordinary tests) the narrative is retryable and the deterministic report is shown provisionally.
 */
@Configuration(proxyBeanMethods = false)
public class ALevelDetailedReportConfiguration {

    @Bean
    ALevelPaidNarrativeService aLevelPaidNarrativeService(
            ObjectProvider<ALevelPaidNarrativeGenerator> generator, ObjectProvider<AssessmentTrace> trace) {
        AssessmentTrace active = trace.getIfAvailable(AssessmentTrace::disabled);
        return new ALevelPaidNarrativeService(() -> Optional.ofNullable(generator.getIfAvailable()),
                new ALevelPaidNarrativeValidator(active), active);
    }

    @Bean
    ALevelDetailedReportService aLevelDetailedReportService(ALevelPurchaseStore purchases,
            ALevelAssessmentStore assessments, ALevelEvidenceStore evidence, ALevelPaidNarrativeService narratives,
            ALevelDetailedReportStore store, ObjectProvider<AssessmentTrace> trace) {
        return new ALevelDetailedReportService(purchases, assessments, evidence, new ALevelGuidancePolicyV1(),
                narratives, ALevelDetailedReportAssembler.v3(), store, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelDetailedReportPdfService aLevelDetailedReportPdfService(ALevelDetailedReportUseCase reports,
            List<ALevelDetailedReportPdfRenderer> renderers, ObjectProvider<AssessmentTrace> trace) {
        return new ALevelDetailedReportPdfService(reports, renderers,
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
