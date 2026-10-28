package com.chikere.futureai.report.alevel.detailed;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The paid a-level-report-v2 lifecycle, as the web layer uses it. */
public interface ALevelDetailedReportUseCase {

    /** The stored report, only with an ACTIVE v2 entitlement. Never generates anything. */
    Optional<ALevelDetailedReportSnapshot> find(UUID assessmentId);

    /**
     * The complete deterministic report with deterministic explanations, for an ACTIVE v2 purchase whose report is
     * not stored: shown after a preparation whose paid narrative was retryable. Assembled from stored artefacts;
     * nothing is called or stored, so it is never the canonical report.
     */
    Optional<ALevelDetailedReport> provisional(UUID assessmentId);

    /** Whether there is an ACTIVE v2 purchase, and whether its report is stored yet. Empty without one. */
    Optional<Preparation> preparation(UUID assessmentId);

    /**
     * Generates or reuses the report on the customer's explicit request. Never calls Jev: the subject profile is
     * already frozen in the assessment.
     *
     * @throws ALevelDetailedReportUnavailableException without an ACTIVE v2 purchase or a v2 assessment
     */
    Outcome prepare(UUID assessmentId);

    /** @param ready the report is stored */
    record Preparation(boolean ready) {
    }

    /** {@code stored} is empty when the report is provisional (the paid narrative is retryable). */
    record Outcome(Optional<ALevelDetailedReportSnapshot> stored, ALevelDetailedReport report, boolean reused) {
        public Outcome {
            Objects.requireNonNull(stored);
            Objects.requireNonNull(report);
        }

        public boolean provisional() {
            return stored.isEmpty();
        }
    }
}
