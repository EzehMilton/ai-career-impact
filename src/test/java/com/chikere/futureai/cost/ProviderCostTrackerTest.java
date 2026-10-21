package com.chikere.futureai.cost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ProviderCostTrackerTest {

    private final ProviderCostTracker tracker = new ProviderCostTracker(new ProviderCostProperties("USD",
            new ProviderRates(new BigDecimal("0.15"), new BigDecimal("0.60")),
            new ProviderRates(new BigDecimal("0.042"), BigDecimal.ZERO)));

    @Test
    void logsOneLineForAnAssessmentWithEveryCallMadeDuringIt(CapturedOutput output) {
        UUID id = UUID.randomUUID();

        UUID returned = tracker.trackAssessment("professional", () -> {
            tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 2_000, 0));
            tracker.record(ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 3_000, 800));
            return id;
        });

        assertThat(returned).isEqualTo(id);
        assertThat(output).contains("Estimated provider cost operation=assessment journey=professional "
                + "assessmentId=" + id + " outcome=completed jevCalls=1 jevInputTokens=2000");
        assertThat(output).contains("openaiInputTokens=3000 openaiOutputTokens=800",
                "totalCost=0.001014 currency=USD");
    }

    @Test
    void stillLogsWhatWasSpentWhenTheSubmissionFails(CapturedOutput output) {
        var failure = new IllegalStateException("save failed");

        assertThatThrownBy(() -> tracker.trackAssessment("university", () -> {
            tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 1_000, 0));
            throw failure;
        })).isSameAs(failure);

        assertThat(output).contains("journey=university assessmentId=none outcome=failed jevCalls=1");
    }

    @Test
    void writesNothingWhenNoBillableCallWasMade(CapturedOutput output) {
        tracker.trackAssessment("a-level", UUID::randomUUID);

        assertThat(output).doesNotContain("Estimated provider cost");
    }

    @Test
    void ignoresCallsMadeOutsideATrackedUnitAndForgetsUsageAfterOne(CapturedOutput output) {
        tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 9_999, 0));
        tracker.trackAssessment("professional", () -> {
            tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 1_000, 0));
            return UUID.randomUUID();
        });
        tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 9_999, 0));
        tracker.trackAssessment("professional", UUID::randomUUID);

        assertThat(output).contains("jevInputTokens=1000").doesNotContain("9999");
        assertThat(output.toString().split("Estimated provider cost", -1)).hasSize(2);
    }

    @Test
    void aNestedUnitContributesToTheOuterLineInsteadOfLoggingItsOwn(CapturedOutput output) {
        tracker.trackAssessment("professional", () -> tracker.trackAssessment("professional", () -> {
            tracker.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 1_000, 0));
            return UUID.randomUUID();
        }));

        assertThat(output.toString().split("Estimated provider cost", -1)).hasSize(2);
    }

    @Test
    void theDisabledTrackerOnlyRunsTheWork(CapturedOutput output) {
        var disabled = ProviderCostTracker.disabled();
        UUID id = UUID.randomUUID();

        assertThat(disabled.trackAssessment("professional", () -> {
            disabled.record(ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 1_000, 0));
            return id;
        })).isEqualTo(id);
        assertThat(output).doesNotContain("Estimated provider cost");
    }
}
