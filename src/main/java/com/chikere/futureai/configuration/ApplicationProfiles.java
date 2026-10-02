package com.chikere.futureai.configuration;

/** The profiles this application understands, named once. */
public final class ApplicationProfiles {

    /** Production defaults: secure cookies, proxy awareness, no development anything. */
    public static final String PROD = "prod";

    /** Local development: every external provider is replaced by a deterministic fixture. */
    public static final String LOCAL_FIXTURE = "local-fixture";

    /** Real Stripe checkout and webhooks. */
    public static final String LIVE_PAYMENT = "live-payment";

    /** Real external evidence providers. */
    public static final String LIVE_EVIDENCE = "live-evidence";

    private ApplicationProfiles() {
    }
}
