package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;

import java.util.HashSet;
import java.util.List;

/**
 * The v1 selection rule: the assessment's own responsibilities, blanks removed, original order and text kept.
 * One to eight are analysed. More than eight is refused explicitly, never truncated, and so are duplicates and
 * over-long entries, so no hidden rule decides which responsibilities a paid report covers.
 *
 * <p>The Professional assessment itself may hold more than eight responsibilities, and that limit is not reduced.
 * When a paid report needs analysis of such an assessment, the future paid-report flow will ask the customer to
 * choose up to eight explicitly before generation. Until that flow exists, more than eight is an explicit failure.</p>
 */
public final class ResponsibilitySelection {

    private ResponsibilitySelection() {
    }

    /**
     * The customer's explicit choice for an assessment with more responsibilities than analysis supports: 1-based
     * positions in the assessment's usable responsibilities. The chosen ones are kept in their original order and
     * text; positions must be distinct and in range, and between one and eight may be chosen.
     */
    public static List<String> selectExplicitly(List<String> stored, List<Integer> chosenPositions) {
        List<String> usable = usable(stored);
        if (chosenPositions == null || chosenPositions.isEmpty()) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.INVALID_SELECTION,
                    "choose at least one responsibility to analyse");
        }
        if (chosenPositions.size() > ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.INVALID_SELECTION,
                    "choose at most " + ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES + " responsibilities");
        }
        var distinct = new java.util.TreeSet<>(chosenPositions);
        if (distinct.size() != chosenPositions.size() || distinct.first() < 1 || distinct.last() > usable.size()) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.INVALID_SELECTION,
                    "the selection must name distinct responsibilities of this assessment");
        }
        return select(distinct.stream().map(position -> usable.get(position - 1)).toList());
    }

    public static List<String> select(List<String> stored) {
        List<String> usable = usable(stored);
        if (usable.isEmpty()) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.NO_RESPONSIBILITIES,
                    "the assessment has no responsibilities to analyse");
        }
        int maximum = ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES;
        if (usable.size() > maximum) {
            throw new ResponsibilityAnalysisUnavailableException(
                    ResponsibilityAnalysisUnavailableException.Reason.RESPONSIBILITY_SELECTION_REQUIRED,
                    "responsibility analysis v1 supports at most " + maximum + " responsibilities; the assessment has "
                            + usable.size() + " and none are dropped automatically, so the customer must choose up to "
                            + maximum);
        }
        if (new HashSet<>(usable).size() != usable.size()) {
            throw new ResponsibilityAnalysisUnavailableException(
                    ResponsibilityAnalysisUnavailableException.Reason.DUPLICATE_RESPONSIBILITIES,
                    "the assessment lists the same responsibility more than once");
        }
        if (usable.stream().anyMatch(value -> value.length() > ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITY_LENGTH)) {
            throw new ResponsibilityAnalysisUnavailableException(
                    ResponsibilityAnalysisUnavailableException.Reason.INVALID_RESPONSIBILITY,
                    "a responsibility is longer than the supported "
                            + ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITY_LENGTH + " characters");
        }
        return usable;
    }

    private static List<String> usable(List<String> stored) {
        return stored == null ? List.of()
                : stored.stream().filter(value -> value != null && !value.isBlank()).map(String::strip).toList();
    }
}
