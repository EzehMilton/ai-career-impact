package com.chikere.futureai.narrative.professional.paid;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.AdaptationStrategy;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;

import java.util.List;

/** Copies of a test narrative with one field replaced, so a test changes exactly what it is about. */
public final class Narratives {

    private Narratives() {
    }

    public static ProfessionalPaidNarrative summary(ProfessionalPaidNarrative n, String executiveSummary) {
        return new ProfessionalPaidNarrative(executiveSummary, n.scoresTogether(), n.responsibilityPattern(),
                n.opportunityInsights(), n.humanValueInsights(), n.skillInsights(), n.planIntroduction(),
                n.planActionNotes(), n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative pattern(ProfessionalPaidNarrative n, String responsibilityPattern) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), responsibilityPattern,
                n.opportunityInsights(), n.humanValueInsights(), n.skillInsights(), n.planIntroduction(),
                n.planActionNotes(), n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative scoresTogether(ProfessionalPaidNarrative n, String scoresTogether) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), scoresTogether, n.responsibilityPattern(),
                n.opportunityInsights(), n.humanValueInsights(), n.skillInsights(), n.planIntroduction(),
                n.planActionNotes(), n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative opportunities(ProfessionalPaidNarrative n, List<OpportunityInsight> items) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), n.responsibilityPattern(), items,
                n.humanValueInsights(), n.skillInsights(), n.planIntroduction(), n.planActionNotes(),
                n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative humanValue(ProfessionalPaidNarrative n, List<HumanValueInsight> items) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), n.responsibilityPattern(),
                n.opportunityInsights(), items, n.skillInsights(), n.planIntroduction(), n.planActionNotes(),
                n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative skills(ProfessionalPaidNarrative n, List<SkillInsight> items) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), n.responsibilityPattern(),
                n.opportunityInsights(), n.humanValueInsights(), items, n.planIntroduction(), n.planActionNotes(),
                n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative actions(ProfessionalPaidNarrative n, List<ItemExplanation> items) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), n.responsibilityPattern(),
                n.opportunityInsights(), n.humanValueInsights(), n.skillInsights(), n.planIntroduction(), items,
                n.adaptationStrategy(), n.closingPerspective());
    }

    public static ProfessionalPaidNarrative strategy(ProfessionalPaidNarrative n, AdaptationStrategy strategy) {
        return new ProfessionalPaidNarrative(n.executiveSummary(), n.scoresTogether(), n.responsibilityPattern(),
                n.opportunityInsights(), n.humanValueInsights(), n.skillInsights(), n.planIntroduction(),
                n.planActionNotes(), strategy, n.closingPerspective());
    }

    /** The opportunity at {@code index} with its practical uses replaced. */
    public static ProfessionalPaidNarrative practicalUses(ProfessionalPaidNarrative n, int index, List<String> uses) {
        var items = new java.util.ArrayList<>(n.opportunityInsights());
        var item = items.get(index);
        items.set(index, new OpportunityInsight(item.id(), item.explanation(), uses, item.humanOwnership(),
                item.firstExperiment()));
        return opportunities(n, items);
    }

    /** The opportunity at {@code index} with its first experiment replaced. */
    public static ProfessionalPaidNarrative experiment(ProfessionalPaidNarrative n, int index, String experiment) {
        var items = new java.util.ArrayList<>(n.opportunityInsights());
        var item = items.get(index);
        items.set(index, new OpportunityInsight(item.id(), item.explanation(), item.practicalAiUses(),
                item.humanOwnership(), experiment));
        return opportunities(n, items);
    }
}
