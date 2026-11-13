package com.chikere.futureai.judgment.professional.schema;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Score;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalJudgmentSchemaParityTest {

    @Test
    void theJevAdapterIsAnExactRepresentationOfTheCanonicalSchema() {
        var canonical = new ProfessionalJudgmentSchema();
        var jev = new com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema(canonical);

        assertThat(jev.version()).isEqualTo(canonical.version());
        assertThat(jev.questions().keySet()).containsExactlyElementsOf(canonical.questions().keySet());
        canonical.questions().forEach((id, question) -> {
            if (question instanceof ProfessionalPredicateQuestion predicate) {
                Noul adapted = (Noul) jev.questions().get(id);
                assertThat(adapted.instructions().asText()).isEqualTo(predicate.instructions());
                assertThat(adapted.criteria().whenTrue().asText()).isEqualTo(predicate.whenTrue());
                assertThat(adapted.criteria().whenFalse().asText()).isEqualTo(predicate.whenFalse());
            }
            else {
                ProfessionalScoreQuestion score = (ProfessionalScoreQuestion) question;
                Score adapted = (Score) jev.questions().get(id);
                assertThat(adapted.instructions().asText()).isEqualTo(score.instructions());
                assertThat(adapted.criteria()).extracting(level -> level.asText())
                        .containsExactlyElementsOf(score.levels().stream()
                                .map(ProfessionalScoreLevel::description).toList());
            }
        });
    }
}
