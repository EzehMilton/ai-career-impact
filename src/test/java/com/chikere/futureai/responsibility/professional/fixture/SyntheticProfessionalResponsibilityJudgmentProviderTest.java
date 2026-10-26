package com.chikere.futureai.responsibility.professional.fixture;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentException;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.chikere.futureai.responsibility.professional.fixture.SyntheticProfessionalResponsibilityJudgmentProvider.Profile;
import static com.chikere.futureai.responsibility.professional.fixture.SyntheticProfessionalResponsibilityJudgmentProvider.RESPONSIBILITIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticProfessionalResponsibilityJudgmentProviderTest {

    private final SyntheticProfessionalResponsibilityJudgmentProvider provider =
            new SyntheticProfessionalResponsibilityJudgmentProvider();

    @Test
    void everySyntheticShapeIsDeterministicLabelledAndInternallyConsistent() {
        var request = request(new ArrayList<>(RESPONSIBILITIES.keySet()));
        var result = provider.judge(request);

        assertThat(result).isEqualTo(provider.judge(request));
        assertThat(result.source()).isEqualTo(ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE);
        assertThat(result.schemaVersion()).isEqualTo("professional-responsibility-v1");
        assertThat(result.model()).isEmpty();
        assertThat(result.answers(request)).isTrue();
        assertThat(RESPONSIBILITIES.keySet()).allMatch(text -> text.startsWith("Synthetic:"));
        assertThat(RESPONSIBILITIES.values()).containsExactlyInAnyOrder(Profile.values());
        for (ResponsibilityJudgmentSet set : result.responsibilities()) {
            for (ResponsibilityScoredJudgment judgment : List.of(set.assistanceScope(), set.contextualJudgement(),
                    set.humanInteraction(), set.physicalPresence(), set.outcomeAccountability())) {
                double expected = judgment.distribution().entrySet().stream()
                        .mapToDouble(level -> level.getKey() * level.getValue()).sum();
                assertThat(judgment.value()).isCloseTo(expected, org.assertj.core.api.Assertions.within(1e-9));
            }
        }
    }

    @Test
    void eachShapeStandsOutOnTheJudgmentItExercises() {
        assertThat(single(Profile.VAGUE).describedClearly()).isLessThan(0.5);
        assertThat(single(Profile.CLEARLY_ANALYSABLE).describedClearly()).isGreaterThan(0.9);
        assertHighest(Profile.HIGH_CONTEXTUAL_JUDGEMENT, ResponsibilityJudgmentSet::contextualJudgement);
        assertHighest(Profile.HIGH_HUMAN_INTERACTION, ResponsibilityJudgmentSet::humanInteraction);
        assertHighest(Profile.HIGH_PHYSICAL_PRESENCE, ResponsibilityJudgmentSet::physicalPresence);
        assertHighest(Profile.HIGH_OUTCOME_ACCOUNTABILITY, ResponsibilityJudgmentSet::outcomeAccountability);
        assertHighest(Profile.HIGH_ASSISTANCE_SCOPE, ResponsibilityJudgmentSet::assistanceScope);
    }

    @Test
    void anUnknownResponsibilityIsRefusedRatherThanInvented() {
        assertThatThrownBy(() -> provider.judge(request(List.of("Prepare monthly management accounts"))))
                .isInstanceOf(ResponsibilityJudgmentException.class).hasMessageContaining("synthetic catalogue");
    }

    private void assertHighest(Profile profile, Function<ResponsibilityJudgmentSet, ResponsibilityScoredJudgment> judgment) {
        double value = judgment.apply(single(profile)).value();
        for (Profile other : Profile.values()) {
            if (other != profile && other != Profile.VAGUE) {
                assertThat(value).as("%s vs %s", profile, other).isGreaterThan(judgment.apply(single(other)).value());
            }
        }
    }

    private ResponsibilityJudgmentSet single(Profile profile) {
        String text = RESPONSIBILITIES.entrySet().stream().filter(entry -> entry.getValue() == profile)
                .findFirst().orElseThrow().getKey();
        return provider.judge(request(List.of(text))).responsibilities().getFirst();
    }

    private static ProfessionalResponsibilityAnalysisRequest request(List<String> responsibilities) {
        return new ProfessionalResponsibilityAnalysisRequest(UUID.randomUUID(), "Synthetic role",
                "Synthetic work description.", responsibilities, List.of());
    }
}
