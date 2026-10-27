package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evaluation.university.UniversityBaselineHarness.Mode;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 5B live baseline: one real {@code university-v1} Jev call per representative course, decided by the
 * unchanged {@code university-decision-v1} policy. Calls Jev only: never OpenAI, Stripe or the database. Never
 * selected by ordinary test or verify runs.
 *
 * <pre>
 * TYPESAFE_API_KEY=... ./mvnw -Plive-university verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * Writes {@code .live-smoke/university/<time>/}, outside target/ so a later clean does not delete it.
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "university.baseline.live.enabled", matches = "true")
class UniversityBaselineLiveIT {

    @Test
    void capturesTheUniversityV1BaselineForTheRepresentativeCourses() throws Exception {
        String model = System.getProperty("spring.ai.typesafe.model", "jev-latest");
        TypeSafeClient client = TypeSafeClient.builder().apiKey(System.getenv("TYPESAFE_API_KEY"))
                .defaultModel(model).build();
        var provider = new UniversityJevJudgmentService(client, new UniversityJudgmentSchema(),
                new UniversityJevStateMapper(), model);
        Instant startedAt = Instant.now();

        var runs = new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1())
                .run(UniversityBaselineCases.ALL);

        Path directory = Path.of(".live-smoke", "university", startedAt.toString().replace(":", "-"));
        UniversityBaselineHarness.write(directory, Mode.LIVE, model, startedAt, runs);
        System.out.println("University baseline written to " + directory.toAbsolutePath());
        assertThat(runs).hasSize(UniversityBaselineCases.ALL.size());
    }
}
