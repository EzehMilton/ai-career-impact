package com.chikere.futureai.report.university.application;

import com.chikere.futureai.decision.university.ProfileBand;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV2;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.report.BoundarySafeScore;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * The one way a {@code university-decision-v2} course profile is worded and its scores shown.
 *
 * <p>Scores are shown to two decimal places, rounded half-up from their shortest decimal representation. As for
 * Professional, a rounded value that would visually reach the next band's boundary while the stored band is the
 * lower one shows as the highest two-decimal value still inside the stored band: a MODERATE 6.996 shows as 6.99.
 * The band always comes from the stored deterministic result; it is never recomputed from the displayed number.</p>
 */
public final class UniversityProfilePresentation {

    public static final String HEADING = "Your course profile in an AI-shaped world";

    private UniversityProfilePresentation() {
    }

    /** The score as shown, for example {@code "6.99"}, consistent with {@code storedBand}. */
    public static String score(double score, ProfileBand storedBand) {
        Objects.requireNonNull(storedBand, "storedBand must not be null");
        return BoundarySafeScore.format(score, nextBandFrom(storedBand));
    }

    /** The band in the measure's context, for example "High Human and Practical Capability". */
    public static String bandLabel(UniversityProfileMeasure measure, ProfileBand band) {
        Objects.requireNonNull(measure, "measure must not be null");
        String name = Objects.requireNonNull(band, "band must not be null").name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + measure.displayName();
    }

    private static OptionalDouble nextBandFrom(ProfileBand band) {
        return switch (band) {
            case LOW -> OptionalDouble.of(UniversityDecisionPolicyV2.MODERATE_FROM);
            case MODERATE -> OptionalDouble.of(UniversityDecisionPolicyV2.HIGH_FROM);
            case HIGH -> OptionalDouble.empty();
        };
    }
}
