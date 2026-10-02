package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationService;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Coordinates persistence-first assessment submission and independent enrichment lifecycles. */
public final class ProfessionalAssessmentWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentWorkflow.class);
    private final ProfessionalAssessmentService assessmentService;
    private final ProfessionalNarrativeService narrativeService;
    private final ProfessionalEvidenceService evidenceService;
    private final ProfessionalOccupationClassificationService classificationService;
    private final JourneyMetrics metrics;

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentService assessmentService,
            ProfessionalNarrativeService narrativeService,
            ProfessionalOccupationClassificationService classificationService,
            ProfessionalEvidenceService evidenceService) {
        this(assessmentService, narrativeService, classificationService, evidenceService,
                JourneyMetrics.disabled());
    }

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentService assessmentService,
            ProfessionalNarrativeService narrativeService,
            ProfessionalOccupationClassificationService classificationService,
            ProfessionalEvidenceService evidenceService,
            JourneyMetrics metrics) {
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.classificationService = Objects.requireNonNull(classificationService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public UUID submit(ProfessionalAssessmentState state) {
        return enrich(assessmentService.submitProfessionalAssessment(state));
    }

    public UUID submit(ProfessionalAssessmentState state, ProfessionalAssessmentInputMetadata inputMetadata) {
        return enrich(assessmentService.submitProfessionalAssessment(state, inputMetadata));
    }

    private UUID enrich(UUID id) {
        metrics.assessmentCreated("professional");
        try {
            narrativeService.generateFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            // The authoritative assessment has already committed. Do not turn an optional
            // presentation-layer failure into a failed assessment submission.
            LOGGER.error("Narrative lifecycle could not be persisted for assessment {}", id, persistenceFailure);
            metrics.narrativeFailed("professional");
        }
        try {
            classificationService.classifyFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            LOGGER.error("Occupation classification could not be persisted for assessment {}", id, persistenceFailure);
        }
        try {
            evidenceService.gatherFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            LOGGER.error("Evidence lifecycle could not be persisted for assessment {}", id, persistenceFailure);
            metrics.evidenceFailed("professional");
        }
        return id;
    }
}
