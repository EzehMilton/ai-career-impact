package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryReportAccess;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceProvider;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceRequestFactory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceService;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContextFactory;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeValidator;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportPresentationVersions;
import com.chikere.futureai.report.alevel.application.ALevelReportService;
import org.springframework.beans.factory.annotation.Value;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.springframework.beans.factory.ObjectProvider;
import com.chikere.futureai.trace.AssessmentTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ALevelAssessmentConfiguration {

    /** New submissions are decided by {@code a-level-decision-v2}; stored v1 decisions are only read back. */
    @Bean
    ALevelDecisionPolicyV2 aLevelDecisionPolicyV2(ObjectProvider<AssessmentTrace> trace) {
        return new ALevelDecisionPolicyV2(trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelAssessmentService aLevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV2 decisionPolicy, ALevelAssessmentStore store,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelAssessmentService(judgmentProvider, decisionPolicy, store, Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelNarrativeService aLevelNarrativeService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelNarrativeGenerator generator,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelNarrativeService(assessmentStore, narrativeStore, generator,
                new ALevelNarrativeContextFactory(),
                new ALevelNarrativeValidator(trace.getIfAvailable(AssessmentTrace::disabled)),
                Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelEvidenceService aLevelEvidenceService(ALevelAssessmentStore assessmentStore,
            ALevelEvidenceStore evidenceStore, Optional<ALevelEvidenceProvider> evidenceProvider,
            @Value("${app.a-level.evidence.geography:United Kingdom}") String geography,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelEvidenceService(assessmentStore, evidenceStore, evidenceProvider,
                new ALevelEvidenceRequestFactory(geography), Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelAssessmentWorkflow aLevelAssessmentWorkflow(ALevelAssessmentService assessmentService,
            ALevelNarrativeService narrativeService, ALevelEvidenceService evidenceService,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ProviderCostTracker> costs,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelAssessmentWorkflow(assessmentService, narrativeService, evidenceService,
                metrics.getIfAvailable(JourneyMetrics::disabled),
                costs.getIfAvailable(ProviderCostTracker::disabled),
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelReportPresentationVersions aLevelReportPresentationVersions(
            @Value("${app.a-level.presentation-v3-cutover:9999-12-31T23:59:59Z}") String configuredCutover) {
        return new ALevelReportPresentationVersions(configuredCutover);
    }

    @Bean
    ALevelReportService aLevelReportService(ALevelAssessmentStore assessmentStore,
            ALevelNarrativeStore narrativeStore, ALevelEvidenceStore evidenceStore,
            ALevelReportPresentationVersions presentationVersions,
            ObjectProvider<AssessmentTrace> trace) {
        return new ALevelReportService(assessmentStore, narrativeStore, evidenceStore,
                new ALevelReportAssembler(), presentationVersions,
                trace.getIfAvailable(AssessmentTrace::disabled));
    }

    @Bean
    ALevelReportPurchaseService aLevelReportPurchaseService(ALevelReportService reportService,
            ALevelPurchaseStore purchaseStore, Optional<ALevelPaymentGateway> paymentGateway,
            @Value("${app.a-level.report.base-url}") URI applicationBaseUrl,
            @Value("${app.a-level.report.display-price:}") String displayPrice,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ComplimentaryReportAccess> complimentaryAccess) {
        return new ALevelReportPurchaseService(reportService, purchaseStore, paymentGateway,
                applicationBaseUrl, Optional.ofNullable(displayPrice), Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                complimentaryAccess.getIfAvailable(ComplimentaryReportAccess::disabled));
    }
}
