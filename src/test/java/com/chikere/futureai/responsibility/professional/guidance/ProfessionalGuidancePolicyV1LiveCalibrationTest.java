package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.OpportunityType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardImportance;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.ResponsibleUseSafeguard;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden regression on the captured seven-role live Jev probe (jev-1.14.0, 2026-10-27): stable, meaningful
 * properties of the guidance, not every ranking score. No Jev or OpenAI call is made.
 */
class ProfessionalGuidancePolicyV1LiveCalibrationTest {

    private static final List<String> ROLES = List.of("registered-nurse-8", "java-software-developer-8",
            "management-accountant-8", "electrician-8", "secondary-school-teacher-8", "solicitor-8",
            "customer-service-adviser-8");
    private final ProfessionalGuidancePolicyV1 policy = new ProfessionalGuidancePolicyV1();

    @Test
    void registeredNurse() {
        var guidance = guidance("registered-nurse-8");
        // Documentation outranks medication and emergency work, which are never opportunities.
        assertThat(opportunities(guidance)).first().isEqualTo("Record observations and clinical information");
        assertThat(opportunities(guidance)).doesNotContain("Administer medication safely",
                "Respond to emergencies and unexpected situations", "Monitor patients and assess changes in their condition");
        assertThat(guidance.opportunities()).allSatisfy(o -> assertThat(o.type()).isNotEqualTo(
                OpportunityType.SUPPORT_FOR_PARTS_UNDER_YOUR_JUDGEMENT));
        assertThat(humanValue(guidance)).contains("Support patients and communicate with families",
                "Administer medication safely");
        assertThat(safeguards(guidance)).contains(SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK,
                SafeguardType.ESCALATE_UNCERTAINTY);
        assertThat(pilot(guidance)).isEqualTo("r3");
    }

    @Test
    void javaSoftwareDeveloper() {
        var guidance = guidance("java-software-developer-8");
        assertThat(opportunities(guidance)).containsExactly("Improve automated tests and code quality",
                "Review pull requests and technical designs", "Investigate and resolve production incidents");
        assertThat(opportunities(guidance)).doesNotContain("Discuss requirements with product and business teams",
                "Mentor less experienced developers");
        assertThat(humanValue(guidance)).contains("Discuss requirements with product and business teams",
                "Mentor less experienced developers", "Make decisions about APIs, integrations and data models");
        assertThat(guidance.safeguards()).extracting(ResponsibleUseSafeguard::importance).containsOnly(SafeguardImportance.STANDARD);
        assertThat(safeguards(guidance)).contains(SafeguardType.HUMAN_REVIEW_OF_AI_OUTPUT, SafeguardType.KEEP_DECISIONS_HUMAN);
    }

    @Test
    void managementAccountant() {
        var guidance = guidance("management-accountant-8");
        assertThat(opportunities(guidance)).contains("Prepare monthly management accounts",
                "Produce financial reports for management");
        assertThat(humanValue(guidance)).containsExactly("Meet department managers to discuss financial performance");
        assertThat(guidance.safeguards()).filteredOn(s -> s.type() == SafeguardType.APPROVED_TOOLS_AND_CONFIDENTIALITY)
                .singleElement().satisfies(s -> assertThat(s.importance()).isEqualTo(SafeguardImportance.HIGH));
        assertThat(safeguards(guidance)).contains(SafeguardType.VERIFY_FACTS_FIGURES_AND_SOURCES);
    }

    @Test
    void electrician() {
        var guidance = guidance("electrician-8");
        // Every candidate carries O1; documentation and drawings rank ahead of the safety-critical regulations item.
        assertThat(opportunities(guidance)).containsExactly("Complete safety documentation",
                "Read technical drawings and specifications", "Follow electrical safety regulations");
        assertThat(guidance.opportunities().get(2).scoreComponents().get("governanceSafetyCriticalPenalty")).isNegative();
        assertThat(opportunities(guidance)).doesNotContain("Install electrical wiring and equipment",
                "Inspect and test electrical systems", "Diagnose electrical faults", "Repair faulty wiring and components");
        assertThat(humanValue(guidance)).contains("Install electrical wiring and equipment",
                "Repair faulty wiring and components");
        assertThat(guidance.safeguards().getFirst().type()).isEqualTo(SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK);
        assertThat(skills(guidance)).contains(SkillFamily.HANDS_ON_PRACTICE_AND_SAFETY);
    }

    @Test
    void secondarySchoolTeacher() {
        var guidance = guidance("secondary-school-teacher-8");
        assertThat(humanValue(guidance)).contains("Manage classroom behaviour", "Communicate with parents",
                "Teach lessons to students");
        // Lesson planning (no oversight) and assessment (O2) rank ahead of the O1 safeguarding records, which remain listed.
        assertThat(opportunities(guidance)).containsExactly("Plan lessons", "Assess student work",
                "Maintain safeguarding and academic records");
        // The pilot starts with lesson planning, the opportunity needing least oversight, not with safeguarding records.
        assertThat(pilot(guidance)).isEqualTo("r1");
    }

    @Test
    void solicitor() {
        var guidance = guidance("solicitor-8");
        assertThat(opportunities(guidance)).containsExactlyInAnyOrder("Research relevant law and precedent",
                "Review contracts and legal documents", "Draft legal documents and correspondence");
        assertThat(opportunities(guidance)).doesNotContain("Negotiate with other parties", "Advise clients on legal options");
        assertThat(humanValue(guidance)).contains("Negotiate with other parties", "Advise clients on legal options",
                "Assess legal risks");
        assertThat(guidance.opportunities()).allSatisfy(o ->
                assertThat(o.constraints()).contains(GuidanceVocabulary.SafeUseConstraint.KEEP_DECISIONS_HUMAN));
        assertThat(skills(guidance).getFirst()).isEqualTo(SkillFamily.RESPONSIBLE_AI_USE);
        // All solicitor candidates carry O1, so the order is unchanged and strong review safeguards stay in place.
        assertThat(guidance.opportunities()).allSatisfy(o -> assertThat(o.rationale())
                .contains(ProfessionalGuidancePolicyV1.GOVERNANCE_O1));
        assertThat(safeguards(guidance)).contains(SafeguardType.ESCALATE_UNCERTAINTY, SafeguardType.HUMAN_REVIEW_OF_AI_OUTPUT);
    }

    @Test
    void customerServiceAdviser() {
        var guidance = guidance("customer-service-adviser-8");
        // Routine record support ranks ahead of handling sensitive information (both O2; higher accountability).
        assertThat(opportunities(guidance)).containsExactly("Investigate account problems", "Update customer records",
                "Handle sensitive customer information");
        assertThat(opportunities(guidance)).doesNotContain("Communicate difficult decisions to customers");
        assertThat(humanValue(guidance)).first().isEqualTo("Communicate difficult decisions to customers");
        assertThat(skills(guidance).getFirst()).isEqualTo(SkillFamily.COMMUNICATION_AND_RELATIONSHIPS);
        assertThat(safeguards(guidance)).contains(SafeguardType.APPROVED_TOOLS_AND_CONFIDENTIALITY);
    }

    @Test
    void everyRoleStaysWithinTheLimitsIsDeterministicAndExplainsEveryItem() {
        for (String role : ROLES) {
            var analysis = AnalysisSnapshots.live(role);
            var guidance = policy.decide(analysis);
            assertThat(policy.decide(analysis)).as(role).isEqualTo(guidance);
            assertThat(guidance.policyVersion()).isEqualTo("professional-guidance-policy-v1");
            assertThat(guidance.analysisSnapshotId()).isEqualTo(analysis.snapshotId());
            assertThat(guidance.opportunities()).as(role).hasSizeBetween(1, 3).allSatisfy(o -> assertThat(o.ruleId()).isNotBlank());
            assertThat(guidance.humanValueAreas()).as(role).hasSizeBetween(1, 3).allSatisfy(h -> assertThat(h.ruleId()).isNotBlank());
            assertThat(guidance.skillPriorities()).as(role).hasSizeBetween(2, 4).allSatisfy(s -> assertThat(s.ruleId()).isNotBlank());
            assertThat(guidance.safeguards()).as(role).hasSizeBetween(1, 4).allSatisfy(s -> assertThat(s.ruleId()).isNotBlank());
            for (List<PlanAction> phase : List.of(guidance.plan().days1To30(), guidance.plan().days31To60(),
                    guidance.plan().days61To90())) {
                assertThat(phase).as(role).hasSizeBetween(2, 4);
            }
            // An opportunity is never also a human-value area.
            assertThat(guidance.opportunities()).extracting(AiAssistanceOpportunity::responsibilityKey)
                    .doesNotContainAnyElementsOf(guidance.humanValueAreas().stream().map(HumanValueArea::responsibilityKey).toList());
        }
        // The plans differ by role because they follow each role's own responsibilities.
        assertThat(ROLES.stream().map(role -> pilotText(guidance(role))).distinct()).hasSize(ROLES.size());
    }

    private ProfessionalResponsibilityGuidance guidance(String role) {
        return policy.decide(AnalysisSnapshots.live(role));
    }

    private static List<String> opportunities(ProfessionalResponsibilityGuidance guidance) {
        return guidance.opportunities().stream().map(AiAssistanceOpportunity::responsibilityText).toList();
    }

    private static List<String> humanValue(ProfessionalResponsibilityGuidance guidance) {
        return guidance.humanValueAreas().stream().map(HumanValueArea::responsibilityText).toList();
    }

    private static List<SafeguardType> safeguards(ProfessionalResponsibilityGuidance guidance) {
        return guidance.safeguards().stream().map(ResponsibleUseSafeguard::type).toList();
    }

    private static List<SkillFamily> skills(ProfessionalResponsibilityGuidance guidance) {
        return guidance.skillPriorities().stream().map(SkillPriority::family).toList();
    }

    private static String pilot(ProfessionalResponsibilityGuidance guidance) {
        return pilotAction(guidance).responsibilityKeys().getFirst();
    }

    private static String pilotText(ProfessionalResponsibilityGuidance guidance) {
        return pilotAction(guidance).title();
    }

    private static PlanAction pilotAction(ProfessionalResponsibilityGuidance guidance) {
        return Stream.of(guidance.plan().days31To60()).flatMap(List::stream)
                .filter(action -> action.actionId().equals("P2_PILOT_WITH_REVIEW")).findFirst().orElseThrow();
    }
}
