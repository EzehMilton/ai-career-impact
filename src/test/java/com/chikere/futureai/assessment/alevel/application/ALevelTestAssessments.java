package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stored A-level assessments for tests: a v2 subject profile as new submissions make, and a historical v1 one. */
public final class ALevelTestAssessments {

    public static final Instant ASSESSED_AT = Instant.parse("2026-10-28T09:00:00Z");

    private ALevelTestAssessments() {
    }

    public static ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of("Mathematics", "A"),
                List.of("Problem solving"), List.of("Economics"), List.of("Finance"), Optional.of("United Kingdom"),
                Optional.of("Keeping options open."));
    }

    /** An {@code a-level-decision-v2} assessment, as every new submission is decided since Stage A2. */
    public static StoredALevelAssessment subjectProfile(UUID id, String fixtureId, AssessmentSource source) {
        var state = state();
        var judgments = new LocalFixtureALevelV2JudgmentProvider(fixtureId).judge(ALevelJudgmentInput.of(state));
        var profile = new ALevelDecisionPolicyV2().decide(judgments);
        return new StoredALevelAssessment(id, ASSESSED_AT, state, judgments,
                new ALevelAssessmentResult(profile, source, judgments.schemaVersion(), profile.policyVersion()),
                Optional.of("jev-latest"));
    }

    /** A historical {@code a-level-decision-v1} assessment, exactly as it was stored before Stage A2. */
    public static StoredALevelAssessment historicalV1(UUID id, String fixtureId) {
        var state = state();
        var judgments = new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(id, ASSESSED_AT, state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE, judgments.schemaVersion(),
                        decision.policyVersion()), Optional.empty());
    }
}
