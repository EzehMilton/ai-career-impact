package com.chikere.futureai.narrative.professional.application;

import java.util.UUID;

public interface ProfessionalNarrativeGeneration {
    StoredProfessionalNarrative generateFor(UUID assessmentId);
}
