package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGeneration;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceGathering;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationUseCase;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Coordinates persistence-first assessment submission and independent enrichment lifecycles. */
public final class ProfessionalAssessmentWorkflow implements ProfessionalAssessmentUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentWorkflow.class);
    private final ProfessionalAssessmentSubmission assessmentService;
    private final ProfessionalNarrativeGeneration narrativeService;
    private final ProfessionalEvidenceGathering evidenceService;
    private final ProfessionalOccupationClassificationUseCase classificationService;
    private final JourneyMetrics metrics;
    private final ProviderCostTracker costs;

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentSubmission assessmentService,
            ProfessionalNarrativeGeneration narrativeService,
            ProfessionalOccupationClassificationUseCase classificationService,
            ProfessionalEvidenceGathering evidenceService) {
        this(assessmentService, narrativeService, classificationService, evidenceService,
                JourneyMetrics.disabled());
    }

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentSubmission assessmentService,
            ProfessionalNarrativeGeneration narrativeService,
            ProfessionalOccupationClassificationUseCase classificationService,
            ProfessionalEvidenceGathering evidenceService,
            JourneyMetrics metrics) {
        this(assessmentService, narrativeService, classificationService, evidenceService, metrics,
                ProviderCostTracker.disabled());
    }

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentSubmission assessmentService,
            ProfessionalNarrativeGeneration narrativeService,
            ProfessionalOccupationClassificationUseCase classificationService,
            ProfessionalEvidenceGathering evidenceService,
            JourneyMetrics metrics,
            ProviderCostTracker costs) {
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.classificationService = Objects.requireNonNull(classificationService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
        this.metrics = Objects.requireNonNull(metrics);
        this.costs = Objects.requireNonNull(costs);
    }

    @Override
    public UUID submit(ProfessionalAssessmentState state) {
        return costs.trackAssessment("professional",
                () -> enrich(assessmentService.submitProfessionalAssessment(state)));
    }

    @Override
    public UUID submit(ProfessionalAssessmentState state, ProfessionalAssessmentInputMetadata inputMetadata) {
        return costs.trackAssessment("professional",
                () -> enrich(assessmentService.submitProfessionalAssessment(state, inputMetadata)));
    }

    private UUID enrich(UUID id) {
        metrics.assessmentCreated("professional");
        try {
            if (narrativeService.generateFor(id).status() == NarrativeStatus.FAILED) {
                metrics.narrativeFailed("professional");
            }
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
