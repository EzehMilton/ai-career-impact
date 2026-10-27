package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.report.professional.detailed.DetailedReportFixtures;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The paid-narrative v1 contract on the captured Solicitor chain. Hand-written drafts, no provider call. */
class ProfessionalPaidNarrativeValidatorTest {

    private final ProfessionalPaidNarrativeValidator validator = new ProfessionalPaidNarrativeValidator();
    private final ProfessionalPaidNarrativeContext context = DetailedReportFixtures.chain("solicitor-8").context();
    private final ProfessionalPaidNarrative valid = DetailedReportFixtures.validNarrative(context);

    @Test
    void aValidNarrativeIsAcceptedUnchanged() {
        assertThat(validator.validate(valid, context)).isSameAs(valid);
        // Every list is keyed to the deterministic order.
        assertThat(context.opportunityIds()).hasSize(3).first().asString().startsWith("opportunity-1-");
        assertThat(context.planActionIds()).isNotEmpty();
    }

    @Test
    void aMissingNarrativeOrAnEmptyFieldIsRejected() {
        rejects(null, PRESENT);
        rejects(with(valid, "", valid.scoresTogether()), WELL_FORMED);
        rejects(new ProfessionalPaidNarrative(valid.executiveSummary(), valid.scoresTogether(),
                valid.responsibilityPattern(), null, valid.humanValueExplanations(), valid.skillDevelopment(),
                valid.skillExplanations(), valid.planIntroduction(), valid.planActionNotes(),
                valid.closingPerspective()), WELL_FORMED);
    }

    @Test
    void missingExtraReorderedOrChangedOpportunityIdsAreRejected() {
        var opportunities = valid.opportunityExplanations();
        rejects(opportunities(opportunities.subList(0, 2)), IDS);
        var extra = new ArrayList<>(opportunities);
        extra.add(new ItemExplanation("opportunity-4-r8", "Another place AI may assist."));
        rejects(opportunities(extra), IDS);
        var reordered = new ArrayList<>(opportunities);
        Collections.swap(reordered, 0, 1);
        rejects(opportunities(reordered), IDS);
        var changed = new ArrayList<>(opportunities);
        changed.set(0, new ItemExplanation(opportunities.getFirst().id().replace("-r2", "-r5"), "Changed."));
        rejects(opportunities(changed), IDS);
        var duplicated = new ArrayList<>(opportunities);
        duplicated.set(2, opportunities.getFirst());
        rejects(opportunities(duplicated), IDS);
    }

    @Test
    void changedSkillsHumanValueAreasOrActionsAreRejected() {
        var skills = new ArrayList<>(valid.skillExplanations());
        skills.set(0, new ItemExplanation("skill-1-hands-on-practice-and-safety", "A different skill."));
        rejects(new ProfessionalPaidNarrative(valid.executiveSummary(), valid.scoresTogether(),
                valid.responsibilityPattern(), valid.opportunityExplanations(), valid.humanValueExplanations(),
                valid.skillDevelopment(), skills, valid.planIntroduction(), valid.planActionNotes(),
                valid.closingPerspective()), IDS);
        var actions = new ArrayList<>(valid.planActionNotes());
        actions.set(0, new ItemExplanation("action-1-p1-invented-step", "An invented step."));
        rejects(new ProfessionalPaidNarrative(valid.executiveSummary(), valid.scoresTogether(),
                valid.responsibilityPattern(), valid.opportunityExplanations(), valid.humanValueExplanations(),
                valid.skillDevelopment(), valid.skillExplanations(), valid.planIntroduction(), actions,
                valid.closingPerspective()), IDS);
        rejects(new ProfessionalPaidNarrative(valid.executiveSummary(), valid.scoresTogether(),
                valid.responsibilityPattern(), valid.opportunityExplanations(), valid.humanValueExplanations().subList(0, 1),
                valid.skillDevelopment(), valid.skillExplanations(), valid.planIntroduction(), valid.planActionNotes(),
                valid.closingPerspective()), IDS);
    }

    @Test
    void aResponsibilityGivenAnotherCategoryIsRejected() {
        var opportunities = new ArrayList<>(valid.opportunityExplanations());
        opportunities.set(0, new ItemExplanation(opportunities.getFirst().id(),
                "This is really mainly hands-on or in person work, so leave it as it is."));
        rejects(opportunities(opportunities), RECLASSIFICATION);
        // Ordinary advice wording is not a category claim.
        opportunities.set(0, new ItemExplanation(opportunities.getFirst().id(),
                "AI may assist with parts of the research, while you check every source."));
        assertThat(validator.validate(opportunities(opportunities), context)).isNotNull();
    }

    @Test
    void inventedRecommendationsAndSkillsAreRejected() {
        rejects(summary("Your next step is to take a short course in AI before you try anything."), NEW_RECOMMENDATIONS);
        rejects(summary("You could get a certificate to show your AI skills."), NEW_RECOMMENDATIONS);
        rejects(summary("You may want to change careers in the long run."), NEW_RECOMMENDATIONS);
        // A skill family the guidance did not select must not be introduced.
        String unselected = context.unselectedSkillLabels().getFirst();
        rejects(summary("You should also build " + unselected.toLowerCase() + "."), NEW_RECOMMENDATIONS);
    }

    @Test
    void jobLossCapabilityFutureAndRankingClaimsAreRejected() {
        rejects(summary("Your job is at risk because of how much research you do."), JOB_LOSS);
        rejects(summary("Your work carries a real risk of being replaced."), JOB_LOSS);
        rejects(summary("AI cannot replace the trust your clients place in you."), CAPABILITY);
        rejects(summary("AI will replace some of the drafting you do."), CAPABILITY);
        rejects(summary("Your research can be automated by AI tools."), TASK_AUTOMATION);
        rejects(summary("Your role will change significantly in the coming years."), FUTURE);
        rejects(summary("Your negotiation work is safe from AI."), FUTURE);
        rejects(summary("Drafting is more important than research for you."), RANKING);
        rejects(summary("Solicitors are usually cautious about new technology."), OCCUPATION);
    }

    @Test
    void unsafeDelegationIsRejectedButASafeguardWordedAsANegationIsNot() {
        rejects(summary("You can let AI decide which contract terms to accept."), DELEGATION);
        rejects(summary("Use AI drafts without review when you are busy."), DELEGATION);
        rejects(summary("Hand the first review over to AI."), DELEGATION);
        assertThat(validator.validate(summary("Never use an AI draft without review, and keep every decision yours."),
                context)).isNotNull();
        rejects(summary("When you are busy, skip the usual checks on AI drafts."), DELEGATION);
        rejects(summary("You could set aside the firm's confidentiality policy for routine drafts."), PROCEDURE);
    }

    @Test
    void vendorsNumbersInternalsSpellingPersonAndToneAreChecked() {
        rejects(summary("ChatGPT may help you research precedent."), VENDORS);
        rejects(summary("Your AI Exposure score of 6.2 is moderate."), NUMERIC);
        rejects(summary("About 40% of your work involves research."), NUMERIC);
        rejects(summary("The rule R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH applies to your research."), INTERNALS);
        rejects(summary("The probability distribution suggests that research suits AI assistance."), INTERNALS);
        rejects(summary("Your HIGH Human Advantage reflects your client work."), BAND_WORDS);
        rejects(summary("You prioritize client judgment in your work."), SPELLING);
        rejects(with(valid, "The user relies on judgement and client relationships throughout the work described.",
                valid.scoresTogether()), SECOND_PERSON);
        rejects(summary("This is an amazing opportunity for you!"), TONE);
    }

    @Test
    void quotedResponsibilityTextsAreTheCustomersOwnWordsAndNeverTripALanguageRule() {
        // "Manage confidential client information" and "Ensure work complies with professional obligations" are
        // the customer's texts; quoting them is not advice about confidentiality or obligations.
        var narrative = summary("Your responsibilities include “Ensure work complies with professional obligations” "
                + "and “Manage confidential client information”, which rely on your own judgement.");
        assertThat(validator.validate(narrative, context)).isNotNull();
    }

    @Test
    void everyRuleExceptAMissingNarrativeCanBeRepairedOnce() {
        assertThat(REPAIRABLE).doesNotContain(PRESENT).contains(WELL_FORMED, IDS, NUMERIC, DELEGATION, TONE);
    }

    private void rejects(ProfessionalPaidNarrative narrative, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, context)).isInstanceOfSatisfying(
                NarrativeRuleViolation.class, violation -> assertThat(violation.rule()).isEqualTo(rule));
    }

    private ProfessionalPaidNarrative summary(String extraSentence) {
        return with(valid, valid.executiveSummary() + " " + extraSentence, valid.scoresTogether());
    }

    private static ProfessionalPaidNarrative with(ProfessionalPaidNarrative n, String executiveSummary,
            String scoresTogether) {
        return new ProfessionalPaidNarrative(executiveSummary, scoresTogether, n.responsibilityPattern(),
                n.opportunityExplanations(), n.humanValueExplanations(), n.skillDevelopment(), n.skillExplanations(),
                n.planIntroduction(), n.planActionNotes(), n.closingPerspective());
    }

    private ProfessionalPaidNarrative opportunities(List<ItemExplanation> opportunities) {
        return new ProfessionalPaidNarrative(valid.executiveSummary(), valid.scoresTogether(),
                valid.responsibilityPattern(), opportunities, valid.humanValueExplanations(), valid.skillDevelopment(),
                valid.skillExplanations(), valid.planIntroduction(), valid.planActionNotes(), valid.closingPerspective());
    }
}
