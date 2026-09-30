package com.chikere.futureai.evidence.professional.fixture;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.professional.application.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFixtureProfessionalEvidenceProviderTest {
    @Test
    void exactFixtureSelectionIsDeterministicAndClearlyLocal() {
        var provider = new LocalFixtureProfessionalEvidenceProvider("electrician");
        var request = new ProfessionalEvidenceRequest(UUID.randomUUID(), "Any entered title",
                List.of("Install wiring"), List.of("Diagnostics"), "United Kingdom",
                JourneyType.PROFESSIONAL, Optional.empty());

        var first = provider.gather(request);
        var second = provider.gather(request);

        assertThat(first).isEqualTo(second);
        assertThat(first.source()).isEqualTo(EvidenceSource.LOCAL_FIXTURE);
        assertThat(first.providerIdentifier()).isEqualTo("local-fixture:electrician");
        assertThat(first.items()).allSatisfy(item -> {
            assertThat(item.sourceQuality()).isEqualTo(EvidenceSourceQuality.DEVELOPMENT_FIXTURE);
            assertThat(item.sourceUrl()).isEmpty();
            assertThat(item.sourceName()).contains("Local fixture");
        });
    }

    @Test
    void unsupportedOrFuzzyIdsFailClearly() {
        assertThatThrownBy(() -> new LocalFixtureProfessionalEvidenceProvider("Electrician"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> new LocalFixtureProfessionalEvidenceProvider("electric"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
    }
}
