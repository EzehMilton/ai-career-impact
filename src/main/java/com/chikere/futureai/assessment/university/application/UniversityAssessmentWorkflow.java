package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceGathering;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGeneration;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Coordinates persistence-first submission and the independent enrichment lifecycles. */
public final class UniversityAssessmentWorkflow implements UniversityAssessmentUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityAssessmentWorkflow.class);

    private final UniversityAssessmentSubmission assessmentService;
    private final UniversityNarrativeGeneration narrativeService;
    private final UniversityEvidenceGathering evidenceService;
    private final JourneyMetrics metrics;

    public UniversityAssessmentWorkflow(UniversityAssessmentSubmission assessmentService,
            UniversityNarrativeGeneration narrativeService, UniversityEvidenceGathering evidenceService) {
        this(assessmentService, narrativeService, evidenceService, JourneyMetrics.disabled());
    }

    public UniversityAssessmentWorkflow(UniversityAssessmentSubmission assessmentService,
            UniversityNarrativeGeneration narrativeService, UniversityEvidenceGathering evidenceService,
            JourneyMetrics metrics) {
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public UUID submit(UniversityAssessmentState state) {
        UUID id = assessmentService.submitUniversityAssessment(state);
        metrics.assessmentCreated("university");
        try {
            if (narrativeService.generateFor(id).status() == NarrativeStatus.FAILED) {
                metrics.narrativeFailed("university");
            }
        }
        catch (RuntimeException persistenceFailure) {
            // The authoritative assessment has already committed. Do not turn an optional
            // presentation-layer failure into a failed assessment submission.
            LOGGER.error("Narrative lifecycle could not be persisted for university assessment {}",
                    id, persistenceFailure);
            metrics.narrativeFailed("university");
        }
        try {
            evidenceService.gatherFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            LOGGER.error("Evidence lifecycle could not be persisted for university assessment {}",
                    id, persistenceFailure);
            metrics.evidenceFailed("university");
        }
        return id;
    }
}
