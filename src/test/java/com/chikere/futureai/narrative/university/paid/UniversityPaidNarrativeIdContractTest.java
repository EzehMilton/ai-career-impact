package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The live PPE failure: module-2 returned where module-7 was required, rejected on the first draft and on the repair.
 * The id contract is never relaxed; a repair with the supplied ids passes. No OpenAI call.
 */
class UniversityPaidNarrativeIdContractTest {

    private final UniversityPaidNarrativeContext ppe = PpeNarrativeContext.context();
    private final UniversityPaidNarrativeValidator validator = new UniversityPaidNarrativeValidator();
    private final ScriptedPaidNarrativeGenerator generator = new ScriptedPaidNarrativeGenerator();

    @Test
    void theSuppliedIdsAreNotSequentialAndOverlapAcrossSections() {
        assertThat(ppe.moduleLearningIds()).isEqualTo(PpeNarrativeContext.AI_LEARNING_IDS);
        assertThat(ppe.humanCapabilityIds()).isEqualTo(PpeNarrativeContext.OWN_CAPABILITY_IDS);
        var valid = UniversityNarratives.valid(ppe);
        assertThat(validator.validate(valid, ppe)).isEqualTo(valid);
    }

    @Test
    void module2WhereModule7IsRequiredIsRejected() {
        assertIds(PpeNarrativeContext.withLearningIds(UniversityNarratives.valid(ppe), "module-2", "module-3", "module-1", "module-5"),
                "moduleLearningInsights: unknown id module-2");
        // Positional renumbering is rejected too.
        assertIds(PpeNarrativeContext.withLearningIds(UniversityNarratives.valid(ppe), "module-1", "module-2", "module-3", "module-4"),
                "moduleLearningInsights: unknown id module-2");
    }

    @Test
    void theSuppliedIdsInAnyOtherOrderAreRejected() {
        assertIds(PpeNarrativeContext.withLearningIds(UniversityNarratives.valid(ppe), "module-3", "module-7", "module-1", "module-5"),
                "moduleLearningInsights: ids are not in the deterministic order");
        var draft = UniversityNarratives.valid(ppe);
        var human = new ArrayList<>(draft.humanCapabilityInsights());
        Collections.reverse(human);
        assertIds(new UniversityPaidNarrative(draft.executiveSummary(), draft.measuresTogether(), draft.coursePattern(),
                draft.moduleLearningInsights(), human, draft.skillInsights(), draft.actionPlanNotes(),
                draft.aiStudyStrategy(), draft.closingPerspective()), "humanCapabilityInsights");
    }

    @Test
    void aRepairWithTheSuppliedIdsIsAvailableAfterTwoCallsAndTheRepairSeesTheRejectedIds() {
        var wrong = PpeNarrativeContext.withLearningIds(UniversityNarratives.valid(ppe), "module-2", "module-3", "module-1", "module-5");
        generator.then(wrong).then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var outcome = service().generate(ppe);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.narrative().orElseThrow().moduleLearningInsights()).extracting(ModuleLearningInsight::moduleId)
                .containsExactlyElementsOf(PpeNarrativeContext.AI_LEARNING_IDS);
        assertThat(generator.calls()).isEqualTo(2);
        assertThat(generator.lastFeedback.rule()).isEqualTo(UniversityPaidNarrativeValidator.IDS);
        assertThat(generator.lastFeedback.rejectedDraft()).contains(wrong);
    }

    @Test
    void wrongIdsTwiceAreAFinalFallbackWithNoNarrativeAndNoThirdCall() {
        var wrong = PpeNarrativeContext.withLearningIds(UniversityNarratives.valid(ppe), "module-2", "module-3", "module-1", "module-5");
        generator.then(wrong).then(wrong);

        var outcome = service().generate(ppe);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(outcome.narrative()).isEmpty();
        assertThat(outcome.reason()).contains("VALIDATION_FAILED_AFTER_REPAIR:IDS_MATCH_DETERMINISTIC_ORDER"
                + ">IDS_MATCH_DETERMINISTIC_ORDER");
        assertThat(generator.calls()).isEqualTo(2);
    }

    private UniversityPaidNarrativeService service() {
        return new UniversityPaidNarrativeService(() -> Optional.of(generator), validator, AssessmentTrace.disabled());
    }

    private void assertIds(UniversityPaidNarrative narrative, String reason) {
        assertThatThrownBy(() -> validator.validate(narrative, ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(UniversityPaidNarrativeValidator.IDS);
                    assertThat(violation.triggeringText()).contains(reason);
                });
    }
}
