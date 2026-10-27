package com.chikere.futureai.report.professional.detailed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The detailed Professional report as the web layer sees it. */
public interface ProfessionalDetailedReportUseCase {

    /** The stored report, only while the assessment's entitlement is ACTIVE. Never generates anything. */
    Optional<ProfessionalDetailedReportSnapshot> find(UUID assessmentId);

    /**
     * The provisional report while the paid narrative is retryable: no stored report yet, but a stored analysis and
     * guidance and no stored narrative. Assembled deterministically on each view, never stored and never calling a
     * provider; only while the entitlement is ACTIVE.
     */
    Optional<ProfessionalDetailedReport> provisional(UUID assessmentId);

    /** What preparing the report needs, only while the entitlement is ACTIVE. Never generates anything. */
    Optional<DetailedReportPreparation> preparation(UUID assessmentId);

    /**
     * Returns the stored report, or generates every missing artefact once (analysis, guidance, paid narrative) and
     * stores the assembled report. {@code selection} is the customer's choice when there are more than eight
     * responsibilities; it is ignored once an analysis exists, which keeps the analysed set frozen.
     */
    DetailedReportOutcome prepare(UUID assessmentId, Optional<List<Integer>> selection);

    /**
     * {@code snapshot} is the canonical stored report; it is empty when the paid narrative was retryable, and
     * {@code report} is then the provisional, unstored report.
     */
    record DetailedReportOutcome(Optional<ProfessionalDetailedReportSnapshot> snapshot, ProfessionalDetailedReport report,
            boolean reused) {
        public DetailedReportOutcome {
            java.util.Objects.requireNonNull(snapshot);
            java.util.Objects.requireNonNull(report);
        }

        public boolean provisional() {
            return snapshot.isEmpty();
        }
    }

    /**
     * {@code responsibilities} are the usable stored responsibilities in their original order; a selection names
     * 1-based positions in this list. {@code selectionRequired} only when no analysis exists yet and there are more
     * than {@code maximum}.
     */
    record DetailedReportPreparation(boolean reportReady, boolean analysisExists, boolean selectionRequired,
            List<String> responsibilities, int maximum) {
        public DetailedReportPreparation {
            responsibilities = List.copyOf(responsibilities);
        }
    }
}
