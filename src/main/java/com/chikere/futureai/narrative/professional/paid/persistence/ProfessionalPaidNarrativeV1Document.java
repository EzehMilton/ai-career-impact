package com.chikere.futureai.narrative.professional.paid.persistence;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.HumanValueInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.ItemExplanation;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.OpportunityInsight;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative.SkillInsight;

import java.util.List;

/**
 * The stored JSON shape of a professional-paid-narrative-v1 row, read only. It is mapped onto the current record with
 * its explanations kept and every v2-only field empty, so a stored v1 narrative is never rewritten or regenerated.
 * The stored row keeps its v1 version, which any report assembled from it records as provenance.
 */
record ProfessionalPaidNarrativeV1Document(String executiveSummary, String scoresTogether,
        String responsibilityPattern, List<ItemExplanation> opportunityExplanations,
        List<ItemExplanation> humanValueExplanations, String skillDevelopment, List<ItemExplanation> skillExplanations,
        String planIntroduction, List<ItemExplanation> planActionNotes, String closingPerspective) {

    ProfessionalPaidNarrative toCurrent() {
        return new ProfessionalPaidNarrative(executiveSummary, scoresTogether, responsibilityPattern,
                opportunityExplanations.stream().map(item -> new OpportunityInsight(item.id(), item.explanation(),
                        List.of(), List.of(), null)).toList(),
                humanValueExplanations.stream().map(item -> new HumanValueInsight(item.id(), item.explanation(), null))
                        .toList(),
                skillExplanations.stream().map(item -> new SkillInsight(item.id(), item.explanation(), List.of())).toList(),
                planIntroduction, planActionNotes, null, closingPerspective);
    }
}
