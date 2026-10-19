package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryReportAccess;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceProvider;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceRequestFactory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceService;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContextFactory;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeService;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeValidator;
import com.chikere.futureai.payment.university.application.UniversityPaymentGateway;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseService;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportService;
import org.springframework.beans.factory.annotation.Value;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class UniversityAssessmentConfiguration {

    @Bean
    UniversityDecisionPolicyV1 universityDecisionPolicyV1() {
        return new UniversityDecisionPolicyV1();
    }

    @Bean
    UniversityAssessmentService universityAssessmentService(
            UniversityJudgmentProvider judgmentProvider,
            UniversityDecisionPolicyV1 decisionPolicy,
            UniversityAssessmentStore store,
            ObjectProvider<JourneyMetrics> metrics) {
        return new UniversityAssessmentService(judgmentProvider, decisionPolicy, store, Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled));
    }

    @Bean
    UniversityNarrativeService universityNarrativeService(
            UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore,
            UniversityNarrativeGenerator generator,
            ObjectProvider<JourneyMetrics> metrics) {
        return new UniversityNarrativeService(assessmentStore, narrativeStore, generator,
                new UniversityNarrativeContextFactory(), new UniversityNarrativeValidator(), Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled));
    }

    @Bean
    UniversityEvidenceService universityEvidenceService(
            UniversityAssessmentStore assessmentStore,
            UniversityEvidenceStore evidenceStore,
            Optional<UniversityEvidenceProvider> evidenceProvider,
            @Value("${app.university.evidence.geography:United Kingdom}") String geography,
            ObjectProvider<JourneyMetrics> metrics) {
        return new UniversityEvidenceService(assessmentStore, evidenceStore, evidenceProvider,
                new UniversityEvidenceRequestFactory(geography), Clock.systemUTC(), metrics.getIfAvailable(JourneyMetrics::disabled));
    }

    @Bean
    UniversityAssessmentWorkflow universityAssessmentWorkflow(
            UniversityAssessmentService assessmentService,
            UniversityNarrativeService narrativeService,
            UniversityEvidenceService evidenceService, ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ProviderCostTracker> costs) {
        return new UniversityAssessmentWorkflow(assessmentService, narrativeService, evidenceService,
                metrics.getIfAvailable(JourneyMetrics::disabled),
                costs.getIfAvailable(ProviderCostTracker::disabled));
    }

    @Bean
    UniversityReportService universityReportService(UniversityAssessmentStore assessmentStore,
            UniversityNarrativeStore narrativeStore, UniversityEvidenceStore evidenceStore) {
        return new UniversityReportService(assessmentStore, narrativeStore, evidenceStore,
                new UniversityReportAssembler());
    }

    @Bean
    UniversityReportPurchaseService universityReportPurchaseService(
            UniversityReportService reportService,
            UniversityPurchaseStore purchaseStore,
            Optional<UniversityPaymentGateway> paymentGateway,
            @Value("${app.university.report.base-url}") URI applicationBaseUrl,
            @Value("${app.university.report.display-price:}") String displayPrice,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ComplimentaryReportAccess> complimentaryAccess) {
        return new UniversityReportPurchaseService(reportService, purchaseStore, paymentGateway,
                applicationBaseUrl, Optional.ofNullable(displayPrice), Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                complimentaryAccess.getIfAvailable(ComplimentaryReportAccess::disabled));
    }
}
