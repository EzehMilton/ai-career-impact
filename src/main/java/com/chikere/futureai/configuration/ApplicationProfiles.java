package com.chikere.futureai.configuration;

/** The profiles this application understands, named once. */
public final class ApplicationProfiles {

    /** Full local development: every external provider replaced by a deterministic fixture. */
    public static final String DEV = "dev";

    /**
     * Hybrid development: fixture Jev judgments and fixture evidence, but real OpenAI and Stripe.
     * For machines that cannot reach the TypeSafe / Jev network.
     */
    public static final String DEV_NO_JEV = "dev-no-jev";

    /** Production: every real provider active. */
    public static final String PROD = "prod";

    private ApplicationProfiles() {
    }
}