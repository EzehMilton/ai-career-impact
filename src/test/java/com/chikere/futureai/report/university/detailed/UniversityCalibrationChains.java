package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidance;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV1;
import com.chikere.futureai.coursemodule.university.policy.ModuleProbeFixtures;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeOutcome;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeVersions;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Offline calibration chains for the two courses reviewed by hand, with the module lists named in that review. Test
 * data only. Each module's judgments are the frozen Stage 5C live judgments of the closest module captured for the same
 * course (the capture has no "Machine Learning" or "Foundations of Nursing Practice"), so classification, guidance and
 * course profile run through the real frozen policies with no Jev call.
 *
 * <p>Computer Science keeps its captured group project, so the course has the team work a BSc usually includes. (With
 * an independent project but no judgement-led or interpersonal module, guidance policy v1's M4 action names an empty
 * module list and throws: a frozen-policy defect, reported separately and not worked around in production.)</p>
 */
public final class UniversityCalibrationChains {

    public static final String COMPUTER_SCIENCE = "computer-science";
    public static final String NURSING = "nursing";

    /** Module title, then the captured module whose frozen judgments it borrows. In course order. */
    private static final Map<String, Map<String, String>> MODULES = Map.of(
            COMPUTER_SCIENCE, ordered(
                    "Programming and Software Development", "Programming Fundamentals",
                    "Database Systems", "Databases",
                    "Artificial Intelligence", "Artificial Intelligence",
                    "Machine Learning", "Algorithms and Data Structures",
                    "Software Engineering", "Cybersecurity",
                    "Software Engineering Group Project", "Software Engineering Group Project"),
            NURSING, ordered(
                    "Foundations of Nursing Practice", "Professional, Legal and Ethical Practice",
                    "Anatomy and Physiology", "Anatomy and Physiology",
                    "Clinical Skills and Simulation", "Clinical Skills and Simulation",
                    "Pharmacology and Medicines Management", "Pharmacology and Medicines Management",
                    "Communication and Compassionate Care", "Communication and Compassionate Care",
                    "Evidence-Based Practice", "Evidence-Based Practice",
                    "Clinical Placements", "Clinical Placements"));

    private static final Map<String, String> COURSE_FIXTURE = Map.of(COMPUTER_SCIENCE, "computer_science",
            NURSING, "nursing");

    private UniversityCalibrationChains() {
    }

    public record Chain(StoredUniversityAssessment assessment, UniversityModuleAnalysisSnapshot modules,
            UniversityGuidance guidance, UniversityPaidNarrativeContext context) {
    }

    public static Chain chain(String course) {
        try {
            JsonNode capture = new ObjectMapper().readTree(ModuleProbeFixtures.DIRECTORY.resolve(course + ".json")
                    .toFile());
            JsonNode courseContext = capture.get("courseContext");
            List<String> skills = new ArrayList<>();
            courseContext.get("skillsDeveloped").forEach(skill -> skills.add(skill.asText()));
            Map<String, String> modules = MODULES.get(course);
            UUID id = UUID.nameUUIDFromBytes(("calibration-" + course).getBytes());
            var state = new UniversityAssessmentState(courseContext.get("courseTitle").asText(),
                    courseContext.get("courseDescription").asText(), List.copyOf(modules.keySet()), skills, List.of(),
                    Optional.empty(), Optional.of("United Kingdom"));
            var judgments = new LocalFixtureUniversityJudgmentProvider(COURSE_FIXTURE.get(course)).judge(state);
            var profile = new UniversityDecisionPolicyV2().decide(judgments);
            var assessment = new StoredUniversityAssessment(id, Instant.parse("2026-11-04T09:00:00Z"), state, judgments,
                    new UniversityAssessmentResult(profile, AssessmentSource.LOCAL_FIXTURE, judgments.schemaVersion(),
                            profile.policyVersion()), Optional.empty());

            var probe = ModuleProbeFixtures.load(course);
            List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
            int index = 1;
            for (var module : modules.entrySet()) {
                var source = probe.modules().stream().filter(set -> set.moduleTitle().equals(module.getValue()))
                        .findFirst().orElseThrow();
                sets.add(new UniversityModuleJudgmentSet(index, UniversityModuleAnalysisRequest.key(index),
                        module.getKey(), source.describedClearly(), source.professionalStandards(),
                        source.aiLearningAssistance(), source.independentDemonstration(), source.humanJudgement(),
                        source.interpersonalWork(), source.practicalApplication(), source.technicalQuantitative()));
                index++;
            }
            var result = new UniversityModuleJudgmentResult(probe.schemaVersion(), probe.source(), probe.model(),
                    Optional.of("req_calibration_" + course), probe.inputTokens(), probe.outputTokens(), sets);
            var classifications = new UniversityModulePolicyV1().classify(result);
            List<UniversityModuleAnalysisItem> items = new ArrayList<>();
            for (int position = 0; position < sets.size(); position++) {
                items.add(new UniversityModuleAnalysisItem(position + 1, sets.get(position),
                        classifications.get(position), classifications.get(position).customerLabel()));
            }
            var snapshot = new UniversityModuleAnalysisSnapshot(UUID.nameUUIDFromBytes(("modules-" + course).getBytes()),
                    id, result.schemaVersion(), UniversityModulePolicyV1.POLICY_VERSION, result.source(), result.model(),
                    result.providerRequestId(), result.inputTokens(), result.outputTokens(), state.courseTitle(),
                    state.courseDescription(), skills, items, Instant.parse("2026-11-04T09:00:01Z"),
                    Instant.parse("2026-11-04T09:00:02Z"));
            var guidance = new UniversityGuidancePolicyV1().decide(snapshot);
            var context = new UniversityDetailedReportAssembler().narrativeContext(assessment, snapshot, guidance);
            return new Chain(assessment, snapshot, guidance, context);
        }
        catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The v3 report of a calibration course, assembled with its hand-written calibration narrative. */
    public static UniversityDetailedReport report(String course) {
        var chain = chain(course);
        UniversityPaidNarrative narrative = course.equals(NURSING)
                ? UniversityCalibrationNarratives.nursing(chain.context())
                : UniversityCalibrationNarratives.computerScience(chain.context());
        return new UniversityDetailedReportAssembler().assemble(chain.assessment(), chain.modules(), chain.guidance(),
                outcome(UniversityPaidNarrativeStatus.AVAILABLE, Optional.of(narrative)));
    }

    /** The provisional v3 report of a calibration course: deterministic copy while the narrative is retryable. */
    public static UniversityDetailedReport provisional(String course) {
        var chain = chain(course);
        return new UniversityDetailedReportAssembler().assemble(chain.assessment(), chain.modules(), chain.guidance(),
                outcome(UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK, Optional.empty()));
    }

    public static UniversityPaidNarrativeOutcome outcome(UniversityPaidNarrativeStatus status,
            Optional<UniversityPaidNarrative> narrative) {
        return new UniversityPaidNarrativeOutcome(status, narrative, UniversityPaidNarrativeVersions.CURRENT,
                "calibration", Optional.of("calibration-model"), Optional.of("req_calibration"), 1,
                status == UniversityPaidNarrativeStatus.AVAILABLE ? Optional.empty() : Optional.of("CALIBRATION"));
    }

    /** The calibration report frozen as the lifecycle would store it: v3 document, v3 PDF. */
    public static UniversityDetailedReportSnapshot stored(String course, UUID assessmentId) {
        var chain = chain(course);
        var report = report(course);
        return new UniversityDetailedReportSnapshot(UUID.nameUUIDFromBytes(("report-" + course).getBytes()),
                assessmentId, UUID.nameUUIDFromBytes(("entitlement-" + course).getBytes()),
                chain.assessment().result().decisionPolicyVersion(), chain.modules().snapshotId(),
                chain.modules().schemaVersion(), chain.modules().policyVersion(), chain.guidance().policyVersion(),
                UniversityPaidNarrativeVersions.CURRENT, UniversityPaidNarrativeStatus.AVAILABLE,
                UniversityReportVersions.V3, UniversityReportVersions.PDF_V3, Instant.parse("2026-11-04T09:01:00Z"),
                report);
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
        return map;
    }
}
