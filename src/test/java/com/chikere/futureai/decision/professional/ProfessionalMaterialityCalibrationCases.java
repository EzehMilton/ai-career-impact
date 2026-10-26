package com.chikere.futureai.decision.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Diagnostic reconstruction of the Stage 2 materiality calibration cases, for tests only.
 *
 * <p>Only the AI Exposure signed effects supplied from the live smoke runs are used. Each supplied effect is
 * converted back to the raw judgement score that produces it under the shared formula
 * ({@code signedEffect = weight x (transformed - 0.5)}); a dimension whose effect was not supplied is set to the
 * neutral midpoint 2.0, so it contributes nothing to the score and can never be selected. These are not the live
 * Jev judgments, and the reconstructed scores are not the live scores: AI Complementarity, information sufficiency
 * and every confidence come from the repository's registered_nurse fixture.</p>
 */
public final class ProfessionalMaterialityCalibrationCases {

    public static final String TASK = "task_repeatability";
    public static final String DIGITAL = "digital_executability";
    public static final String TRUST = "human_trust_dependency";
    public static final String PHYSICAL = "physical_world_dependency";
    public static final String CONTEXT = "contextual_complexity";
    public static final String JUDGEMENT = "original_judgment";
    public static final String ACCOUNTABILITY = "accountability_responsibility";

    private static final Map<String, Double> WEIGHTS = Map.of(TASK, 0.25, DIGITAL, 0.25, TRUST, 0.15, PHYSICAL, 0.15,
            CONTEXT, 0.10, JUDGEMENT, 0.07, ACCOUNTABILITY, 0.03);
    private static final ProfessionalAssessmentState STATE = new ProfessionalAssessmentState("Calibration case",
            "Diagnostic reconstruction.", List.of("Diagnostic"), List.of("Diagnostic"), OptionalInt.empty());

    private ProfessionalMaterialityCalibrationCases() {
    }

    /** Live Registered Nurse: context and accountability effects were not supplied. */
    public static Map<String, Double> registeredNurse() {
        return raw(Map.of(TASK, 0.009, JUDGEMENT, 0.003, DIGITAL, -0.115, PHYSICAL, -0.073, TRUST, -0.056));
    }

    /** Live Java Software Developer: the accountability effect was not supplied. */
    public static Map<String, Double> javaSoftwareDeveloper() {
        return raw(Map.of(DIGITAL, 0.124375, PHYSICAL, 0.074625, TRUST, 0.00375, CONTEXT, -0.016,
                JUDGEMENT, -0.011, TASK, -0.008));
    }

    /** Live Management Accountant: only the three raising effects were supplied. */
    public static Map<String, Double> managementAccountant() {
        return raw(Map.of(DIGITAL, 0.105, PHYSICAL, 0.072, TASK, 0.052));
    }

    /** The registered_nurse fixture with the given AI Exposure judgement scores, keeping every confidence. */
    public static AssessmentJudgmentSet judgments(Map<String, Double> scores) {
        AssessmentJudgmentSet fixture = new LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(STATE);
        return new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), fixture.judgments().stream()
                .map(judgment -> scores.containsKey(judgment.dimensionId())
                        ? new DimensionJudgment(judgment.dimensionId(), judgment.name(),
                                OptionalDouble.of(scores.get(judgment.dimensionId())), judgment.categoricalValue(),
                                judgment.confidence(), judgment.confidenceValue(), judgment.probabilityDistribution())
                        : judgment)
                .toList());
    }

    /** Raw 0-4 scores for all seven AI Exposure dimensions; unsupplied ones sit at the neutral midpoint. */
    public static Map<String, Double> raw(Map<String, Double> signedEffects) {
        Map<String, Double> scores = new LinkedHashMap<>();
        WEIGHTS.forEach((id, weight) -> {
            double effect = signedEffects.getOrDefault(id, 0.0);
            boolean direct = id.equals(TASK) || id.equals(DIGITAL);
            double transformed = 0.5 + effect / weight;
            scores.put(id, 4.0 * (direct ? transformed : 1.0 - transformed));
        });
        return scores;
    }
}
