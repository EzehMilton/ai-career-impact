package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.decision.professional.ProfessionalMeasureResult;
import org.springframework.stereotype.Component;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
public final class ProfessionalAssessmentViewMapper {

    public ProfessionalAssessmentViewModel toView(
            StoredProfessionalAssessment snapshot,
            Optional<StoredProfessionalNarrative> storedNarrative) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(storedNarrative, "storedNarrative must not be null");
        var result = snapshot.result();
        var decision = result.decision();
        boolean needsInformation = decision.assessmentDecision().status()
                == AssessmentStatus.MORE_INFORMATION_REQUIRED;
        List<ProfessionalAssessmentViewModel.MeasureView> measures = needsInformation
                ? List.of()
                : Arrays.stream(ProfessionalMeasure.values())
                        .map(measure -> measureView(decision.measure(measure).orElseThrow()))
                        .toList();
        ProfessionalAssessmentViewModel.NarrativeView narrative = storedNarrative
                .filter(stored -> stored.status() == NarrativeStatus.AVAILABLE)
                .flatMap(StoredProfessionalNarrative::narrative)
                .map(value -> new ProfessionalAssessmentViewModel.NarrativeView(
                        value.headline(), value.summary(), value.whatThisMeans(),
                        value.exposureExplanation(), value.humanAdvantageExplanation(),
                        value.complementarityExplanation(), value.adaptabilityExplanation(),
                        value.practicalActions(), value.cautionOrLimitations()))
                .orElse(null);
        return new ProfessionalAssessmentViewModel(
                snapshot.state().profession(),
                statusLabel(decision.assessmentDecision().status()),
                confidenceLabel(decision.assessmentDecision().confidence()),
                needsInformation,
                result.source() == AssessmentSource.LOCAL_FIXTURE,
                measures,
                decision.topExposureDrivers().stream().map(this::factorLabel).toList(),
                decision.topProtectiveFactors().stream().map(this::factorLabel).toList(),
                narrative == null,
                narrative);
    }

    private ProfessionalAssessmentViewModel.MeasureView measureView(ProfessionalMeasureResult result) {
        return new ProfessionalAssessmentViewModel.MeasureView(
                measureLabel(result.measure()),
                String.format(Locale.ROOT, "%.2f", result.indicator()),
                bandLabel(result.band().name()),
                String.format(Locale.ROOT, "%.0f%%", result.confidence() * 100.0),
                measureExplanation(result.measure()));
    }

    private String measureLabel(ProfessionalMeasure measure) {
        return switch (measure) {
            case AI_EXPOSURE -> "AI Exposure";
            case HUMAN_ADVANTAGE -> "Human Advantage";
            case AI_COMPLEMENTARITY -> "AI Complementarity";
            case ROLE_ADAPTABILITY -> "Role Adaptability";
        };
    }

    private String measureExplanation(ProfessionalMeasure measure) {
        return switch (measure) {
            case AI_EXPOSURE -> "How strongly the structure of the work overlaps with characteristics AI can "
                    + "increasingly perform. This is not a probability that your job will disappear.";
            case HUMAN_ADVANTAGE -> "How strongly this role depends on human trust, real-world presence, "
                    + "contextual understanding, original judgment and accountability.";
            case AI_COMPLEMENTARITY -> "How much scope there appears to be for AI to increase productivity "
                    + "while meaningful human contribution remains.";
            case ROLE_ADAPTABILITY -> "How much scope the structure of the role provides for continued human "
                    + "value and AI-enabled evolution. This does not measure your motivation, learning ability, "
                    + "willingness to retrain or personal circumstances.";
        };
    }

    private String factorLabel(ProfessionalDecisionFactor factor) {
        return switch (factor.direction()) {
            case HIGH_TASK_REPEATABILITY -> "A significant part of the work is repetitive or standardised";
            case LOW_TASK_REPEATABILITY -> "The work is less repetitive or standardised";
            case HIGH_DIGITAL_EXECUTABILITY -> "Much of the work can be performed digitally";
            case LOW_DIGITAL_EXECUTABILITY -> "The work cannot be done entirely through digital systems";
            case HIGH_HUMAN_TRUST_DEPENDENCY -> "Trust, persuasion or relationships matter to successful performance";
            case LOW_HUMAN_TRUST_DEPENDENCY -> "The work depends less on human trust or relationships";
            case HIGH_PHYSICAL_WORLD_DEPENDENCY -> "The work relies on skilled physical-world presence";
            case LOW_PHYSICAL_WORLD_DEPENDENCY -> "The work requires little physical-world presence";
            case HIGH_CONTEXTUAL_COMPLEXITY -> "The work depends on understanding complex or changing situations";
            case LOW_CONTEXTUAL_COMPLEXITY -> "The work usually follows clear and predictable patterns";
            case HIGH_ORIGINAL_JUDGMENT -> "The role relies on original judgment and synthesis";
            case LOW_ORIGINAL_JUDGMENT -> "The work relies more on established methods than original judgment";
            case HIGH_ACCOUNTABILITY_RESPONSIBILITY ->
                    "The role carries meaningful responsibility for consequential decisions";
            case LOW_ACCOUNTABILITY_RESPONSIBILITY ->
                    "The role carries limited responsibility for consequential decisions";
        };
    }

    private String statusLabel(AssessmentStatus status) {
        return switch (status) {
            case READY -> "Ready";
            case READY_WITH_CAUTION -> "Ready with caution";
            case MORE_INFORMATION_REQUIRED -> "More information required";
        };
    }

    private String confidenceLabel(AssessmentConfidence confidence) {
        return bandLabel(confidence.name());
    }

    private String bandLabel(String name) {
        return name.substring(0, 1) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
