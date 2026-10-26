package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicy;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceRequestFactory;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContextFactory;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeValidator;
import com.chikere.futureai.occupation.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportService;
import org.springframework.beans.factory.annotation.Value;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.springframework.beans.factory.ObjectProvider;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ProfessionalAssessmentConfiguration {

    /** New assessments are decided under v2; stored v1 assessments keep their saved decision. */
    @Bean
    ProfessionalDecisionPolicy professionalDecisionPolicy(ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalDecisionPolicyV2(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalAssessmentService professionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicy decisionPolicy,
            ProfessionalAssessmentStore store,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalAssessmentService(judgmentProvider, decisionPolicy, store, Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalNarrativeService professionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalNarrativeService(assessmentStore, narrativeStore, generator,
                new ProfessionalNarrativeContextFactory(),
                new ProfessionalNarrativeValidator(trace.getIfAvailable(AssessmentTrace::disabled)),
                Clock.systemUTC(), metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalAssessmentWorkflow professionalAssessmentWorkflow(
            ProfessionalAssessmentService assessmentService,
            ProfessionalNarrativeService narrativeService,
            ProfessionalOccupationClassificationService classificationService,
            ProfessionalEvidenceService evidenceService, ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ProviderCostTracker> costs,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalAssessmentWorkflow(assessmentService, narrativeService,
                classificationService, evidenceService,
                metrics.getIfAvailable(JourneyMetrics::disabled),
                costs.getIfAvailable(ProviderCostTracker::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalOccupationClassificationService professionalOccupationClassificationService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalOccupationClassificationStore classificationStore,
            Optional<ProfessionalOccupationClassifier> classifier,
            @Value("${app.professional.evidence.geography:United Kingdom}") String geography,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalOccupationClassificationService(assessmentStore, classificationStore,
                classifier, geography, Clock.systemUTC(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalEvidenceService professionalEvidenceService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore,
            Optional<ProfessionalEvidenceProvider> evidenceProvider,
            ProfessionalOccupationClassificationStore classificationStore,
            @Value("${app.professional.evidence.geography:United Kingdom}") String geography,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalEvidenceService(assessmentStore, evidenceStore, evidenceProvider,
                classificationStore, new ProfessionalEvidenceRequestFactory(geography),
                Clock.systemUTC(), metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ProfessionalReportService professionalReportService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore, ProfessionalEvidenceStore evidenceStore,
            ProfessionalOccupationClassificationStore classificationStore,
            ObjectProvider<AssessmentTrace> trace) {
        return new ProfessionalReportService(assessmentStore, narrativeStore, evidenceStore,
                classificationStore, new ProfessionalReportAssembler(),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }
}
