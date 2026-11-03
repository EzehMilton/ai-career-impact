package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.AdaptationStrategy;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;
import com.chikere.futureai.report.professional.detailed.DetailedReportFixtures;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The paid-narrative v2 contract on the captured Solicitor chain. Hand-written drafts, no provider call. */
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
        rejects(Narratives.summary(valid, ""), WELL_FORMED);
        rejects(Narratives.opportunities(valid, null), WELL_FORMED);
        rejects(Narratives.strategy(valid, null), WELL_FORMED);
        rejects(Narratives.experiment(valid, 0, " "), WELL_FORMED);
        rejects(Narratives.practicalUses(valid, 0, null), WELL_FORMED);
    }

    @Test
    void missingExtraReorderedOrChangedOpportunityIdsAreRejected() {
        var opportunities = valid.opportunityInsights();
        rejects(opportunities(opportunities.subList(0, 2)), IDS);
        var extra = new ArrayList<>(opportunities);
        extra.add(new OpportunityInsight("opportunity-4-r8", "Another place AI may assist.", List.of("A use", "Another"),
                List.of("Yours"), "An experiment."));
        rejects(opportunities(extra), IDS);
        var reordered = new ArrayList<>(opportunities);
        Collections.swap(reordered, 0, 1);
        rejects(opportunities(reordered), IDS);
        var changed = new ArrayList<>(opportunities);
        var first = opportunities.getFirst();
        changed.set(0, new OpportunityInsight(first.id().replace("-r2", "-r5"), first.explanation(),
                first.practicalAiUses(), first.humanOwnership(), first.firstExperiment()));
        rejects(opportunities(changed), IDS);
        var duplicated = new ArrayList<>(opportunities);
        duplicated.set(2, opportunities.getFirst());
        rejects(opportunities(duplicated), IDS);
    }

    @Test
    void changedSkillsHumanValueAreasOrActionsAreRejected() {
        var skills = new ArrayList<>(valid.skillInsights());
        skills.set(0, new SkillInsight("skill-1-hands-on-practice-and-safety", "A different skill.",
                skills.getFirst().inYourWorkThisMeans()));
        rejects(Narratives.skills(valid, skills), IDS);
        var actions = new ArrayList<>(valid.planActionNotes());
        actions.set(0, new ItemExplanation("action-1-p1-invented-step", "An invented step."));
        rejects(Narratives.actions(valid, actions), IDS);
        rejects(Narratives.humanValue(valid, valid.humanValueInsights().subList(0, 1)), IDS);
    }

    @Test
    void aResponsibilityGivenAnotherCategoryIsRejected() {
        rejects(explanation(0, "This is really mainly hands-on or in person work, so leave it as it is."),
                RECLASSIFICATION);
        // Ordinary advice wording is not a category claim.
        assertThat(validator.validate(explanation(0, "AI may assist with parts of the research, while you check every "
                + "source."), context)).isNotNull();
        // A practical example may not reclassify either.
        rejects(Narratives.practicalUses(valid, 1, List.of("Treating it as a good opportunity for AI assistance",
                "Summarising the bundle for your review")), RECLASSIFICATION);
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
        rejects(summary("Your client work calls for a HIGH degree of care."), BAND_WORDS);
        rejects(summary("You prioritize client judgment in your work."), SPELLING);
        rejects(Narratives.summary(valid, "The user relies on judgement and client relationships throughout the work "
                + "described."), SECOND_PERSON);
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

    // ---- professional-paid-narrative-v2: bounded practical interpretation ----

    @Test
    void practicalListsAreHeldToTheirSizesAndLengths() {
        rejects(Narratives.practicalUses(valid, 0, List.of("Summarising the bundle of documents for your review")),
                LIST_SIZES);
        rejects(Narratives.practicalUses(valid, 0, List.of("Drafting a first chronology", "Summarising the bundle",
                "Listing missing documents", "Comparing versions of a clause", "Formatting the index")), LIST_SIZES);
        rejects(Narratives.practicalUses(valid, 0, List.of("Draft", "Summarising the bundle for your review")), LIST_SIZES);
        rejects(Narratives.practicalUses(valid, 0, List.of("Summarising the bundle for your review",
                "x".repeat(MAX_ITEM + 1))), LIST_SIZES);
        var strategy = valid.adaptationStrategy();
        rejects(Narratives.strategy(valid, new AdaptationStrategy(List.of(), strategy.learn(), strategy.experiment(),
                strategy.strengthen(), strategy.monitor())), LIST_SIZES);
        rejects(Narratives.strategy(valid, new AdaptationStrategy(strategy.protect(), strategy.learn(),
                strategy.experiment(), strategy.strengthen(), List.of("Watching one", "Watching two", "Watching three",
                "Watching four"))), LIST_SIZES);
        var skills = new ArrayList<>(valid.skillInsights());
        skills.set(0, new SkillInsight(skills.getFirst().id(), skills.getFirst().explanation(),
                List.of("Checking your own drafts against sources")));
        rejects(Narratives.skills(valid, skills), LIST_SIZES);
    }

    @Test
    void identicalOrGenericPracticalContentAcrossResponsibilitiesIsRejected() {
        var opportunities = new ArrayList<>(valid.opportunityInsights());
        var first = opportunities.getFirst();
        for (int i = 1; i < opportunities.size(); i++) {
            var item = opportunities.get(i);
            opportunities.set(i, new OpportunityInsight(item.id(), item.explanation(), first.practicalAiUses(),
                    item.humanOwnership(), item.firstExperiment()));
        }
        rejects(opportunities(opportunities), GENERIC);
        rejects(Narratives.practicalUses(valid, 0, List.of("Use AI to support preparation, drafting, checking or "
                + "information handling, with you reviewing the result.", "Summarising the bundle for your review")), GENERIC);
        rejects(Narratives.experiment(valid, 1, valid.opportunityInsights().getFirst().firstExperiment()), GENERIC);
        var human = new ArrayList<>(valid.humanValueInsights());
        human.set(1, new HumanValueInsight(human.get(1).id(), human.get(1).explanation(), human.getFirst().strengthen()));
        rejects(Narratives.humanValue(valid, human), GENERIC);
    }

    @Test
    void aMeasureDescribedWithAnotherBandIsRejected() {
        var band = context.bandByMeasure().get("AI Exposure").toLowerCase();
        String other = band.equals("high") ? "low" : "high";
        rejects(summary("Your " + other + " AI Exposure reflects how much drafting you do."), BAND_CONTRADICTION);
        assertThat(validator.validate(summary("Your " + band + " AI Exposure reflects how much drafting you do."),
                context)).isNotNull();
    }

    @Test
    void anExampleMustKeepApprovalSignOffDecisionsAndEscalationWithYou() {
        rejects(Narratives.practicalUses(valid, 0, List.of("Approving routine contract amendments",
                "Summarising the bundle for your review")), HIGH_STAKES);
        rejects(Narratives.practicalUses(valid, 0, List.of("Deciding which matters to escalate to a partner",
                "Summarising the bundle for your review")), HIGH_STAKES);
        rejects(Narratives.experiment(valid, 0, "Let the draft be signed off once it reads well, for two weeks."),
                HIGH_STAKES);
        // The same activity with the authority kept by you is the intended kind of example.
        assertThat(validator.validate(Narratives.practicalUses(valid, 0, List.of(
                "Preparing a short note on each matter so you can decide what to escalate",
                "Drafting amendment summaries ready for your sign-off")), context)).isNotNull();
        rejects(summary("AI could approve routine amendments for you."), DELEGATION);
    }

    @Test
    void safetyCriticalExamplesNeverDescribeDiagnosisTreatmentDosesOrDirectAssessment() {
        var nurse = DetailedReportFixtures.chain("registered-nurse-8").context();
        assertThat(nurse.safetyCriticalOpportunityIds()).isNotEmpty();
        assertThat(nurse.prompt().opportunities()).allSatisfy(opportunity -> assertThat(
                opportunity.practicalExampleLimits()).contains(ProfessionalPaidNarrativeContext.HIGH_STAKES_LIMIT,
                ProfessionalPaidNarrativeContext.SAFETY_CRITICAL_LIMIT));
        var draft = DetailedReportFixtures.validNarrative(nurse);
        for (String unsafe : List.of("Suggesting a likely diagnosis from recorded observations",
                "Calculating medication doses from the chart", "Triaging which patients to see first",
                "Recommending treatment options for the care plan", "Assessing the patient from recorded vital signs")) {
            rejects(Narratives.practicalUses(draft, 0, List.of(unsafe, "Checking records for missing fields")), nurse,
                    HIGH_STAKES);
        }
        // Even "for your review" does not make a diagnosis or a dose acceptable in a safety-critical example.
        rejects(Narratives.practicalUses(draft, 0, List.of("Suggesting a dose for your review",
                "Checking records for missing fields")), nurse, HIGH_STAKES);
        // Human ownership may name those responsibilities: that is where they belong.
        var items = new ArrayList<>(draft.opportunityInsights());
        var first = items.getFirst();
        items.set(0, new OpportunityInsight(first.id(), first.explanation(), List.of(
                "Structuring your record from approved source information for your review",
                "Checking a draft record for missing or inconsistent fields"),
                List.of("Interpreting observations and recognising deterioration",
                        "Deciding whether escalation is needed"), first.firstExperiment()));
        assertThat(validator.validate(Narratives.opportunities(draft, items), nurse)).isNotNull();
        // A software opportunity with no safety signals may still "diagnose" a failing build.
        var java = DetailedReportFixtures.chain("java-software-developer-8").context();
        assertThat(java.safetyCriticalOpportunityIds()).isEmpty();
        var javaDraft = DetailedReportFixtures.validNarrative(java);
        assertThat(validator.validate(Narratives.practicalUses(javaDraft, 0, List.of(
                "Suggesting where to start diagnosing a failing build for you to confirm",
                "Drafting unit-test outlines from the method signature")), java)).isNotNull();
    }

    @Test
    void confidentialDataRegulationEmployerSystemsAndProductsAreNotInvented() {
        rejects(summary("You could paste client case notes into a chat assistant to summarise them."), CONFIDENTIAL);
        assertThat(validator.validate(summary("Never paste client case notes into a tool that is not approved for "
                + "them."), context)).isNotNull();
        assertThat(validator.validate(summary("You could load client case notes into an approved tool to summarise "
                + "them."), context)).isNotNull();
        rejects(summary("The regulator requires you to keep a record of every AI-assisted draft."), REGULATION);
        rejects(summary("Under GDPR you must keep these notes separate."), REGULATION);
        rejects(summary("Your firm's case management system could summarise each file."), SYSTEMS);
        rejects(summary("The CRM could flag overdue replies for you."), SYSTEMS);
        rejects(summary("You could build variance tables in Excel."), VENDORS);
        // Ordinary words are not product names, and approved tools are not an invented system.
        assertThat(validator.validate(summary("Your care teams and your organisation's approved tools stay central."),
                context)).isNotNull();
    }

    @Test
    void aTermThePersonUsedInTheirOwnWorkDescriptionIsNotInvented() {
        var base = DetailedReportFixtures.chain("management-accountant-8");
        var role = base.role();
        var analysis = base.analysis();
        var input = new com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisInput(
                analysis.input().profession(), "I report under IFRS and build the monthly pack in Excel.",
                analysis.input().responsibilities(), analysis.input().skills());
        var context = ProfessionalPaidNarrativeContext.from(role.report(), copy(analysis, input), base.guidance());
        var draft = DetailedReportFixtures.validNarrative(context);
        assertThat(validator.validate(Narratives.summary(draft, draft.executiveSummary()
                + " Your IFRS reporting and your Excel pack both rely on your review."), context)).isNotNull();
    }

    private static com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot copy(
            com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot a,
            com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisInput input) {
        return new com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot(
                a.snapshotId(), a.assessmentId(), a.status(), a.schemaVersion(), a.policyVersion(), a.judgmentSource(),
                a.model(), a.providerRequestId(), a.inputTokens(), a.outputTokens(), input, a.regulatedContext(),
                a.regulatedContextLevel(), a.policyParameters(), a.items(), a.createdAt(), a.completedAt());
    }

    @Test
    void ordinaryPracticalWordingIsNotMistakenForInternalsSalariesOrRemovingReview() {
        for (String sentence : List.of("You judge which variances pass the materiality threshold.",
                "Distribution costs and company earnings are part of the pack you prepare.",
                "Judge the trial on how much review effort it needed and whether quality improved.",
                "You could note whether it helped reduce review time without lowering accuracy.",
                "Share patient information with colleagues only as your procedures require.")) {
            assertThat(validator.validate(summary(sentence), context)).as(sentence).isNotNull();
        }
        rejects(summary("Reduce the human review of drafts once they look reliable."), DELEGATION);
        rejects(summary("Your salary is unlikely to be affected."), JOB_LOSS);
        rejects(summary("The score distribution suggests your drafting suits AI."), INTERNALS);
    }

    // ---- Stage 4F.1 live-run false positives (gpt-6-luna) ----

    /** Rejected live as NO_NEW_RECOMMENDATIONS: the noun "Installation" matched the software-install pattern. */
    static final String ELECTRICIAN_SYNTHESIS = "Installation, inspection, diagnosis and repair are mainly hands-on "
            + "responsibilities, while customer communication depends on your own interaction and judgement.";

    /** Rejected live as SECOND_PERSON: "the customer-facing" matched the third-person "the customer". */
    static final String CUSTOMER_SERVICE_SKILLS = "Together, these skills support both the customer-facing and "
            + "information-focused parts of your work: maintaining trust through communication, using AI responsibly "
            + "within your obligations, checking assisted work, and fitting assistance into your workflow while you "
            + "review the result.";

    @Test
    void theElectricianSynthesisOfSuppliedClassificationsIsAccepted() {
        var electrician = DetailedReportFixtures.chain("electrician-8").context();
        var draft = DetailedReportFixtures.validNarrative(electrician);
        var narrative = Narratives.pattern(draft, ELECTRICIAN_SYNTHESIS);
        assertThat(validator.validate(narrative, electrician)).isSameAs(narrative);
        assertThat(validator.validate(summary("Your installation and repair work depends on being on site."), context))
                .isNotNull();
        assertThat(validator.validate(summary("You keep installing and testing circuits yourself."), context)).isNotNull();
    }

    @Test
    void installingSoftwareOrBuyingToolsIsStillAnInventedRecommendation() {
        rejects(summary("You could install an AI assistant to draft your letters."), NEW_RECOMMENDATIONS);
        rejects(summary("Install a new drafting tool before you begin."), NEW_RECOMMENDATIONS);
        rejects(summary("You might install some note-taking software for meetings."), NEW_RECOMMENDATIONS);
        rejects(summary("You could download a template pack for your drafting."), NEW_RECOMMENDATIONS);
        rejects(summary("Consider whether to buy a subscription for research."), NEW_RECOMMENDATIONS);
        rejects(summary("You should sign up for a webinar on AI."), NEW_RECOMMENDATIONS);
    }

    @Test
    void theCustomerServiceSkillsSentenceIsSecondPerson() {
        var adviser = DetailedReportFixtures.chain("customer-service-adviser-8").context();
        var draft = DetailedReportFixtures.validNarrative(adviser);
        var narrative = Narratives.pattern(draft, CUSTOMER_SERVICE_SKILLS);
        assertThat(validator.validate(narrative, adviser)).isSameAs(narrative);
        // Sentences need not begin with "you" when they plainly address you.
        assertThat(validator.validate(summary("These skills support your client work. This pattern reflects how you "
                + "work. The findings describe your work as you described it."), context)).isNotNull();
    }

    @Test
    void genuinelyThirdPersonPhrasingIsStillRejected() {
        var adviser = DetailedReportFixtures.chain("customer-service-adviser-8").context();
        var draft = DetailedReportFixtures.validNarrative(adviser);
        for (String sentence : List.of("The worker should check every reply before it is sent.",
                "A customer-service adviser should check every reply before it is sent.",
                "Your skills matter, and the adviser must keep reviewing replies.",
                "The adviser must keep reviewing replies.",
                "The user relies on communication and trust in this work.",
                "The employee reviews every reply.",
                "This person reviews every reply.",
                "This individual reviews every reply.",
                "This professional reviews every reply.",
                "He or she reviews every reply.")) {
            var narrative = Narratives.summary(draft, draft.executiveSummary() + " " + sentence);
            assertThatThrownBy(() -> validator.validate(narrative, adviser)).as(sentence).isInstanceOfSatisfying(
                    NarrativeRuleViolation.class, violation -> assertThat(violation.rule()).isEqualTo(SECOND_PERSON));
        }
        rejects(summary("A solicitor should review every draft."), SECOND_PERSON);
        // Also third person, but an occupation-wide instruction: the earlier occupation rule rejects it first.
        rejects(summary("Solicitors must review every draft."), OCCUPATION);
    }

    /** Rejected live as SECOND_PERSON: the customer here is the person served, not the person assessed. */
    static final String ELECTRICIAN_CUSTOMER = "Explaining faults and repair options depends on trust, communication "
            + "and handling the relationship with the customer.";
    static final String ADVISER_CUSTOMER = "Your understanding of the customer’s situation helps shape a relevant "
            + "response.";

    @Test
    void theCustomersAndClientsYouServeAreNotThirdPersonWording() {
        var electrician = DetailedReportFixtures.chain("electrician-8").context();
        var draft = DetailedReportFixtures.validNarrative(electrician);
        var narrative = Narratives.pattern(draft, ELECTRICIAN_CUSTOMER);
        assertThat(validator.validate(narrative, electrician)).isSameAs(narrative);

        var adviser = DetailedReportFixtures.chain("customer-service-adviser-8").context();
        var adviserDraft = DetailedReportFixtures.validNarrative(adviser);
        var adviserNarrative = Narratives.summary(adviserDraft, adviserDraft.executiveSummary() + " " + ADVISER_CUSTOMER);
        assertThat(validator.validate(adviserNarrative, adviser)).isSameAs(adviserNarrative);

        for (String sentence : List.of("Your understanding of the customer's situation helps shape a relevant response.",
                "Explaining faults depends on handling the relationship with the customer.",
                "You explain the result clearly to the customer.",
                "Your discussion with the client remains your responsibility.",
                "You should review the client's instructions before proceeding.")) {
            assertThat(validator.validate(summary(sentence), context)).as(sentence).isNotNull();
        }
    }

    @Test
    void theyReferringToThingsOrOtherPeopleIsNotThirdPersonWording() {
        for (String sentence : List.of("AI drafts can help, but they should still be checked by you.",
                "Where replies are assisted, they need to be checked before use.",
                "You decide, and they must follow your judgement.",
                "They should be reviewed before you send them.")) {
            assertThat(validator.validate(summary(sentence), context)).as(sentence).isNotNull();
        }
    }

    @Test
    void everyRuleExceptAMissingNarrativeCanBeRepairedOnce() {
        assertThat(REPAIRABLE).doesNotContain(PRESENT).contains(WELL_FORMED, IDS, LIST_SIZES, NUMERIC, DELEGATION,
                HIGH_STAKES, GENERIC, TONE);
    }

    private void rejects(ProfessionalPaidNarrative narrative, String rule) {
        rejects(narrative, context, rule);
    }

    private void rejects(ProfessionalPaidNarrative narrative, ProfessionalPaidNarrativeContext on, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, on)).isInstanceOfSatisfying(
                NarrativeRuleViolation.class, violation -> assertThat(violation.rule()).isEqualTo(rule));
    }

    private ProfessionalPaidNarrative summary(String extraSentence) {
        return Narratives.summary(valid, valid.executiveSummary() + " " + extraSentence);
    }

    private ProfessionalPaidNarrative opportunities(List<OpportunityInsight> opportunities) {
        return Narratives.opportunities(valid, opportunities);
    }

    private ProfessionalPaidNarrative explanation(int index, String explanation) {
        var items = new ArrayList<>(valid.opportunityInsights());
        var item = items.get(index);
        items.set(index, new OpportunityInsight(item.id(), explanation, item.practicalAiUses(), item.humanOwnership(),
                item.firstExperiment()));
        return opportunities(items);
    }
}
