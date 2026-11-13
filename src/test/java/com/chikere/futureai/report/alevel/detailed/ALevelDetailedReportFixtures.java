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

/** Complete v2 and v3 reports assembled from development fixtures, for rendering tests. */
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

    public static ALevelDetailedReport reportV3(String fixture) {
        var assessment = ALevelTestAssessments.subjectProfile(UUID.randomUUID(), fixture, AssessmentSource.JEV);
        var guidance = new ALevelGuidancePolicyV1().decide(assessment.result().profile(), assessment.state());
        var narrative = new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.AVAILABLE,
                Optional.of(ScriptedALevelPaidNarrativeGenerator.cleanNarrative()), "a-level-paid-narrative-v1",
                "scripted", Optional.of("req_fixture"), Optional.of("scripted-model"), 1, Optional.empty());
        return ALevelDetailedReportAssembler.v3().assemble(assessment, guidance, narrative, Optional.empty());
    }

    public static ALevelDetailedReportSnapshot stored(UUID assessmentId) {
        var report = report("maths_economics_geography");
        return new ALevelDetailedReportSnapshot(UUID.randomUUID(), assessmentId, UUID.randomUUID(), "a-level-v2",
                "a-level-decision-v2", "a-level-guidance-policy-v1", "a-level-paid-narrative-v1",
                ALevelPaidNarrativeStatus.AVAILABLE, Optional.empty(), "a-level-report-v2", "a-level-report-pdf-v2",
                Instant.parse("2026-10-28T10:00:00Z"), report);
    }

    public static ALevelDetailedReportSnapshot storedV3(UUID assessmentId) {
        ALevelDetailedReport report = reportV3("maths_economics_geography");
        return new ALevelDetailedReportSnapshot(UUID.randomUUID(), assessmentId, UUID.randomUUID(), "a-level-v2",
                "a-level-decision-v2", "a-level-guidance-policy-v1", "a-level-paid-narrative-v1",
                ALevelPaidNarrativeStatus.AVAILABLE, Optional.empty(), "a-level-report-v3", "a-level-report-pdf-v3",
                Instant.parse("2026-11-13T10:00:00Z"), report);
    }
}
