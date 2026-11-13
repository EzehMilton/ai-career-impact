package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.report.alevel.application.ALevelSubjectProfile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A versioned paid report, assembled from frozen artefacts: the stored subject profile, the
 * deterministic {@code a-level-guidance-policy-v1} guidance, the paid narrative (or its deterministic copy) and any
 * configured evidence. Plain values only, so it is stored as one JSON document and rendered identically on the web
 * page and in the PDF. Provenance is kept for audit and never shown.
 */
public record ALevelDetailedReport(
        String reportVersion,
        List<String> subjects,
        ALevelSubjectProfile profile,
        Perspectives perspectives,
        List<Entry> combinationShape,
        List<Entry> strengths,
        List<Entry> areasToStrengthen,
        Options options,
        AiStudy aiStudy,
        List<Entry> skillPriorities,
        ActionPlan actionPlan,
        List<Entry> safeguards,
        List<EvidenceItem> evidence,
        List<String> limitations,
        Provenance provenance) {

    public ALevelDetailedReport {
        Objects.requireNonNull(reportVersion);
        subjects = List.copyOf(subjects);
        Objects.requireNonNull(profile);
        Objects.requireNonNull(perspectives);
        combinationShape = List.copyOf(combinationShape);
        strengths = List.copyOf(strengths);
        areasToStrengthen = List.copyOf(areasToStrengthen);
        Objects.requireNonNull(options);
        Objects.requireNonNull(aiStudy);
        skillPriorities = List.copyOf(skillPriorities);
        Objects.requireNonNull(actionPlan);
        safeguards = List.copyOf(safeguards);
        evidence = List.copyOf(evidence);
        limitations = List.copyOf(limitations);
        Objects.requireNonNull(provenance);
    }

    /**
     * The five explanations: validated paid-narrative prose, or deterministic copy.
     *
     * @param modelWritten whether the text is the validated paid narrative
     */
    public record Perspectives(boolean modelWritten, String profile, String capability, String options,
            String aiLearning, String actionPlanIntroduction) {
    }

    /** @param provenance the frozen dimensions, measures or guidance-only inputs it came from; never shown */
    public record Entry(String title, String text, List<String> provenance) {
        public Entry {
            provenance = List.copyOf(provenance);
        }
    }

    /** @param gradesNote empty when no predicted grades were shared */
    public record Options(List<String> canSay, List<String> cannotSay, List<Entry> checks, String gradesNote) {
        public Options {
            canSay = List.copyOf(canSay);
            cannotSay = List.copyOf(cannotSay);
            checks = List.copyOf(checks);
            Objects.requireNonNull(gradesNote);
        }
    }

    public record AiStudy(String caution, List<Entry> uses) {
        public AiStudy {
            uses = List.copyOf(uses);
        }
    }

    public record ActionPlan(List<Entry> now, List<Entry> sixthForm, List<Entry> applications) {
        public ActionPlan {
            now = List.copyOf(now);
            sixthForm = List.copyOf(sixthForm);
            applications = List.copyOf(applications);
        }
    }

    /** A sourced factual note, shown apart from the assessment; it never changes the profile. */
    public record EvidenceItem(String category, String title, String summary, String sourceName, String sourceUrl,
            Instant retrievedAt) {
    }

    /**
     * The versions and source artefacts the report was assembled from. Internal; never shown to the customer.
     *
     * @param evidenceVersion empty when no evidence was stored for the assessment
     */
    public record Provenance(UUID assessmentId, String judgmentSchemaVersion, String decisionPolicyVersion,
            String guidancePolicyVersion, String paidNarrativeVersion, String paidNarrativeStatus,
            String evidenceVersion, String pdfVersion) {
    }
}
