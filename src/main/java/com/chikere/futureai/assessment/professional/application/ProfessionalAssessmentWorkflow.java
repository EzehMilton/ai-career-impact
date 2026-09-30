package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationService;
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

    public ProfessionalAssessmentWorkflow(
            ProfessionalAssessmentService assessmentService,
            ProfessionalNarrativeService narrativeService,
            ProfessionalOccupationClassificationService classificationService,
            ProfessionalEvidenceService evidenceService) {
        this.assessmentService = Objects.requireNonNull(assessmentService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
        this.classificationService = Objects.requireNonNull(classificationService);
        this.evidenceService = Objects.requireNonNull(evidenceService);
    }

    public UUID submit(ProfessionalAssessmentState state) {
        UUID id = assessmentService.submitProfessionalAssessment(state);
        try {
            narrativeService.generateFor(id);
        }
        catch (RuntimeException persistenceFailure) {
            // The authoritative assessment has already committed. Do not turn an optional
            // presentation-layer failure into a failed assessment submission.
            LOGGER.error("Narrative lifecycle could not be persisted for assessment {}", id, persistenceFailure);
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
        }
        return id;
    }
}
