package com.chikere.futureai.narrative.alevel.paid;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** One provider call, at most one repair, then AVAILABLE, a final FALLBACK or RETRYABLE_FALLBACK. Nothing stored. */
class ALevelPaidNarrativeServiceTest {

    private final ScriptedALevelPaidNarrativeGenerator generator = new ScriptedALevelPaidNarrativeGenerator();
    private final ALevelPaidNarrativeContext context = ALevelPaidNarrativeValidatorTest.context();

    private ALevelPaidNarrativeService service() {
        return new ALevelPaidNarrativeService(() -> Optional.of(generator), new ALevelPaidNarrativeValidator(),
                com.chikere.futureai.trace.AssessmentTrace.disabled());
    }

    @Test
    void validProseIsAvailableAfterOneCall() {
        generator.then(ALevelPaidNarrativeValidatorTest.clean());

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.narrative()).contains(ALevelPaidNarrativeValidatorTest.clean());
        assertThat(outcome.attempts()).isEqualTo(1);
        assertThat(outcome.narrativeVersion()).isEqualTo("a-level-paid-narrative-v1");
        assertThat(generator.calls()).isEqualTo(1);
    }

    @Test
    void aRejectedDraftIsRepairedOnceWithItsRule() {
        generator.then(ALevelPaidNarrativeValidatorTest.withOptions("Your score is 7.4."))
                .then(ALevelPaidNarrativeValidatorTest.clean());

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.attempts()).isEqualTo(2);
        assertThat(generator.repairCalls).isEqualTo(1);
        assertThat(generator.lastFeedback.rule()).isEqualTo(ALevelPaidNarrativeValidator.NUMERIC);
    }

    @Test
    void aRepairThatStillFailsIsAFinalFallbackWithNoThirdCall() {
        generator.then(ALevelPaidNarrativeValidatorTest.withOptions("Your score is 7.4."))
                .then(ALevelPaidNarrativeValidatorTest.withOptions("This course is AI-proof."));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.FALLBACK);
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.narrative()).isEmpty();
        assertThat(outcome.reason()).contains("VALIDATION_FAILED_AFTER_REPAIR:NO_NUMBERS_SCORES_OR_PERCENTAGES"
                + ">NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO");
        assertThat(generator.calls()).isEqualTo(2);
    }

    @Test
    void aProviderErrorIsRetryable() {
        generator.failing(new IllegalStateException("timeout"));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("PROVIDER_ERROR:IllegalStateException");
        assertThat(generator.calls()).isEqualTo(1);
    }

    @Test
    void anEmptyResponseIsRetryableWithoutARepair() {
        generator.then(new ALevelPaidNarrative("", "", "", "", ""));

        var outcome = service().generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("EMPTY_PROVIDER_RESPONSE");
        assertThat(generator.repairCalls).isZero();
    }

    @Test
    void noConfiguredProviderIsRetryableWithNoCall() {
        var outcome = new ALevelPaidNarrativeService(Optional::empty, new ALevelPaidNarrativeValidator(),
                com.chikere.futureai.trace.AssessmentTrace.disabled()).generate(context);

        assertThat(outcome.status()).isEqualTo(ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(outcome.reason()).contains("PROVIDER_NOT_CONFIGURED");
        assertThat(outcome.attempts()).isZero();
    }
}
