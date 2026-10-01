package com.chikere.futureai.evidence.alevel.fixture;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceRequest;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalFixtureALevelEvidenceProviderTest {

    private static final UUID ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");

    @Test
    void supportsOnlyTheExactConfiguredFixtureIds() {
        assertThat(LocalFixtureALevelEvidenceProvider.supportedFixtureIds()).containsExactly(
                "art_design_media", "biology_chemistry_mathematics", "english_history_politics",
                "mathematics_physics_computer_science", "maths_economics_geography", "mixed_balanced");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureALevelEvidenceProvider("maths economics geography"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureALevelEvidenceProvider(null));
    }

    @Test
    void returnsDeterministicDevelopmentDataThatNeverClaimsToBeOfficial() {
        var provider = new LocalFixtureALevelEvidenceProvider("biology_chemistry_mathematics");

        ALevelEvidenceBundle first = provider.gather(request());
        ALevelEvidenceBundle second = provider.gather(request());

        assertThat(second).isEqualTo(first);
        assertThat(provider.source()).isEqualTo(EvidenceSource.LOCAL_FIXTURE);
        assertThat(provider.providerIdentifier()).isEqualTo("local-fixture:biology_chemistry_mathematics");
        assertThat(first.evidenceVersion()).isEqualTo("a-level-evidence-v1");
        assertThat(first.status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(first.items()).hasSize(1).allSatisfy(item -> {
            assertThat(item.sourceQuality()).isEqualTo(EvidenceSourceQuality.DEVELOPMENT_FIXTURE);
            // No fake university or government URL is ever produced.
            assertThat(item.sourceUrl()).isEmpty();
            assertThat(item.publisher()).contains("AI Career Impact development fixture");
            assertThat(item.summary()).contains("local example");
            assertThat(item.geography()).contains("United Kingdom");
        });
    }

    @Test
    void prerequisiteNotesAreFramedAsFactsToCheckRatherThanAsAVerdict() {
        var item = new LocalFixtureALevelEvidenceProvider("maths_economics_geography")
                .gather(request()).items().getFirst();

        assertThat(item.category()).isEqualTo(ALevelEvidenceCategory.DEGREE_PREREQUISITES);
        assertThat(item.summary()).contains("some Mathematics-heavy Economics courses ask for",
                "Always check each course's own published requirements");
        assertThat(item.relevanceNote()).contains("does not change your assessment")
                .contains("not a judgement on your subjects");
        assertThat(item.summary().toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("you cannot", "you will not", "bad choice", "wrong subject",
                        "you should", "you must");
    }

    @Test
    void contrastingFixturesUseContrastingEvidenceCategories() {
        assertThat(new LocalFixtureALevelEvidenceProvider("mathematics_physics_computer_science")
                .gather(request()).items().getFirst().category())
                .isEqualTo(ALevelEvidenceCategory.DEGREE_PREREQUISITES);
        assertThat(new LocalFixtureALevelEvidenceProvider("english_history_politics")
                .gather(request()).items().getFirst().category())
                .isEqualTo(ALevelEvidenceCategory.SUBJECT_PATHWAY_CONTEXT);
        assertThat(new LocalFixtureALevelEvidenceProvider("mixed_balanced")
                .gather(request()).items().getFirst().category())
                .isEqualTo(ALevelEvidenceCategory.SKILL_DEMAND_CONTEXT);
        assertThat(new LocalFixtureALevelEvidenceProvider("art_design_media")
                .gather(request()).items().getFirst().category())
                .isEqualTo(ALevelEvidenceCategory.COURSE_ENTRY_CONTEXT);
    }

    private ALevelEvidenceRequest request() {
        return new ALevelEvidenceRequest(ID, List.of("Mathematics", "Economics"), List.of("Economics"),
                List.of(), "United Kingdom", JourneyType.A_LEVEL);
    }
}
