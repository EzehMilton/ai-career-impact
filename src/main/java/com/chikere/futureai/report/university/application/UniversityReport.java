package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, delivery-neutral composition suitable for the web page and the PDF renderer. */
public record UniversityReport(
        String reportVersion,
        UniversityReportTier tier,
        boolean developmentFixture,
        Instant assessedAt,
        String courseTitle,
        Optional<String> institution,
        String headline,
        AssessmentSummary assessmentSummary,
        List<MeasureSummary> measures,
        List<String> keyStrengths,
        List<String> developmentAreas,
        NarrativeContent narrative,
        List<String> practicalActions,
        List<SkillDevelopmentPriority> skillPriorities,
        CareerAreaContext careerAreas,
        EvidenceContent evidence,
        Methodology methodology) {

    public UniversityReport {
        reportVersion = required(reportVersion, "reportVersion");
        Objects.requireNonNull(tier);
        Objects.requireNonNull(assessedAt);
        courseTitle = required(courseTitle, "courseTitle");
        institution = clean(institution);
        headline = required(headline, "headline");
        Objects.requireNonNull(assessmentSummary);
        measures = List.copyOf(Objects.requireNonNull(measures));
        keyStrengths = List.copyOf(Objects.requireNonNull(keyStrengths));
        developmentAreas = List.copyOf(Objects.requireNonNull(developmentAreas));
        Objects.requireNonNull(narrative);
        practicalActions = List.copyOf(Objects.requireNonNull(practicalActions));
        skillPriorities = List.copyOf(Objects.requireNonNull(skillPriorities));
        Objects.requireNonNull(careerAreas);
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(methodology);
    }

    public record AssessmentSummary(AssessmentStatus status, AssessmentConfidence confidence,
            String interpretation, String employmentDisclaimer, String methodologyCaveat) {
        public AssessmentSummary {
            Objects.requireNonNull(status);
            Objects.requireNonNull(confidence);
            interpretation = required(interpretation, "interpretation");
            employmentDisclaimer = required(employmentDisclaimer, "employmentDisclaimer");
            methodologyCaveat = required(methodologyCaveat, "methodologyCaveat");
        }
    }

    public record MeasureSummary(UniversityMeasure measure, double indicator, ReadinessBand band,
            double confidence, String conciseExplanation, Optional<String> detailedExplanation) {
        public MeasureSummary {
            Objects.requireNonNull(measure);
            Objects.requireNonNull(band);
            conciseExplanation = required(conciseExplanation, "conciseExplanation");
            detailedExplanation = clean(detailedExplanation);
        }
    }

    public record NarrativeContent(boolean available, Optional<String> summary, Optional<String> whatThisMeans,
            Optional<String> futureReadinessExplanation, Optional<String> strengthsExplanation,
            Optional<String> developmentOpportunitiesExplanation, Optional<String> complementarityExplanation,
            Optional<String> transferableSkillsExplanation, Optional<String> careerAdaptabilityExplanation,
            Optional<String> cautionOrLimitations) {
        public NarrativeContent {
            summary = clean(summary);
            whatThisMeans = clean(whatThisMeans);
            futureReadinessExplanation = clean(futureReadinessExplanation);
            strengthsExplanation = clean(strengthsExplanation);
            developmentOpportunitiesExplanation = clean(developmentOpportunitiesExplanation);
            complementarityExplanation = clean(complementarityExplanation);
            transferableSkillsExplanation = clean(transferableSkillsExplanation);
            careerAdaptabilityExplanation = clean(careerAdaptabilityExplanation);
            cautionOrLimitations = clean(cautionOrLimitations);
            if (!available && java.util.stream.Stream.of(summary, whatThisMeans, futureReadinessExplanation,
                    strengthsExplanation, developmentOpportunitiesExplanation, complementarityExplanation,
                    transferableSkillsExplanation, careerAdaptabilityExplanation, cautionOrLimitations)
                    .anyMatch(Optional::isPresent)) {
                throw new IllegalArgumentException("unavailable narrative must not contain content");
            }
        }
    }

    public record SkillDevelopmentPriority(SkillDevelopmentCategory category, SkillPriorityLevel priority,
            String title, String rationale) {
        public SkillDevelopmentPriority {
            Objects.requireNonNull(category);
            Objects.requireNonNull(priority);
            title = required(title, "skill title");
            rationale = required(rationale, "skill rationale");
        }
    }

    /** Career areas are context the student supplied, never a recommendation engine. */
    public record CareerAreaContext(List<String> areasToExplore, String guidance, String limitation) {
        public CareerAreaContext {
            areasToExplore = List.copyOf(Objects.requireNonNull(areasToExplore));
            guidance = required(guidance, "career area guidance");
            limitation = required(limitation, "career area limitation");
        }
    }

    public record EvidenceContent(EvidenceStatus status, String message, List<EvidenceItem> items) {
        public EvidenceContent {
            Objects.requireNonNull(status);
            message = required(message, "evidence message");
            items = List.copyOf(Objects.requireNonNull(items));
        }
    }

    public record EvidenceItem(String category, String title, String summary, String sourceName,
            Optional<URI> sourceUrl, Optional<String> publisher, Optional<String> geography,
            Optional<LocalDate> publicationDate, Instant retrievedAt,
            Optional<String> metricName, Optional<BigDecimal> metricValue, Optional<String> metricUnit,
            EvidenceSourceQuality sourceQuality, String relevanceNote) {
        public EvidenceItem {
            category = required(category, "evidence category");
            title = required(title, "evidence title");
            summary = required(summary, "evidence summary");
            sourceName = required(sourceName, "sourceName");
            Objects.requireNonNull(sourceUrl);
            publisher = clean(publisher);
            geography = clean(geography);
            Objects.requireNonNull(publicationDate);
            Objects.requireNonNull(retrievedAt);
            metricName = clean(metricName);
            Objects.requireNonNull(metricValue);
            metricUnit = clean(metricUnit);
            Objects.requireNonNull(sourceQuality);
            relevanceNote = required(relevanceNote, "relevanceNote");
        }
    }

    public record Methodology(String judgmentSchemaVersion, String decisionPolicyVersion,
            Optional<String> narrativeVersion, Optional<String> evidenceVersion,
            String reportVersion, List<String> limitations) {
        public Methodology {
            judgmentSchemaVersion = required(judgmentSchemaVersion, "judgmentSchemaVersion");
            decisionPolicyVersion = required(decisionPolicyVersion, "decisionPolicyVersion");
            narrativeVersion = clean(narrativeVersion);
            evidenceVersion = clean(evidenceVersion);
            reportVersion = required(reportVersion, "reportVersion");
            limitations = List.copyOf(Objects.requireNonNull(limitations));
        }
    }

    private static Optional<String> clean(Optional<String> value) {
        return Objects.requireNonNull(value).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
