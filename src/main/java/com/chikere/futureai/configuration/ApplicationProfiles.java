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

    /**
     * Local live regression testing: real Jev, OpenAI, persistence, reports and PDFs, with payment bypassed
     * through the complimentary entitlement path. Requires {@code APP_LIVE_SMOKE_ENABLED=true} as well and
     * must never be used as a deployment profile.
     */
    public static final String LIVE_SMOKE = "live-smoke";

    private ApplicationProfiles() {
    }
}