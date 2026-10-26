package com.chikere.futureai.responsibility.professional.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The responsibilities a caller has already selected for analysis, with the role context they belong to.
 *
 * <p>Selection is the caller's job: this request accepts 1 to {@link #MAXIMUM_RESPONSIBILITIES} responsibilities
 * and refuses more rather than quietly keeping the first few, so no "first eight win" rule can hide in a provider.
 * Each responsibility gets a stable key, {@code r1} to {@code r8}, in the order supplied.</p>
 */
public record ProfessionalResponsibilityAnalysisRequest(
        UUID assessmentId,
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills) {

    public static final int MAXIMUM_RESPONSIBILITIES = 8;
    /** The same cap the Professional form applies to one responsibility. */
    public static final int MAXIMUM_RESPONSIBILITY_LENGTH = 300;

    public ProfessionalResponsibilityAnalysisRequest {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        profession = required(profession, "profession");
        workDescription = required(workDescription, "workDescription");
        Objects.requireNonNull(responsibilities, "responsibilities must not be null");
        if (responsibilities.isEmpty()) {
            throw new IllegalArgumentException("at least one responsibility must be selected for analysis");
        }
        if (responsibilities.size() > MAXIMUM_RESPONSIBILITIES) {
            throw new IllegalArgumentException("at most " + MAXIMUM_RESPONSIBILITIES
                    + " responsibilities can be analysed in one request; " + responsibilities.size()
                    + " were supplied, and choosing which to analyse is the caller's decision");
        }
        responsibilities = responsibilities.stream().map(value -> {
            String text = required(value, "responsibility");
            if (text.length() > MAXIMUM_RESPONSIBILITY_LENGTH) {
                throw new IllegalArgumentException("a responsibility must be at most "
                        + MAXIMUM_RESPONSIBILITY_LENGTH + " characters");
            }
            return text;
        }).toList();
        if (responsibilities.stream().distinct().count() != responsibilities.size()) {
            throw new IllegalArgumentException("each selected responsibility must be distinct");
        }
        skills = skills == null ? List.of() : skills.stream().map(value -> required(value, "skill")).toList();
    }

    /** The stable key of the responsibility at a 1-based index, for example {@code r3}. */
    public static String key(int index) {
        if (index < 1 || index > MAXIMUM_RESPONSIBILITIES) {
            throw new IllegalArgumentException("responsibility index must be between 1 and " + MAXIMUM_RESPONSIBILITIES);
        }
        return "r" + index;
    }

    /** Responsibilities by stable key, in the order supplied. */
    public Map<String, String> keyedResponsibilities() {
        LinkedHashMap<String, String> keyed = new LinkedHashMap<>();
        for (int index = 1; index <= responsibilities.size(); index++) {
            keyed.put(key(index), responsibilities.get(index - 1));
        }
        return Collections.unmodifiableMap(keyed);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
