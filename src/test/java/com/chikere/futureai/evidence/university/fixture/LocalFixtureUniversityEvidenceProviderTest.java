package com.chikere.futureai.evidence.university.fixture;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalFixtureUniversityEvidenceProviderTest {

    private static final UUID ID = UUID.fromString("7c6a1b4d-9e2f-4a3b-8c5d-6e7f8a9b0c1d");

    @Test
    void supportsOnlyTheExactConfiguredFixtureIds() {
        assertThat(LocalFixtureUniversityEvidenceProvider.supportedFixtureIds()).containsExactly(
                "computer_science", "economics", "graphic_design", "history",
                "mechanical_engineering", "nursing");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureUniversityEvidenceProvider("computer science"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureUniversityEvidenceProvider(null));
    }

    @Test
    void returnsDeterministicDevelopmentDataThatNeverClaimsToBeOfficial() {
        var provider = new LocalFixtureUniversityEvidenceProvider("nursing");

        UniversityEvidenceBundle first = provider.gather(request());
        UniversityEvidenceBundle second = provider.gather(request());

        assertThat(second).isEqualTo(first);
        assertThat(provider.source()).isEqualTo(EvidenceSource.LOCAL_FIXTURE);
        assertThat(provider.providerIdentifier()).isEqualTo("local-fixture:nursing");
        assertThat(first.evidenceVersion()).isEqualTo("university-evidence-v1");
        assertThat(first.status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(first.items()).hasSize(1).allSatisfy(item -> {
            assertThat(item.sourceQuality()).isEqualTo(EvidenceSourceQuality.DEVELOPMENT_FIXTURE);
            // No fake government URL is ever produced.
            assertThat(item.sourceUrl()).isEmpty();
            assertThat(item.publisher()).contains("AI Career Impact development fixture");
            assertThat(item.summary()).contains("local example");
            assertThat(item.relevanceNote()).contains("Representative UI data");
            assertThat(item.geography()).contains("United Kingdom");
        });
    }

    @Test
    void contrastingFixturesUseContrastingEvidenceCategories() {
        assertThat(new LocalFixtureUniversityEvidenceProvider("computer_science").gather(request())
                .items().getFirst().category().name()).isEqualTo("OCCUPATIONAL_DESTINATIONS");
        assertThat(new LocalFixtureUniversityEvidenceProvider("economics").gather(request())
                .items().getFirst().category().name()).isEqualTo("SKILL_DEMAND");
        assertThat(new LocalFixtureUniversityEvidenceProvider("mechanical_engineering").gather(request())
                .items().getFirst().category().name()).isEqualTo("COURSE_CONTENT");
    }

    private UniversityEvidenceRequest request() {
        return new UniversityEvidenceRequest(ID, "A course", List.of("A module"), List.of("A skill"),
                List.of(), Optional.empty(), "United Kingdom", JourneyType.UNIVERSITY);
    }
}
