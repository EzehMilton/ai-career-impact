package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.policy.ModuleProbeFixtures;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module-analysis snapshots of the seven frozen live courses, as Stage 5E would store them: the frozen judgments,
 * classified by the frozen module policy, with each module's id its position in the course's full module list.
 */
final class GuidanceFixtures {

    private static final ObjectMapper JSON = new ObjectMapper();

    private GuidanceFixtures() {
    }

    static UniversityModuleAnalysisSnapshot snapshot(String course) {
        try {
            JsonNode fixture = JSON.readTree(ModuleProbeFixtures.DIRECTORY.resolve(course + ".json").toFile());
            List<String> allModules = new ArrayList<>();
            fixture.get("courseContext").get("allModules").forEach(module -> allModules.add(module.asText()));
            List<String> skills = new ArrayList<>();
            fixture.get("courseContext").get("skillsDeveloped").forEach(skill -> skills.add(skill.asText()));
            var probe = ModuleProbeFixtures.load(course);
            // The probe sent modules in its own selection order; a stored snapshot is always in course order.
            List<UniversityModuleJudgmentSet> sorted = probe.modules().stream()
                    .sorted(Comparator.comparingInt(set -> allModules.indexOf(set.moduleTitle()))).toList();
            List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
            for (int index = 0; index < sorted.size(); index++) {
                var set = sorted.get(index);
                sets.add(new UniversityModuleJudgmentSet(index + 1, UniversityModuleAnalysisRequest.key(index + 1),
                        set.moduleTitle(), set.describedClearly(), set.professionalStandards(), set.aiLearningAssistance(),
                        set.independentDemonstration(), set.humanJudgement(), set.interpersonalWork(),
                        set.practicalApplication(), set.technicalQuantitative()));
            }
            var judgments = new UniversityModuleJudgmentResult(probe.schemaVersion(), probe.source(), probe.model(),
                    probe.providerRequestId(), probe.inputTokens(), probe.outputTokens(), sets);
            var classifications = new UniversityModulePolicyV1().classify(judgments);
            List<UniversityModuleAnalysisItem> items = new ArrayList<>();
            for (int index = 0; index < sets.size(); index++) {
                items.add(new UniversityModuleAnalysisItem(allModules.indexOf(sets.get(index).moduleTitle()) + 1,
                        sets.get(index), classifications.get(index), classifications.get(index).customerLabel()));
            }
            return new UniversityModuleAnalysisSnapshot(UUID.nameUUIDFromBytes(("snapshot-" + course).getBytes()),
                    UUID.nameUUIDFromBytes(course.getBytes()), judgments.schemaVersion(),
                    UniversityModulePolicyV1.POLICY_VERSION, judgments.source(), judgments.model(),
                    judgments.providerRequestId(), judgments.inputTokens(), judgments.outputTokens(),
                    fixture.get("courseContext").get("courseTitle").asText(),
                    fixture.get("courseContext").get("courseDescription").asText(), skills, items,
                    Instant.parse("2026-10-28T09:00:00Z"), Instant.parse("2026-10-28T09:00:01Z"));
        }
        catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The same snapshot with only the given module ids, re-keyed in course order and reclassified. */
    static UniversityModuleAnalysisSnapshot subset(UniversityModuleAnalysisSnapshot snapshot, List<Integer> moduleIds) {
        var kept = snapshot.items().stream().filter(item -> moduleIds.contains(item.moduleId())).toList();
        List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
        for (int index = 0; index < kept.size(); index++) {
            var set = kept.get(index).judgments();
            sets.add(new UniversityModuleJudgmentSet(index + 1, UniversityModuleAnalysisRequest.key(index + 1),
                    set.moduleTitle(), set.describedClearly(), set.professionalStandards(), set.aiLearningAssistance(),
                    set.independentDemonstration(), set.humanJudgement(), set.interpersonalWork(),
                    set.practicalApplication(), set.technicalQuantitative()));
        }
        var judgments = new UniversityModuleJudgmentResult(snapshot.schemaVersion(), snapshot.judgmentSource(),
                snapshot.model(), snapshot.providerRequestId(), snapshot.inputTokens(), snapshot.outputTokens(), sets);
        var classifications = new UniversityModulePolicyV1().classify(judgments);
        List<UniversityModuleAnalysisItem> items = new ArrayList<>();
        for (int index = 0; index < sets.size(); index++) {
            items.add(new UniversityModuleAnalysisItem(kept.get(index).moduleId(), sets.get(index),
                    classifications.get(index), classifications.get(index).customerLabel()));
        }
        return new UniversityModuleAnalysisSnapshot(snapshot.snapshotId(), snapshot.assessmentId(),
                snapshot.schemaVersion(), snapshot.policyVersion(), snapshot.judgmentSource(), snapshot.model(),
                snapshot.providerRequestId(), snapshot.inputTokens(), snapshot.outputTokens(), snapshot.courseTitle(),
                snapshot.courseDescription(), snapshot.skillsDeveloped(), items, snapshot.createdAt(),
                snapshot.completedAt());
    }

    static Map<String, UniversityModuleAnalysisSnapshot> all() {
        Map<String, UniversityModuleAnalysisSnapshot> all = new LinkedHashMap<>();
        ModuleProbeFixtures.COURSES.forEach(course -> all.put(course, snapshot(course)));
        return all;
    }
}
