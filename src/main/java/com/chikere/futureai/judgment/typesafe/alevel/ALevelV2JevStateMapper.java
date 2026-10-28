package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;

import java.util.Map;
import java.util.Objects;

/**
 * The {@code a-level-v2} Jev wire state: the canonical subject names and nothing else. It is built from
 * {@link ALevelJudgmentInput}, which never holds student context, so no grade, interest, ambition, country or free
 * text can reach a v2 judgment.
 */
public final class ALevelV2JevStateMapper {

    public static final String SELECTED_SUBJECTS = "selected_subjects";

    public Map<String, Object> map(ALevelJudgmentInput input) {
        Objects.requireNonNull(input, "input must not be null");
        return Map.of(SELECTED_SUBJECTS, input.subjects());
    }
}
