package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.alevel.application.ALevelTestAssessments;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.guidance.alevel.ALevelGuidancePolicyV1;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeOutcome;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.narrative.alevel.paid.ScriptedALevelPaidNarrativeGenerator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A complete a-level-report-v2 assembled from a development fixture, for rendering tests. */
public final class ALevelDetailedReportFixtures {

    private ALevelDetailedReportFixtures() {
    }

    public static ALevelDetailedReport report(String fixture) {
        var assessment = ALevelTestAssessments.subjectProfile(UUID.randomUUID(), fixture, AssessmentSource.JEV);
        var guidance = new ALevelGuidancePolicyV1().decide(assessment.result().profile(), assessment.state());
        var narrative = new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.AVAILABLE,
                Optional.of(ScriptedALevelPaidNarrativeGenerator.cleanNarrative()), "a-level-paid-narrative-v1",
                "openai", Optional.of("model"), Optional.of("req"), 1, Optional.empty());
        return new ALevelDetailedReportAssembler().assemble(assessment, guidance, narrative, Optional.empty());
    }

    public static ALevelDetailedReportSnapshot stored(UUID assessmentId) {
        var report = report("maths_economics_geography");
        return new ALevelDetailedReportSnapshot(UUID.randomUUID(), assessmentId, UUID.randomUUID(), "a-level-v2",
                "a-level-decision-v2", "a-level-guidance-policy-v1", "a-level-paid-narrative-v1",
                ALevelPaidNarrativeStatus.AVAILABLE, Optional.empty(), "a-level-report-v2", "a-level-report-pdf-v2",
                Instant.parse("2026-10-28T10:00:00Z"), report);
    }
}
