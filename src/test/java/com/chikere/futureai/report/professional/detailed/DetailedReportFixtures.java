package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.decision.professional.ProfessionalMaterialityCalibrationCases;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeVersions;
import com.chikere.futureai.narrative.professional.paid.StoredProfessionalPaidNarrative;
import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportSource;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The full stored-artefact chain for detailed-report tests, built only from captured or fixture data. The
 * responsibility analyses are the captured seven-role live Jev probe. Role-level judgments: Nurse, Java and
 * Accountant use the live AI Exposure effects reconstructed in Stage 2; the other four roles have no captured
 * role-level result and use a local fixture as a PLACEHOLDER (Electrician and Teacher the nurse fixture, Solicitor
 * and Customer-service the architect fixture), so their four measures are not calibration data.
 */
public final class DetailedReportFixtures {

    public static final Map<String, String> ROLES = roles();

    private DetailedReportFixtures() {
    }

    public record Chain(PurchasedProfessionalReportSnapshot role, ProfessionalResponsibilityAnalysisSnapshot analysis,
            ProfessionalResponsibilityGuidanceSnapshot guidance) {

        public ProfessionalPaidNarrativeContext context() {
            return ProfessionalPaidNarrativeContext.from(role.report(), analysis, guidance);
        }
    }

    public static Chain chain(String run) {
        var analysis = AnalysisSnapshots.live(run);
        var guidance = new ProfessionalResponsibilityGuidanceSnapshot(
                UUID.nameUUIDFromBytes(("guidance" + run).getBytes()), analysis.assessmentId(), analysis.snapshotId(),
                ProfessionalGuidancePolicyV1.POLICY_VERSION, Instant.parse("2026-10-27T10:00:00Z"),
                new ProfessionalGuidancePolicyV1().decide(analysis));
        return new Chain(roleSnapshot(assessment(run)), analysis, guidance);
    }

    /**
     * The stored Professional assessment a captured run belongs to: the id the captured analysis records, the
     * responsibilities, work description and skills that analysis was made from, and the role-level judgments
     * described above (placeholders for four roles). The profession is the role's display name.
     */
    public static StoredProfessionalAssessment assessment(String run) {
        var analysis = AnalysisSnapshots.live(run);
        var input = analysis.input();
        var state = new ProfessionalAssessmentState(ROLES.get(run), input.workDescription(), input.responsibilities(),
                input.skills(), OptionalInt.of(8));
        return assessment(analysis.assessmentId(), state, run);
    }

    public static PurchasedProfessionalReportSnapshot roleSnapshot(UUID assessmentId, String profession, String run) {
        var state = new ProfessionalAssessmentState(profession, "The work described for " + profession + ".",
                List.of("Responsibility"), List.of("Skill"), OptionalInt.of(8));
        return roleSnapshot(assessment(assessmentId, state, run));
    }

    private static StoredProfessionalAssessment assessment(UUID assessmentId, ProfessionalAssessmentState state,
            String run) {
        var judgments = switch (run) {
            case "registered-nurse-8" -> ProfessionalMaterialityCalibrationCases.judgments(
                    ProfessionalMaterialityCalibrationCases.registeredNurse());
            case "java-software-developer-8" -> ProfessionalMaterialityCalibrationCases.judgments(
                    ProfessionalMaterialityCalibrationCases.javaSoftwareDeveloper());
            case "management-accountant-8" -> ProfessionalMaterialityCalibrationCases.judgments(
                    ProfessionalMaterialityCalibrationCases.managementAccountant());
            case "electrician-8", "secondary-school-teacher-8" ->
                    new LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(state);
            default -> new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        };
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);
        return new StoredProfessionalAssessment(assessmentId, Instant.parse("2026-10-27T09:00:00Z"), state,
                judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private static PurchasedProfessionalReportSnapshot roleSnapshot(StoredProfessionalAssessment assessment) {
        UUID assessmentId = assessment.id();
        ProfessionalReport report = new ProfessionalReportAssembler().assemble(new ProfessionalReportSource(assessment,
                Optional.empty(), Optional.empty(), Optional.empty()), ProfessionalReportTier.PAID);
        return new PurchasedProfessionalReportSnapshot(UUID.nameUUIDFromBytes(("entitlement" + assessmentId).getBytes()),
                assessmentId, report.reportVersion(), Instant.parse("2026-10-27T09:05:00Z"), report);
    }

    public static StoredProfessionalPaidNarrative fallback(Chain chain) {
        return new StoredProfessionalPaidNarrative(UUID.nameUUIDFromBytes(("narrative" + chain.analysis().snapshotId())
                .getBytes()), chain.analysis().assessmentId(), chain.analysis().snapshotId(), chain.guidance().guidanceId(),
                ProfessionalPaidNarrativeVersions.CURRENT, PaidNarrativeStatus.FALLBACK, "none", Optional.empty(),
                Optional.empty(), 0, Optional.of("PROVIDER_NOT_CONFIGURED"), Instant.parse("2026-10-27T10:01:00Z"),
                Optional.empty());
    }

    public static StoredProfessionalPaidNarrative available(Chain chain, ProfessionalPaidNarrative narrative) {
        return new StoredProfessionalPaidNarrative(UUID.nameUUIDFromBytes(("narrative" + chain.analysis().snapshotId())
                .getBytes()), chain.analysis().assessmentId(), chain.analysis().snapshotId(), chain.guidance().guidanceId(),
                ProfessionalPaidNarrativeVersions.CURRENT, PaidNarrativeStatus.AVAILABLE, "test-double",
                Optional.of("test-model"), Optional.of("req_test"), 1, Optional.empty(),
                Instant.parse("2026-10-27T10:01:00Z"), Optional.of(narrative));
    }

    public static ProfessionalDetailedReportSnapshot snapshot(Chain chain, StoredProfessionalPaidNarrative narrative) {
        var report = new ProfessionalDetailedReportAssembler().assemble(chain.role(), chain.analysis(), chain.guidance(),
                narrative);
        return new ProfessionalDetailedReportSnapshot(UUID.nameUUIDFromBytes(("report" + chain.analysis().snapshotId())
                .getBytes()), chain.analysis().assessmentId(), chain.role().entitlementId(), chain.analysis().snapshotId(),
                chain.guidance().guidanceId(), narrative.narrativeId(), narrative.status(),
                ProfessionalDetailedReportVersions.REPORT, ProfessionalDetailedReportVersions.PDF,
                Instant.parse("2026-10-27T10:02:00Z"), report);
    }

    /** A narrative that passes every rule, with one entry per id in order. Hand-written test copy, not model output. */
    public static ProfessionalPaidNarrative validNarrative(ProfessionalPaidNarrativeContext context) {
        return new ProfessionalPaidNarrative(
                "Your work combines information tasks where AI may assist with responsibilities that depend on your "
                        + "own judgement and contact with people.\n\nThe sections below set out where assistance may help "
                        + "and where your contribution matters most.",
                "Read together, your measures describe work with some scope for AI assistance alongside a clear need "
                        + "for your own judgement.",
                "Across your responsibilities, some involve information work where AI may assist, while others depend "
                        + "on being present or on your interaction with people.",
                items(context.opportunityIds(), "This is a sensible place to try AI assistance with preparation and "
                        + "checking, keeping your review in place."),
                items(context.humanValueIds(), "Your own contribution matters here because it depends on your "
                        + "judgement and your relationships with people."),
                "These skills help you use AI assistance carefully while strengthening the parts of your work that rely "
                        + "on you.",
                items(context.skillIds(), "This skill supports the responsibilities linked to it and keeps your review "
                        + "at the centre of your work."),
                "Work through the three phases in order, keeping each step small and reviewed.",
                items(context.planActionIds(), "Keep this step small and note what you learn."),
                "Small, reviewed steps let you adapt your work in a practical way while keeping your judgement central.");
    }

    public static List<ItemExplanation> items(List<String> ids, String explanation) {
        return ids.stream().map(id -> new ItemExplanation(id, explanation)).toList();
    }

    private static Map<String, String> roles() {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("registered-nurse-8", "Registered Nurse");
        roles.put("java-software-developer-8", "Java Software Developer");
        roles.put("management-accountant-8", "Management Accountant");
        roles.put("electrician-8", "Electrician");
        roles.put("secondary-school-teacher-8", "Secondary-school Teacher");
        roles.put("solicitor-8", "Solicitor");
        roles.put("customer-service-adviser-8", "Customer-service Adviser");
        return java.util.Collections.unmodifiableMap(roles);
    }
}
