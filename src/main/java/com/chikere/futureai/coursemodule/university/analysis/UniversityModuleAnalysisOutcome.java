package com.chikere.futureai.coursemodule.university.analysis;

import java.util.Objects;

/** The stored snapshot, and whether it already existed (no provider call on this request) or was generated now. */
public record UniversityModuleAnalysisOutcome(UniversityModuleAnalysisSnapshot snapshot, boolean reused) {

    public UniversityModuleAnalysisOutcome {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    }
}
