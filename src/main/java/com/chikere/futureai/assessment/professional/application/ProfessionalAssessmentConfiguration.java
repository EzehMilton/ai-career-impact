package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceRequestFactory;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContextFactory;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ProfessionalAssessmentConfiguration {

    @Bean
    ProfessionalDecisionPolicyV1 professionalDecisionPolicyV1() {
        return new ProfessionalDecisionPolicyV1();
    }

    @Bean
    ProfessionalAssessmentService professionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy,
            ProfessionalAssessmentStore store) {
        return new ProfessionalAssessmentService(judgmentProvider, decisionPolicy, store, Clock.systemUTC());
    }

    @Bean
    ProfessionalNarrativeService professionalNarrativeService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalNarrativeStore narrativeStore,
            ProfessionalNarrativeGenerator generator) {
        return new ProfessionalNarrativeService(assessmentStore, narrativeStore, generator,
                new ProfessionalNarrativeContextFactory(), new ProfessionalNarrativeValidator(),
                Clock.systemUTC());
    }

    @Bean
    ProfessionalAssessmentWorkflow professionalAssessmentWorkflow(
            ProfessionalAssessmentService assessmentService,
            ProfessionalNarrativeService narrativeService,
            ProfessionalEvidenceService evidenceService) {
        return new ProfessionalAssessmentWorkflow(assessmentService, narrativeService, evidenceService);
    }

    @Bean
    ProfessionalEvidenceService professionalEvidenceService(
            ProfessionalAssessmentStore assessmentStore,
            ProfessionalEvidenceStore evidenceStore,
            Optional<ProfessionalEvidenceProvider> evidenceProvider,
            @Value("${app.professional.evidence.geography:United Kingdom}") String geography,
            @Value("${app.professional.evidence.classified-profession:}") String classifiedProfession,
            @Value("${app.professional.evidence.soc2020-code:}") String soc2020Code) {
        return new ProfessionalEvidenceService(assessmentStore, evidenceStore, evidenceProvider,
                new ProfessionalEvidenceRequestFactory(geography, classifiedProfession, soc2020Code),
                Clock.systemUTC());
    }
}
