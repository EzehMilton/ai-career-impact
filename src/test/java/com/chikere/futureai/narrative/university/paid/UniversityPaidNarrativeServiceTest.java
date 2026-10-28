package com.chikere.futureai.narrative.university.paid;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** One provider call, at most one repair, then AVAILABLE, a final FALLBACK or RETRYABLE_FALLBACK. Nothing stored. */
class UniversityPaidNarrativeServiceTest {

    private final ScriptedPaidNarrativeGenerator generator = new ScriptedPaidNarrativeGenerator();
    private final UniversityPaidNarrativeContext context = UniversityPaidNarrativeValidatorTest.context(false);

    private UniversityPaidNarrativeService service() {
        return new UniversityPaidNarrativeService(() -> Optional.of(generator), new UniversityPaidNarrativeValidator(),
                com.chikere.futureai.trace.AssessmentTrace.disabled());
    }

    @Test
    void validProseIsAvailableAfterOneCall() {
        generator.then(UniversityPaidNarrativeValidatorTest.clean());

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.narrative()).contains(UniversityPaidNarrativeValidatorTest.clean());
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.narrativeVersion()).isEqualTo("university-paid-narrative-v1");
        assertThat(generator.calls()).isEqualTo(1);
    }

    @Test
    void aRejectedDraftIsRepairedOnceWithItsRule() {
        generator.then(UniversityPaidNarrativeValidatorTest.withOverall("Your score is 7.4."))
                .then(UniversityPaidNarrativeValidatorTest.clean());

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.attempts()).isEqualTo(2);
        assertThat(generator.repairCalls).isEqualTo(1);
        assertThat(generator.lastFeedback.rule()).isEqualTo(UniversityPaidNarrativeValidator.NUMERIC);
    }

    @Test
    void aRepairThatStillFailsIsAFinalFallbackWithNoThirdCall() {
        generator.then(UniversityPaidNarrativeValidatorTest.withOverall("Your score is 7.4."))
                .then(UniversityPaidNarrativeValidatorTest.withOverall("This course is AI-proof."));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.narrative()).isEmpty();
        assertThat(outcome.reason()).contains("VALIDATION_FAILED_AFTER_REPAIR:NO_NUMBERS_OR_SCORES"
                + ">NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO");
        assertThat(generator.calls()).isEqualTo(2);
    }

    @Test
    void aProviderErrorIsRetryable() {
        generator.failing(new IllegalStateException("timeout"));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("PROVIDER_ERROR:IllegalStateException");
        assertThat(generator.calls()).isEqualTo(1);
    }

    @Test
    void anEmptyResponseIsRetryableWithoutARepair() {
        generator.then(new UniversityPaidNarrative("", "", "", "", ""));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("EMPTY_PROVIDER_RESPONSE");
        assertThat(generator.repairCalls).isZero();
    }

    @Test
    void noConfiguredProviderIsRetryableWithNoCall() {
        var outcome = new UniversityPaidNarrativeService(Optional::empty, new UniversityPaidNarrativeValidator(),
                com.chikere.futureai.trace.AssessmentTrace.disabled()).generate(context);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("PROVIDER_NOT_CONFIGURED");
        assertThat(outcome.attempts()).isZero();
    }
}
