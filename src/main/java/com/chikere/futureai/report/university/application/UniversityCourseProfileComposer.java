package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.university.ProfileBand;
import com.chikere.futureai.decision.university.UniversityProfileDecisionResult;
import com.chikere.futureai.decision.university.UniversityProfileFactor;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.decision.university.UniversityProfileMeasureResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the customer-facing course profile from a stored {@code university-decision-v2} result. Pure wording over
 * frozen values: scores use the boundary-safe display, bands keep their measure context, drivers and limits are the
 * policy's own material factors, and nothing is recalculated, combined into an overall score or ranked.
 */
public final class UniversityCourseProfileComposer {

    public static final String LOWER_CONFIDENCE_NOTE = "The course information gives less certainty for this measure, "
            + "so treat it as a broader indication.";

    private static final Map<UniversityProfileMeasure, String> EXPLANATIONS = Map.of(
            UniversityProfileMeasure.WORKING_ALONGSIDE_AI, "How strongly your course develops the ability to use AI "
                    + "productively alongside your own subject knowledge and judgement.",
            UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY, "How strongly the course develops capabilities "
                    + "involving people, judgement, practical activity and original thinking.",
            UniversityProfileMeasure.TRANSFERABLE_CAPABILITY, "How widely the course develops capabilities that can "
                    + "transfer across different roles and settings.");

    /** Customer wording for each university-v1 judgment when it drives or limits a measure. */
    private static final Map<String, String> DIMENSIONS = Map.of(
            "technical_digital_depth", "technical and digital depth",
            "analytical_reasoning", "structured analysis and problem solving",
            "original_judgment_creativity", "original thinking and creativity",
            "human_relationship_dependency", "work with people",
            "contextual_complexity", "judgement in complex, changing situations",
            "practical_real_world_application", "practical, real-world application",
            "ai_complementarity", "scope to use AI tools productively in the work the course leads to",
            "skill_transferability", "how widely the skills apply",
            "career_breadth", "the range of pathways the course opens");

    public UniversityCourseProfile compose(UniversityProfileDecisionResult profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        if (profile.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return new UniversityCourseProfile(UniversityProfilePresentation.HEADING, true, List.of(),
                    "We need more detail about the course's modules, topics or skills before we can show its profile.");
        }
        List<UniversityCourseProfile.Measure> measures = new ArrayList<>();
        for (UniversityProfileMeasure measure : UniversityProfileMeasure.values()) {
            UniversityProfileMeasureResult result = profile.measure(measure).orElseThrow();
            measures.add(new UniversityCourseProfile.Measure(measure.name(), measure.displayName(),
                    UniversityProfilePresentation.score(result.indicator(), result.band()), result.band().name(),
                    UniversityProfilePresentation.bandLabel(measure, result.band()), EXPLANATIONS.get(measure),
                    result.drivers().stream().map(UniversityCourseProfileComposer::describe).toList(),
                    result.limitingFactors().stream().map(UniversityCourseProfileComposer::describe).toList(),
                    result.lowerConfidence() ? LOWER_CONFIDENCE_NOTE : ""));
        }
        return new UniversityCourseProfile(UniversityProfilePresentation.HEADING, false, measures, summary(profile));
    }

    /** One sentence per measure, in measure order, from its band alone. */
    static String summary(UniversityProfileDecisionResult profile) {
        ProfileBand ai = profile.measure(UniversityProfileMeasure.WORKING_ALONGSIDE_AI).orElseThrow().band();
        ProfileBand human = profile.measure(UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY).orElseThrow().band();
        ProfileBand transfer = profile.measure(UniversityProfileMeasure.TRANSFERABLE_CAPABILITY).orElseThrow().band();
        String aiSentence = switch (ai) {
            case HIGH -> "The course gives you a strong basis for working productively alongside AI tools.";
            case MODERATE -> "The course gives you a moderate basis for working alongside AI tools.";
            case LOW -> "The course does less to prepare you to work alongside AI tools, so this is worth building "
                    + "alongside it.";
        };
        String humanSentence = switch (human) {
            case HIGH -> "It places strong emphasis on work with people, judgement, practical activity and original "
                    + "thinking.";
            case MODERATE -> "It gives moderate emphasis to work with people, judgement, practical activity and "
                    + "original thinking.";
            case LOW -> "It gives less emphasis to work with people, practical activity and original thinking.";
        };
        String transferSentence = switch (transfer) {
            case HIGH -> "Its capabilities appear to transfer widely across different roles and settings.";
            case MODERATE -> "Its capabilities appear to transfer to a moderate range of roles and settings.";
            case LOW -> "Its capabilities appear more closely tied to particular roles and settings.";
        };
        return aiSentence + " " + humanSentence + " " + transferSentence;
    }

    private static String describe(UniversityProfileFactor factor) {
        return DIMENSIONS.getOrDefault(factor.dimensionId(), factor.dimensionId().replace('_', ' '));
    }
}
