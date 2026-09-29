package com.chikere.futureai.decision.professional;

public final class ProfessionalScoreScale {

    private static final double SCORE_MAXIMUM = 4.0;
    private static final double INDICATOR_MAXIMUM = 10.0;

    private ProfessionalScoreScale() {
    }

    public static double normalise(double score) {
        requireScore(score);
        return score / SCORE_MAXIMUM;
    }

    public static double inverse(double score) {
        return 1.0 - normalise(score);
    }

    static double toIndicator(double normalisedComposite) {
        double tolerance = 1.0e-9;
        if (!Double.isFinite(normalisedComposite)
                || normalisedComposite < -tolerance
                || normalisedComposite > 1.0 + tolerance) {
            throw new IllegalArgumentException("normalisedComposite must be between 0 and 1");
        }
        return Math.max(0.0, Math.min(INDICATOR_MAXIMUM, normalisedComposite * INDICATOR_MAXIMUM));
    }

    private static void requireScore(double score) {
        if (!Double.isFinite(score) || score < 0.0 || score > SCORE_MAXIMUM) {
            throw new IllegalArgumentException("score must be finite and between 0 and 4");
        }
    }
}
