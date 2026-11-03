package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The immutable detailed-report content, assembled once from stored artefacts. Every customer-facing sentence is fixed
 * here, so HTML and PDF only present it. Fields documented as nullable hold the paid narrative's explanation and are
 * null when the narrative is unavailable; every section is complete without them.
 *
 * <p>One record serves both stored versions. Fields added for {@value ProfessionalDetailedReportVersions#REPORT_V4}
 * are absent from a stored {@value ProfessionalDetailedReportVersions#REPORT_V3} document and read back as null or
 * empty, so a v3 report deserialises exactly as it was stored and its own template never shows them.</p>
 */
public record ProfessionalDetailedReport(
        String reportVersion,
        Provenance provenance,
        boolean developmentFixture,
        Cover cover,
        PaidNarrativeStatus narrativeStatus,
        List<String> executiveSummary,
        List<Measure> measures,
        String scoresTogether,
        Factors factors,
        ResponsibilitySection responsibilities,
        OpportunitySection opportunities,
        HumanValueSection humanValue,
        SkillSection skills,
        SafeguardSection safeguards,
        PlanSection plan,
        AdaptationStrategy adaptationStrategy,
        List<String> limitations,
        List<String> nextSteps,
        String closingPerspective) {

    public ProfessionalDetailedReport {
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(provenance);
        Objects.requireNonNull(cover);
        Objects.requireNonNull(narrativeStatus);
        executiveSummary = List.copyOf(executiveSummary);
        measures = List.copyOf(measures);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(responsibilities);
        Objects.requireNonNull(opportunities);
        Objects.requireNonNull(humanValue);
        Objects.requireNonNull(skills);
        Objects.requireNonNull(safeguards);
        Objects.requireNonNull(plan);
        limitations = List.copyOf(limitations);
        nextSteps = List.copyOf(nextSteps);
    }

    /** Which versioned artefacts this report was assembled from. Audit data; never shown as methodology. */
    public record Provenance(String roleReportVersion, String judgmentSchemaVersion, String decisionPolicyVersion,
            String roleNarrativeVersion, String responsibilitySchemaVersion, String responsibilityPolicyVersion,
            String guidancePolicyVersion, String paidNarrativeVersion) {
    }

    public record Cover(String title, String profession, Instant assessedAt, String subtitle) {
    }

    /** The frozen role-level score and band, exactly as purchased. Formatted only when displayed. */
    public record Measure(ProfessionalMeasure measure, String name, double indicator, IndicatorBand band,
            String explanation) {
    }

    public record Factors(List<String> raising, List<String> lowering, String raisingEmpty, String loweringEmpty) {
        public Factors {
            raising = List.copyOf(raising);
            lowering = List.copyOf(lowering);
        }
    }

    /** {@code narrative} and {@code oversightNote} are nullable. */
    public record ResponsibilitySection(String introduction, String narrative, String oversightNote,
            List<ResponsibilityCard> cards) {
        public ResponsibilitySection {
            cards = List.copyOf(cards);
        }
    }

    public record ResponsibilityCard(String key, String text, ResponsibilityCategory category, String categoryLabel,
            String explanation, List<String> signals, boolean showOversight) {
        public ResponsibilityCard {
            signals = List.copyOf(signals);
        }
    }

    public record OpportunitySection(String introduction, List<Opportunity> items) {
        public OpportunitySection {
            items = List.copyOf(items);
        }
    }

    /**
     * {@code explanation}, {@code practicalAiUses}, {@code humanOwnership} and {@code firstExperiment} are the paid
     * narrative's (nullable or empty). In v4 {@code conditions} holds only conditions specific to this responsibility
     * or not covered by a listed safeguard; {@code appliedSafeguards} names the listed safeguards that also apply.
     */
    public record Opportunity(int rank, String key, String responsibility, String categoryLabel, String whyCandidate,
            String howAiMayAssist, List<String> conditions, String explanation, List<String> practicalAiUses,
            List<String> humanOwnership, String firstExperiment, List<String> appliedSafeguards) {
        public Opportunity {
            conditions = List.copyOf(conditions);
            practicalAiUses = copy(practicalAiUses);
            humanOwnership = copy(humanOwnership);
            appliedSafeguards = copy(appliedSafeguards);
        }
    }

    public record HumanValueSection(String introduction, List<HumanValue> items) {
        public HumanValueSection {
            items = List.copyOf(items);
        }
    }

    /** {@code explanation} ("why you matter here") and {@code strengthen} are the paid narrative's, nullable. */
    public record HumanValue(int rank, String key, String responsibility, String reason, String reasonExplanation,
            String explanation, String strengthen) {
    }

    /** {@code narrative} is nullable. */
    public record SkillSection(String introduction, String narrative, List<Skill> items) {
        public SkillSection {
            items = List.copyOf(items);
        }
    }

    /** {@code explanation} and {@code inYourWork} ("in your work, this means") are the paid narrative's. */
    public record Skill(int rank, String label, String reason, List<String> supportingResponsibilities,
            String explanation, List<String> inYourWork) {
        public Skill {
            supportingResponsibilities = List.copyOf(supportingResponsibilities);
            inYourWork = copy(inYourWork);
        }
    }

    public record SafeguardSection(String introduction, List<Safeguard> items) {
        public SafeguardSection {
            items = List.copyOf(items);
        }
    }

    public record Safeguard(String title, String guidance, boolean essential) {
    }

    /** {@code narrative} is nullable. */
    public record PlanSection(String introduction, String narrative, List<Phase> phases) {
        public PlanSection {
            phases = List.copyOf(phases);
        }
    }

    public record Phase(String heading, String purpose, List<Action> actions) {
        public Phase {
            actions = List.copyOf(actions);
        }
    }

    /** {@code reviewNote} may be empty; {@code note} is the paid narrative's, nullable. */
    public record Action(String title, String rationale, String reviewNote, String note) {
    }

    /**
     * v4: Protect, Learn, Experiment, Strengthen, Monitor. From the paid narrative when available ({@code
     * personalised}), otherwise composed deterministically from the stored guidance. Never a prediction.
     */
    public record AdaptationStrategy(List<String> protect, List<String> learn, List<String> experiment,
            List<String> strengthen, List<String> monitor, boolean personalised) {
        public AdaptationStrategy {
            protect = copy(protect);
            learn = copy(learn);
            experiment = copy(experiment);
            strengthen = copy(strengthen);
            monitor = copy(monitor);
        }
    }

    /** Lists added in v4 are absent from a stored v3 document. */
    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
