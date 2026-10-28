package com.chikere.futureai.coursemodule.university.policy;

import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** The frozen Stage 5C live module captures, rebuilt into the judgment results the adapter returned. No Jev call. */
public final class ModuleProbeFixtures {

    public static final Path DIRECTORY = Path.of("src", "test", "resources", "university", "module-probe-v1");
    public static final List<String> COURSES = List.of("business-management", "computer-science",
            "english-literature", "graphic-design", "law", "mechanical-engineering", "nursing");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ModuleProbeFixtures() {
    }

    public static UniversityModuleJudgmentResult load(String course) {
        try {
            JsonNode fixture = JSON.readTree(DIRECTORY.resolve(course + ".json").toFile());
            List<UniversityModuleJudgmentSet> modules = new ArrayList<>();
            int index = 1;
            for (JsonNode module : fixture.get("moduleJudgments")) {
                modules.add(new UniversityModuleJudgmentSet(index++, module.get("id").asText(),
                        module.get("title").asText(), module.get("described_clearly").asDouble(),
                        module.get("professional_standards").asDouble(), scored(module, "ai_learning_assistance"),
                        scored(module, "independent_demonstration"), scored(module, "human_judgement"),
                        scored(module, "interpersonal_work"), scored(module, "practical_application"),
                        scored(module, "technical_quantitative")));
            }
            JsonNode provider = fixture.get("provider");
            return new UniversityModuleJudgmentResult(fixture.get("schemaVersion").asText(),
                    UniversityModuleJudgmentSource.JEV, Optional.ofNullable(provider.get("model").asText(null)),
                    Optional.ofNullable(provider.get("requestId").asText(null)),
                    OptionalLong.of(provider.get("inputTokens").asLong()),
                    OptionalLong.of(provider.get("outputTokens").asLong()), modules);
        }
        catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static Map<String, UniversityModuleJudgmentResult> all() {
        Map<String, UniversityModuleJudgmentResult> all = new LinkedHashMap<>();
        COURSES.forEach(course -> all.put(course, load(course)));
        return all;
    }

    private static ModuleScoredJudgment scored(JsonNode module, String judgment) {
        JsonNode node = module.get(judgment);
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        node.get("distribution").properties().forEach(level ->
                distribution.put(Integer.parseInt(level.getKey()), level.getValue().asDouble()));
        return new ModuleScoredJudgment(node.get("value").asDouble(), distribution,
                OptionalDouble.of(node.get("confidence").asDouble()),
                node.get("nearestLevel").isNull() ? OptionalInt.empty() : OptionalInt.of(node.get("nearestLevel").asInt()),
                Optional.empty());
    }
}
