package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.configuration.ConfigurationProblemException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplimentaryReportAccessTest {
    private static final String CODE = "Shared-Code-42";

    @Test
    void onlyTheExactConfiguredCodeIsAccepted() {
        var access = ComplimentaryReportAccess.enabledWith(CODE);

        assertThat(access.enabled()).isTrue();
        assertThat(access.accepts(CODE)).isTrue();
        assertThat(access.accepts("  " + CODE + "\n")).as("surrounding whitespace is ignored").isTrue();
        assertThat(access.accepts("shared-code-42")).as("case matters").isFalse();
        assertThat(access.accepts(CODE + "3")).isFalse();
        assertThat(access.accepts("Shared-Code-4")).isFalse();
        assertThat(access.accepts("")).isFalse();
        assertThat(access.accepts(null)).isFalse();
    }

    @Test
    void theCodeCanBeUsedAnyNumberOfTimes() {
        var access = ComplimentaryReportAccess.enabledWith(CODE);
        for (int i = 0; i < 1_000; i++) {
            assertThat(access.accepts(CODE)).isTrue();
        }
    }

    @Test
    void disabledAccessAcceptsNothing() {
        assertThat(ComplimentaryReportAccess.disabled().enabled()).isFalse();
        assertThat(ComplimentaryReportAccess.disabled().accepts(CODE)).isFalse();
    }

    @Test
    void theCodeIsNeverPartOfItsDescription() {
        assertThat(ComplimentaryReportAccess.enabledWith(CODE).toString()).doesNotContain(CODE)
                .isEqualTo("ComplimentaryReportAccess[enabled=true]");
    }

    @Test
    void theFlagAloneDecidesAndEnablingWithoutACodeRefusesToStart() {
        var configuration = new ComplimentaryReportConfiguration();

        assertThat(configuration.complimentaryReportAccess(false, CODE).enabled()).isFalse();
        assertThat(configuration.complimentaryReportAccess(true, CODE).accepts(CODE)).isTrue();
        for (String missing : new String[]{"", "   ", null}) {
            assertThatThrownBy(() -> configuration.complimentaryReportAccess(true, missing))
                    .isInstanceOf(ConfigurationProblemException.class)
                    .hasMessageContaining("app.complimentary-report.code");
        }
    }
}
