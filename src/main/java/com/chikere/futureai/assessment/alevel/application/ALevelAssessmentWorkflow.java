package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceGathering;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGeneration;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Coordinates persistence-first submission and the independent enrichment lifecycles. */
public final class ALevelAssessmentWorkflow implements ALevelAssessmentUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelAssessmentWorkflow.class);

    private final ALevelAssessmentSubmission assessmentService;
    private final ALevelNarrativeGeneration narrativeService;
    private final ALevelEvidenceGathering evidenceService;
    private final JourneyMetrics metrics;
    private final ProviderCostTracker costs;
    private final AssessmentTrace trace;

    public ALevelAssessmentWorkflow(ALevelAssessmentSubmission assessmentService,
            ALevelNarrativeGeneration narrativeService, ALevelEvidenceGathering evidenceService) {
        this(assessmentService, narrativeService, evidenceService, JourneyMetrics.disabled());
    }

    public ALevelAssessmentWorkflow(ALevelAssessmentSubmission assessmentService,
            ALevelNarrativeGeneration narrativeService, ALevelEvidenceGathering evidenceService,
            JourneyMetrics metrics) {
        this(assessmentService, narrativeService, evidenceService, metrics, ProviderCostTracker.disabled());
    }

    public ALevelAssessmentWorkflow(ALevelAssessmentSubmission assessmentService,
            ALevelNarrativeGeneration narrativeService, ALevelEvidenceGathering evidenceService,
            JourneyMetrics metrics, ProviderCostTracker costs) {
        this(assessmentService, narrativeService, evidenceService, metrics, costs, AssessmentTrace.disabled());
    }

    public ALevelAssessmentWorkflow(ALevelAssessmentSubmission assessmentService,
            ALevelNarrativeGeneration narrativeService, ALevelEvidenceGathering evidenceService,
            JourneyMetrics metrics, ProviderCostTracker costs, AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
        this.metrics = Objects.requireNonNull(metrics);
        this.costs = Objects.requireNonNull(costs);
    }

    @Override
    public UUID submit(ALevelAssessmentState state) {
        return trace.assessment(JourneyType.A_LEVEL,
                () -> costs.trackAssessment("a-level", () -> submitAndEnrich(state)));
    }

    private UUID submitAndEnrich(ALevelAssessmentState state) {
        UUID id = assessmentService.submitALevelAssessment(state);
        metrics.assessmentCreated("a-level");
        try {
            if (narrativeService.generateFor(id).status() == NarrativeStatus.FAILED) {
                metrics.narrativeFailed("a-level");
            }
        }
        catch (RuntimeException persistenceFailure) {
            // The authoritative assessment has already committed. Do not turn an optional
            // presentation-layer failure into a failed assessment submission.
            LOGGER.error("Narrative lifecycle could not be persisted for A-level assessment {}",
                    id, persistenceFailure);
            metrics.narrativeFailed("a-level");
        }
        try {
            evidenceService.gatherFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            LOGGER.error("Evidence lifecycle could not be persisted for A-level assessment {}",
                    id, persistenceFailure);
            metrics.evidenceFailed("a-level");
        }
        return id;
    }
}
