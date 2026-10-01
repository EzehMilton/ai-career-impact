package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.OptionalityBand;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, delivery-neutral composition suitable for the web page and the PDF renderer. */
public record ALevelReport(
        String reportVersion,
        ALevelReportTier tier,
        boolean developmentFixture,
        Instant assessedAt,
        List<String> selectedSubjects,
        Map<String, String> predictedGrades,
        String headline,
        AssessmentSummary assessmentSummary,
        List<MeasureSummary> measures,
        List<String> mainStrengths,
        List<String> developmentOpportunities,
        NarrativeContent narrative,
        List<String> practicalActions,
        List<PracticalGuidance> guidance,
        PreferredAreaContext preferredAreas,
        EvidenceContent evidence,
        Methodology methodology) {

    public ALevelReport {
        reportVersion = required(reportVersion, "reportVersion");
        Objects.requireNonNull(tier);
        Objects.requireNonNull(assessedAt);
        selectedSubjects = List.copyOf(Objects.requireNonNull(selectedSubjects));
        predictedGrades = Map.copyOf(Objects.requireNonNull(predictedGrades));
        headline = required(headline, "headline");
        Objects.requireNonNull(assessmentSummary);
        measures = List.copyOf(Objects.requireNonNull(measures));
        mainStrengths = List.copyOf(Objects.requireNonNull(mainStrengths));
        developmentOpportunities = List.copyOf(Objects.requireNonNull(developmentOpportunities));
        Objects.requireNonNull(narrative);
        practicalActions = List.copyOf(Objects.requireNonNull(practicalActions));
        guidance = List.copyOf(Objects.requireNonNull(guidance));
        Objects.requireNonNull(preferredAreas);
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(methodology);
    }

    public record AssessmentSummary(AssessmentStatus status, AssessmentConfidence confidence,
            String interpretation, String admissionsDisclaimer, String methodologyCaveat) {
        public AssessmentSummary {
            Objects.requireNonNull(status);
            Objects.requireNonNull(confidence);
            interpretation = required(interpretation, "interpretation");
            admissionsDisclaimer = required(admissionsDisclaimer, "admissionsDisclaimer");
            methodologyCaveat = required(methodologyCaveat, "methodologyCaveat");
        }
    }

    public record MeasureSummary(ALevelMeasure measure, double indicator, OptionalityBand band,
            double confidence, String conciseExplanation, Optional<String> detailedExplanation) {
        public MeasureSummary {
            Objects.requireNonNull(measure);
            Objects.requireNonNull(band);
            conciseExplanation = required(conciseExplanation, "conciseExplanation");
            detailedExplanation = clean(detailedExplanation);
        }
    }

    public record NarrativeContent(boolean available, Optional<String> summary, Optional<String> whatThisMeans,
            Optional<String> futureOptionalityExplanation, Optional<String> strengthsExplanation,
            Optional<String> degreePathwayExplanation, Optional<String> analyticalFoundationExplanation,
            Optional<String> humanAndCreativeExplanation, Optional<String> transferabilityExplanation,
            Optional<String> adaptabilityExplanation, Optional<String> developmentOpportunitiesExplanation,
            Optional<String> cautionOrLimitations) {
        public NarrativeContent {
            summary = clean(summary);
            whatThisMeans = clean(whatThisMeans);
            futureOptionalityExplanation = clean(futureOptionalityExplanation);
            strengthsExplanation = clean(strengthsExplanation);
            degreePathwayExplanation = clean(degreePathwayExplanation);
            analyticalFoundationExplanation = clean(analyticalFoundationExplanation);
            humanAndCreativeExplanation = clean(humanAndCreativeExplanation);
            transferabilityExplanation = clean(transferabilityExplanation);
            adaptabilityExplanation = clean(adaptabilityExplanation);
            developmentOpportunitiesExplanation = clean(developmentOpportunitiesExplanation);
            cautionOrLimitations = clean(cautionOrLimitations);
            if (!available && java.util.stream.Stream.of(summary, whatThisMeans,
                    futureOptionalityExplanation, strengthsExplanation, degreePathwayExplanation,
                    analyticalFoundationExplanation, humanAndCreativeExplanation,
                    transferabilityExplanation, adaptabilityExplanation,
                    developmentOpportunitiesExplanation, cautionOrLimitations)
                    .anyMatch(Optional::isPresent)) {
                throw new IllegalArgumentException("unavailable narrative must not contain content");
            }
        }
    }

    public record PracticalGuidance(GuidanceArea area, GuidanceCategory category, String title,
            String suggestion) {
        public PracticalGuidance {
            Objects.requireNonNull(area);
            Objects.requireNonNull(category);
            title = required(title, "guidance title");
            suggestion = required(suggestion, "guidance suggestion");
        }
    }

    /** Degree and career areas are context the student supplied, never a recommendation. */
    public record PreferredAreaContext(List<String> degreeAreas, List<String> careerAreas,
            String guidance, String limitation) {
        public PreferredAreaContext {
            degreeAreas = List.copyOf(Objects.requireNonNull(degreeAreas));
            careerAreas = List.copyOf(Objects.requireNonNull(careerAreas));
            guidance = required(guidance, "preferred area guidance");
            limitation = required(limitation, "preferred area limitation");
        }
    }

    public record EvidenceContent(EvidenceStatus status, String message, String separationNote,
            List<EvidenceItem> items) {
        public EvidenceContent {
            Objects.requireNonNull(status);
            message = required(message, "evidence message");
            separationNote = required(separationNote, "evidence separationNote");
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
