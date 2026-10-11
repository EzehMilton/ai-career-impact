package com.chikere.futureai.configuration;

/** The profiles this application understands, named once. */
public final class ApplicationProfiles {

    /** Production defaults: secure cookies, proxy awareness, no development anything. */
    public static final String PROD = "prod";

    /** Local development: every external provider is replaced by a deterministic fixture. */
    public static final String LOCAL_FIXTURE = "local-fixture";

    /** Local-fixture overlay that makes only professional CV extraction call OpenAI. */
    public static final String LIVE_CV_EXTRACTION = "live-cv-extraction";

    /** Real Stripe checkout and webhooks. */
    public static final String LIVE_PAYMENT = "live-payment";

    /** Real external evidence providers. */
    public static final String LIVE_EVIDENCE = "live-evidence";

    /** Development only: writes each professional Jev request and answer to local JSON files. */
    public static final String JEV_CAPTURE = "jev-capture";

    private ApplicationProfiles() {
    }
}
