package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.policy.ModuleProbeFixtures;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.report.university.application.UniversityReportTier;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.IntStream;

/** Test data for the module-analysis lifecycle: assessments, entitlements and fixture-backed judgments. No Jev. */
public final class ModuleAnalysisTestData {

    public static final Instant ASSESSED_AT = Instant.parse("2026-10-28T08:00:00Z");

    private ModuleAnalysisTestData() {
    }

    /** An assessment with modules named "Module 1" to "Module n". */
    public static StoredUniversityAssessment assessment(UUID id, int modules) {
        return assessment(id, IntStream.rangeClosed(1, modules).mapToObj(index -> "Module " + index).toList());
    }

    public static StoredUniversityAssessment assessment(UUID id, List<String> modules) {
        var state = new UniversityAssessmentState("BSc Nursing (Adult)",
                "A three-year pre-registration course with clinical placements and university study.", modules,
                List.of("Clinical skills", "Communication"), List.of("Adult nursing"), Optional.empty(),
                Optional.of("United Kingdom"));
        var judgments = new LocalFixtureUniversityJudgmentProvider("nursing").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(id, ASSESSED_AT, state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE, judgments.schemaVersion(),
                        decision.policyVersion()), Optional.empty());
    }

    public static UniversityReportEntitlement entitlement(UUID assessmentId, UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(UUID.randomUUID(), assessmentId, UniversityReportTier.PAID, status,
                UniversityPaymentProvider.COMPLIMENTARY, "complimentary_test",
                URI.create("http://localhost/university-assessment/" + assessmentId), "complimentary",
                Optional.empty(), Optional.of("university-report-v1"), ASSESSED_AT,
                status == UniversityEntitlementStatus.ACTIVE ? Optional.of(ASSESSED_AT) : Optional.empty());
    }

    /**
     * The frozen live judgments of one probe course, re-keyed onto the request's modules in order, as if Jev had
     * answered this request. The request may hold at most eight modules, as the fixtures do.
     */
    public static UniversityModuleJudgmentResult fixtureJudgments(String course, UniversityModuleAnalysisRequest request) {
        var fixture = ModuleProbeFixtures.load(course);
        List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (var entry : request.keyedModules().entrySet()) {
            var source = fixture.modules().get(index - 1);
            sets.add(new UniversityModuleJudgmentSet(index++, entry.getKey(), entry.getValue(), source.describedClearly(),
                    source.professionalStandards(), source.aiLearningAssistance(), source.independentDemonstration(),
                    source.humanJudgement(), source.interpersonalWork(), source.practicalApplication(),
                    source.technicalQuantitative()));
        }
        return new UniversityModuleJudgmentResult(fixture.schemaVersion(), UniversityModuleJudgmentSource.JEV,
                fixture.model(), Optional.of("req_fixture_" + course), OptionalLong.of(9986), OptionalLong.of(1332), sets);
    }
}
