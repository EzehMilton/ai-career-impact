package com.chikere.futureai.narrative.alevel.application;

import java.util.UUID;

public interface ALevelNarrativeGeneration {
    StoredALevelNarrative generateFor(UUID assessmentId);
}
