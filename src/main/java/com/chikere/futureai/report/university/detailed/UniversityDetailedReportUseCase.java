package com.chikere.futureai.report.university.detailed;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The paid university-report-v2 lifecycle, as the web layer uses it. */
public interface UniversityDetailedReportUseCase {

    /** The stored report, only with an ACTIVE entitlement. Never generates anything. */
    Optional<UniversityDetailedReportSnapshot> find(UUID assessmentId);

    /**
     * The complete deterministic report while its paid narrative is retryable: the module analysis exists but no
     * report is stored. Assembled from stored artefacts with deterministic copy; nothing is called or stored.
     */
    Optional<UniversityDetailedReport> provisional(UUID assessmentId);

    /** What the preparation step needs, only for an ACTIVE v2 purchase. */
    Optional<Preparation> preparation(UUID assessmentId);

    /** Generates or reuses the report on the customer's explicit request. */
    Outcome prepare(UUID assessmentId, Optional<List<Integer>> selection);

    /**
     * @param selectionRequired the course has more than eight modules and no analysis exists yet, so the customer must
     *     choose 1 to {@code maximum} before anything is generated; nothing is preselected
     * @param modules the course's modules in their original order
     */
    record Preparation(boolean ready, boolean analysisExists, boolean selectionRequired, List<String> modules,
            int maximum) {
        public Preparation {
            modules = List.copyOf(modules);
        }
    }

    /** {@code stored} is empty when the report is provisional (the paid narrative is retryable). */
    record Outcome(Optional<UniversityDetailedReportSnapshot> stored, UniversityDetailedReport report, boolean reused) {
        public Outcome {
            Objects.requireNonNull(stored);
            Objects.requireNonNull(report);
        }

        public boolean provisional() {
            return stored.isEmpty();
        }
    }
}
