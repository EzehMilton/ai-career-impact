package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleJevProvider;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleStateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 5C live module probe: one real {@code university-module-v1} Jev call per representative course, each for up
 * to eight modules. Calls Jev only: never OpenAI, Stripe or the database. Never selected by ordinary test or verify
 * runs.
 *
 * <pre>
 * TYPESAFE_API_KEY=... ./mvnw -Plive-university-module-probe verify -Dtest=NoSuchTest \
 *     -Dsurefire.failIfNoSpecifiedTests=false [-Duniversity.module.probe.case=nursing]
 * </pre>
 *
 * Writes {@code .live-smoke/university-module/<time>/}, outside target/ so a later clean does not delete it.
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "university.module.probe.enabled", matches = "true")
class UniversityModuleProbeLiveIT {

    @Test
    void probesModuleAnalysisForTheRepresentativeCourses() throws Exception {
        // Resolved before any client exists, so an unknown case id fails without a provider call.
        var cases = UniversityModuleProbeCases.select(System.getProperty("university.module.probe.case", ""));
        String model = System.getProperty("spring.ai.typesafe.model", "jev-latest");
        var client = RecordingTypeSafeClient.create(System.getenv("TYPESAFE_API_KEY"), model);
        var provider = new UniversityModuleJevProvider(client, new UniversityModuleSchema(),
                new UniversityModuleStateMapper(), model, ProviderCostTracker.disabled());
        Instant startedAt = Instant.now();

        var runs = new UniversityModuleProbe(provider, client).run(cases);

        Path directory = Path.of(".live-smoke", "university-module", startedAt.toString().replace(":", "-"));
        UniversityModuleProbe.write(directory, "LIVE", model, startedAt, runs);
        System.out.println("University module probe written to " + directory.toAbsolutePath());
        assertThat(runs).hasSize(cases.size());
    }
}
