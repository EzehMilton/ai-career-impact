package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.evidence.professional.application.EvidenceStatus;
import com.chikere.futureai.evidence.professional.application.EvidenceSourceQuality;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, delivery-neutral composition suitable for web and a future PDF renderer. */
public record ProfessionalReport(
        String reportVersion,
        ProfessionalReportTier tier,
        boolean developmentFixture,
        Instant assessedAt,
        String profession,
        String headline,
        AssessmentSummary assessmentSummary,
        List<MeasureSummary> measures,
        List<String> exposureDrivers,
        List<String> protectiveFactors,
        NarrativeContent narrative,
        List<String> practicalActions,
        List<SkillDevelopmentPriority> skillPriorities,
        ProfessionalWorkFocus workFocus,
        EvidenceContent evidence,
        Optional<OccupationClassification> occupationClassification,
        Methodology methodology) {

    public ProfessionalReport {
        reportVersion = required(reportVersion, "reportVersion");
        Objects.requireNonNull(tier);
        Objects.requireNonNull(assessedAt);
        profession = required(profession, "profession");
        headline = required(headline, "headline");
        Objects.requireNonNull(assessmentSummary);
        measures = List.copyOf(Objects.requireNonNull(measures));
        exposureDrivers = List.copyOf(Objects.requireNonNull(exposureDrivers));
        protectiveFactors = List.copyOf(Objects.requireNonNull(protectiveFactors));
        Objects.requireNonNull(narrative);
        practicalActions = List.copyOf(Objects.requireNonNull(practicalActions));
        skillPriorities = List.copyOf(Objects.requireNonNull(skillPriorities));
        Objects.requireNonNull(workFocus);
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(occupationClassification);
        Objects.requireNonNull(methodology);
    }

    public record AssessmentSummary(AssessmentStatus status, AssessmentConfidence confidence,
            String interpretation, String jobLossDisclaimer, String methodologyCaveat) {
        public AssessmentSummary {
            Objects.requireNonNull(status); Objects.requireNonNull(confidence);
            interpretation = required(interpretation, "interpretation");
            jobLossDisclaimer = required(jobLossDisclaimer, "jobLossDisclaimer");
            methodologyCaveat = required(methodologyCaveat, "methodologyCaveat");
        }
    }

    public record MeasureSummary(ProfessionalMeasure measure, double indicator, IndicatorBand band,
            double confidence, String conciseExplanation, Optional<String> detailedExplanation) {
        public MeasureSummary {
            Objects.requireNonNull(measure); Objects.requireNonNull(band);
            conciseExplanation = required(conciseExplanation, "conciseExplanation");
            detailedExplanation = clean(detailedExplanation);
        }
    }

    public record NarrativeContent(boolean available, Optional<String> summary, Optional<String> whatThisMeans,
            Optional<String> exposureExplanation, Optional<String> humanAdvantageExplanation,
            Optional<String> complementarityExplanation, Optional<String> adaptabilityExplanation,
            Optional<String> cautionOrLimitations) {
        public NarrativeContent {
            summary = clean(summary); whatThisMeans = clean(whatThisMeans);
            exposureExplanation = clean(exposureExplanation);
            humanAdvantageExplanation = clean(humanAdvantageExplanation);
            complementarityExplanation = clean(complementarityExplanation);
            adaptabilityExplanation = clean(adaptabilityExplanation);
            cautionOrLimitations = clean(cautionOrLimitations);
            if (!available && java.util.stream.Stream.of(summary, whatThisMeans, exposureExplanation,
                    humanAdvantageExplanation, complementarityExplanation, adaptabilityExplanation,
                    cautionOrLimitations).anyMatch(Optional::isPresent)) {
                throw new IllegalArgumentException("unavailable narrative must not contain content");
            }
        }
    }

    public record SkillDevelopmentPriority(SkillDevelopmentCategory category, SkillPriorityLevel priority,
            String title, String rationale) {
        public SkillDevelopmentPriority {
            Objects.requireNonNull(category); Objects.requireNonNull(priority);
            title = required(title, "skill title"); rationale = required(rationale, "skill rationale");
        }
    }

    public record ProfessionalWorkFocus(List<WorkFocusGuidance> areas, String limitation) {
        public ProfessionalWorkFocus {
            areas = List.copyOf(Objects.requireNonNull(areas));
            limitation = required(limitation, "work focus limitation");
        }
    }

    public record WorkFocusGuidance(WorkFocusCategory category, String title, String guidance,
            List<String> relatedResponsibilities) {
        public WorkFocusGuidance {
            Objects.requireNonNull(category); title = required(title, "work focus title");
            guidance = required(guidance, "work focus guidance");
            relatedResponsibilities = List.copyOf(Objects.requireNonNull(relatedResponsibilities));
        }
    }

    public record EvidenceContent(EvidenceStatus status, String message, List<EvidenceItem> items) {
        public EvidenceContent {
            Objects.requireNonNull(status); message = required(message, "evidence message");
            items = List.copyOf(Objects.requireNonNull(items));
        }
    }

    public record EvidenceItem(String category, String title, String summary, String sourceName,
            Optional<URI> sourceUrl, Optional<String> publisher, Optional<String> geography,
            Optional<LocalDate> publicationDate, Instant retrievedAt,
            Optional<String> metricName, Optional<BigDecimal> metricValue, Optional<String> metricUnit,
            EvidenceSourceQuality sourceQuality, String relevanceNote) {
        public EvidenceItem {
            category = required(category, "evidence category"); title = required(title, "evidence title");
            summary = required(summary, "evidence summary"); sourceName = required(sourceName, "sourceName");
            Objects.requireNonNull(sourceUrl); publisher = clean(publisher); geography = clean(geography);
            Objects.requireNonNull(publicationDate); Objects.requireNonNull(retrievedAt);
            metricName = clean(metricName); Objects.requireNonNull(metricValue); metricUnit = clean(metricUnit);
            Objects.requireNonNull(sourceQuality); relevanceNote = required(relevanceNote, "relevanceNote");
        }
    }

    public record OccupationClassification(String canonicalOccupation, String classificationSystem,
            String classificationCode, String quality, Instant classifiedAt, String classifierVersion) {
        public OccupationClassification {
            canonicalOccupation = required(canonicalOccupation, "canonicalOccupation");
            classificationSystem = required(classificationSystem, "classificationSystem");
            classificationCode = required(classificationCode, "classificationCode");
            quality = required(quality, "quality"); Objects.requireNonNull(classifiedAt);
            classifierVersion = required(classifierVersion, "classifierVersion");
        }
    }

    public record Methodology(String judgmentSchemaVersion, String decisionPolicyVersion,
            Optional<String> narrativeVersion, Optional<String> evidenceVersion,
            Optional<String> classifierVersion, String reportVersion, List<String> limitations) {
        public Methodology {
            judgmentSchemaVersion = required(judgmentSchemaVersion, "judgmentSchemaVersion");
            decisionPolicyVersion = required(decisionPolicyVersion, "decisionPolicyVersion");
            narrativeVersion = clean(narrativeVersion); evidenceVersion = clean(evidenceVersion);
            classifierVersion = clean(classifierVersion); reportVersion = required(reportVersion, "reportVersion");
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
