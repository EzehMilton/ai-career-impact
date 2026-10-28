package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.report.university.application.UniversityCourseProfile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The {@code university-report-v2} paid report, assembled from frozen artefacts: the stored course profile, the
 * immutable module analysis, the deterministic guidance and the paid narrative (or its deterministic copy). Plain
 * values only, so it is stored as one JSON document and rendered identically on the web page and in the PDF. It holds
 * no rule id, probability, close-call or fallback marker.
 */
public record UniversityDetailedReport(
        String reportVersion,
        String courseTitle,
        String institution,
        UniversityCourseProfile profile,
        Perspectives perspectives,
        Modules modules,
        String aiLearningCaution,
        List<Entry> aiLearning,
        List<Entry> ownCapability,
        List<Entry> technicalFoundations,
        List<Skill> skills,
        List<Entry> safeguards,
        List<Phase> plan,
        List<String> limitations,
        Provenance provenance) {

    public UniversityDetailedReport {
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(courseTitle);
        institution = institution == null ? "" : institution;
        Objects.requireNonNull(profile);
        Objects.requireNonNull(perspectives);
        Objects.requireNonNull(modules);
        Objects.requireNonNull(aiLearningCaution);
        aiLearning = List.copyOf(aiLearning);
        ownCapability = List.copyOf(ownCapability);
        technicalFoundations = List.copyOf(technicalFoundations);
        skills = List.copyOf(skills);
        safeguards = List.copyOf(safeguards);
        plan = List.copyOf(plan);
        limitations = List.copyOf(limitations);
        Objects.requireNonNull(provenance);
    }

    /**
     * The five narrative perspectives: validated paid-narrative prose, or deterministic copy.
     *
     * @param modelWritten whether the text is the validated paid narrative
     */
    public record Perspectives(boolean modelWritten, String overall, String aiLearning, String humanPractical,
            String transferable, String actionPlanIntroduction) {
    }

    /** The analysed modules, in course order, with how they were chosen. */
    public record Modules(String selectionNote, List<ModuleCard> cards) {
        public Modules {
            cards = List.copyOf(cards);
        }
    }

    /** One module: its customer-facing classification, a short explanation and its safeguards, if any. */
    public record ModuleCard(int moduleId, String title, String classification, String explanation,
            List<String> notes) {
        public ModuleCard {
            notes = List.copyOf(notes);
        }
    }

    public record Entry(String title, String text) {
    }

    public record Skill(String title, String explanation, List<String> modules) {
        public Skill {
            modules = List.copyOf(modules);
        }
    }

    public record Phase(String title, List<String> actions) {
        public Phase {
            actions = List.copyOf(actions);
        }
    }

    /** The versions and source artefacts the report was assembled from. Internal; the page shows only versions. */
    public record Provenance(UUID assessmentId, UUID moduleAnalysisId, String decisionPolicyVersion,
            String moduleSchemaVersion, String modulePolicyVersion, String guidancePolicyVersion,
            String paidNarrativeVersion, String paidNarrativeStatus, String pdfVersion) {
    }
}
