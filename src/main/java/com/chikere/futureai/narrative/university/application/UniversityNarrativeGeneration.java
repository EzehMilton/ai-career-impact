package com.chikere.futureai.narrative.university.application;

import java.util.UUID;

public interface UniversityNarrativeGeneration {
    StoredUniversityNarrative generateFor(UUID assessmentId);
}
