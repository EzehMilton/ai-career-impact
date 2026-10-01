package com.chikere.futureai.product.application;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DevelopmentModeStatusTest {

    @Test
    void theInactiveStatusCarriesNoFixtureDetail() {
        DevelopmentModeStatus status = DevelopmentModeStatus.inactive();

        assertThat(status.active()).isFalse();
        assertThat(status.fixtureSummary()).isEmpty();
    }

    @Test
    void anActiveStatusMayDescribeItsFixtures() {
        DevelopmentModeStatus status = DevelopmentModeStatus.active("professional: electrician");

        assertThat(status.active()).isTrue();
        assertThat(status.fixtureSummary()).contains("professional: electrician");
        assertThat(DevelopmentModeStatus.active("  ").fixtureSummary()).isEmpty();
        assertThat(DevelopmentModeStatus.active(null).fixtureSummary()).isEmpty();
    }

    @Test
    void anInactiveStatusCanNeverClaimFixtures() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DevelopmentModeStatus(false, Optional.of("some fixture")))
                .withMessageContaining("only active development mode");
        assertThatNullPointerException()
                .isThrownBy(() -> new DevelopmentModeStatus(true, null));
    }
}
