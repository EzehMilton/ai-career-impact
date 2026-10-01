package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/** Coordinates persistence-first submission and the independent enrichment lifecycles. */
public final class ALevelAssessmentWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelAssessmentWorkflow.class);

    private final ALevelAssessmentService assessmentService;
    private final ALevelNarrativeService narrativeService;
    private final ALevelEvidenceService evidenceService;

    public ALevelAssessmentWorkflow(ALevelAssessmentService assessmentService,
            ALevelNarrativeService narrativeService, ALevelEvidenceService evidenceService) {
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
    }

    public UUID submit(ALevelAssessmentState state) {
        UUID id = assessmentService.submitALevelAssessment(state);
        try {
            narrativeService.generateFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            // The authoritative assessment has already committed. Do not turn an optional
            // presentation-layer failure into a failed assessment submission.
            LOGGER.error("Narrative lifecycle could not be persisted for A-level assessment {}",
                    id, persistenceFailure);
        }
        try {
            evidenceService.gatherFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            LOGGER.error("Evidence lifecycle could not be persisted for A-level assessment {}",
                    id, persistenceFailure);
        }
        return id;
    }
}
