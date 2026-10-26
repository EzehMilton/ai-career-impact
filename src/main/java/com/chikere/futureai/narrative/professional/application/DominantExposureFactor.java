package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.decision.professional.ProfessionalMeasure;

import java.util.List;

/**
 * Which of Java's already-selected factors must lead the AI Exposure explanation.
 *
 * <p>Low: the strongest factor lowering exposure. High: the strongest factor raising it. Moderate: the strongest
 * factor from whichever side's strongest factor has the larger absolute signed effect, or either on an exact tie.
 * When the list a result depends on is empty (or, for Moderate, either list is), there is no dominant requirement
 * and the ordinary grounding rule applies; with no factors at all nothing is required. It reads the policy's own
 * values and changes no score, band, selection or rank.</p>
 */
final class DominantExposureFactor {

    private DominantExposureFactor() {
    }

    /** The dimension ids any one of which satisfies the requirement; empty when there is none. */
    static List<String> of(ProfessionalNarrativeContext context) {
        var drivers = context.exposureDrivers();
        var protective = context.protectiveFactors();
        String band = context.measures().stream()
                .filter(measure -> measure.measure() == ProfessionalMeasure.AI_EXPOSURE)
                .map(ProfessionalNarrativeContext.MeasureContext::band)
                .findFirst().orElse("");
        return switch (band) {
            case "Low" -> protective.isEmpty() ? List.of() : List.of(protective.getFirst().dimensionId());
            case "High" -> drivers.isEmpty() ? List.of() : List.of(drivers.getFirst().dimensionId());
            case "Moderate" -> {
                if (drivers.isEmpty() || protective.isEmpty()) {
                    yield List.of();
                }
                int comparison = Double.compare(Math.abs(drivers.getFirst().signedEffect()),
                        Math.abs(protective.getFirst().signedEffect()));
                if (comparison == 0) {
                    yield List.of(drivers.getFirst().dimensionId(), protective.getFirst().dimensionId());
                }
                yield List.of(comparison > 0 ? drivers.getFirst().dimensionId() : protective.getFirst().dimensionId());
            }
            default -> List.of();
        };
    }
}
