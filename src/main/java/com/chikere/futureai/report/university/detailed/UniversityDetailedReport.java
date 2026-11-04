package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.report.university.application.UniversityCourseProfile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The paid detailed report, assembled from frozen artefacts: the stored course profile, the immutable module analysis,
 * the deterministic guidance and the paid narrative (or its deterministic copy). Plain values only, so it is stored as
 * one JSON document and rendered identically on the web page and in the PDF. It holds no rule id, probability,
 * close-call or fallback marker.
 *
 * <p>{@code university-report-v3} adds the practical layer: {@link #practical()}, each skill's
 * {@link Skill#inThisCourse()}, each plan phase's {@link Phase#steps()} and the narrative's course pattern and closing.
 * These are absent from a stored {@code university-report-v2} document and read back as null or empty, so a v2 report
 * deserialises exactly as it was stored and its own page and PDF never show them.</p>
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
        Provenance provenance,
        Practical practical) {

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
     * The narrative perspectives: validated paid-narrative prose, or deterministic copy.
     *
     * @param modelWritten whether the text is the validated paid narrative
     * @param coursePattern v3: the pattern across the analysed modules; null in v2 and without a narrative
     * @param closing v3: the closing perspective; null in v2 and without a narrative
     */
    public record Perspectives(boolean modelWritten, String overall, String aiLearning, String humanPractical,
            String transferable, String actionPlanIntroduction, String coursePattern, String closing) {
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

    /** @param inThisCourse v3: how the unchanged skill shows up in this course; empty in v2 or without a narrative */
    public record Skill(String title, String explanation, List<String> modules, List<String> inThisCourse) {
        public Skill {
            modules = List.copyOf(modules);
            inThisCourse = inThisCourse == null ? List.of() : List.copyOf(inThisCourse);
        }
    }

    /** @param steps v3: each action with its practical note, if any; empty in v2 */
    public record Phase(String title, List<String> actions, List<Step> steps) {
        public Phase {
            actions = List.copyOf(actions);
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    /** One deterministic action and the narrative note that makes it concrete, or null when there is none. */
    public record Step(String text, String note) {
    }

    /**
     * v3: the module-by-module practical layer and the AI-study strategy. Null in a stored v2 report.
     *
     * @param classificationNotes each classification shown on the module cards, with its explanation, once
     * @param technicalSummary the technical-foundations guidance in one statement, worded for the course
     */
    public record Practical(List<Entry> classificationNotes, List<ModuleLearning> moduleLearning,
            List<CapabilityFocus> capabilityFocus, String technicalSummary, StudyStrategy studyStrategy) {
        public Practical {
            classificationNotes = List.copyOf(classificationNotes);
            moduleLearning = List.copyOf(moduleLearning);
            capabilityFocus = List.copyOf(capabilityFocus);
            Objects.requireNonNull(studyStrategy);
        }
    }

    /**
     * One module selected for AI-learning guidance. {@code guidance} is the fixed sentence; the rest is the
     * narrative's interpretation, absent without one.
     */
    public record ModuleLearning(String title, String classification, String guidance, String explanation,
            List<String> uses, List<String> independent, String experiment) {
        public ModuleLearning {
            uses = List.copyOf(uses);
            independent = List.copyOf(independent);
        }
    }

    /** One module where your own capability matters most: the fixed sentence, then why and what to practise. */
    public record CapabilityFocus(String title, String guidance, String explanation, String independentPractice) {
    }

    /** How to study in an AI-shaped university. {@code personalised}: from the narrative, not deterministic copy. */
    public record StudyStrategy(boolean personalised, List<String> useAi, List<String> learnWithoutAi,
            List<String> verify, List<String> demonstrateOwnCapability, List<String> buildEvidence) {
        public StudyStrategy {
            useAi = List.copyOf(useAi);
            learnWithoutAi = List.copyOf(learnWithoutAi);
            verify = List.copyOf(verify);
            demonstrateOwnCapability = List.copyOf(demonstrateOwnCapability);
            buildEvidence = List.copyOf(buildEvidence);
        }
    }

    /** The versions and source artefacts the report was assembled from. Internal; the page shows only versions. */
    public record Provenance(UUID assessmentId, UUID moduleAnalysisId, String decisionPolicyVersion,
            String moduleSchemaVersion, String modulePolicyVersion, String guidancePolicyVersion,
            String paidNarrativeVersion, String paidNarrativeStatus, String pdfVersion) {
    }
}
