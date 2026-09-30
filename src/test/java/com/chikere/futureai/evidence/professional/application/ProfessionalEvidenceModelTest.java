package com.chikere.futureai.evidence.professional.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalEvidenceModelTest {
    @Test
    void rejectsUnsafeLinksBlankSourcesAndIncompleteMetrics() {
        assertThatThrownBy(() -> item(" ", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item("Source", Optional.of(URI.create("javascript:alert(1)")),
                Optional.empty(), Optional.empty(), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item("Source", Optional.empty(), Optional.of("Count"),
                Optional.of(BigDecimal.ONE), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    private ProfessionalEvidenceItem item(String source, Optional<URI> url, Optional<String> metricName,
            Optional<BigDecimal> metricValue, Optional<String> metricUnit) {
        return new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.SKILL_DEMAND, "Title", "Summary", source,
                Optional.empty(), url, Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH,
                metricName, metricValue, metricUnit, EvidenceSourceQuality.PRIMARY, "Relevant context");
    }
}
