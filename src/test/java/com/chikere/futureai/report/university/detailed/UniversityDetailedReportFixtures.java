package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.report.university.application.UniversityCourseProfile;
import com.chikere.futureai.report.university.application.UniversityReportVersions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A small, complete university-report-v2 document, as stored before v3, for rendering tests. */
public final class UniversityDetailedReportFixtures {

    private UniversityDetailedReportFixtures() {
    }

    public static UniversityDetailedReport report() {
        return report(List.of());
    }

    public static UniversityDetailedReport report(List<UniversityDetailedReport.Entry> technical) {
        var measure = new UniversityCourseProfile.Measure("WORKING_ALONGSIDE_AI", "Working Alongside AI", "5.20",
                "MODERATE", "Moderate Working Alongside AI", "How the course works alongside AI.",
                List.of("Learning that AI can support"), List.of("Independent demonstration"), "");
        var profile = new UniversityCourseProfile("Your course profile in an AI-shaped world", false,
                List.of(measure), "Summary.");
        return new UniversityDetailedReport("university-report-v2", "BSc Nursing <Adult>", "A university", profile,
                new UniversityDetailedReport.Perspectives(true, "Overall view.", "AI learning view.", "Human view.",
                        "Transferable view.", "Plan introduction.", null, null),
                new UniversityDetailedReport.Modules("All 1 modules listed for the course are included.",
                        List.of(new UniversityDetailedReport.ModuleCard(1, "Clinical Practice",
                                "Mainly practical or experiential", "Practical work leads.", List.of("Keep a log.")))),
                "These are areas where AI may help with learning, preparation, exploration or checking. This does "
                        + "not mean AI use is permitted in assessed work.",
                List.of(new UniversityDetailedReport.Entry("Revision", "Use AI to quiz yourself.")),
                List.of(new UniversityDetailedReport.Entry("Placements", "Your own practice.")),
                technical,
                List.of(new UniversityDetailedReport.Skill("Clinical reasoning", "Reason through cases.", List.of(), null)),
                List.of(new UniversityDetailedReport.Entry("Check the rules", "Check your institution's rules.")),
                List.of(new UniversityDetailedReport.Phase("Now", List.of("Action one", "Action two"), null),
                        new UniversityDetailedReport.Phase("This year", List.of("Action three", "Action four"), null),
                        new UniversityDetailedReport.Phase("Later", List.of("Action five", "Action six"), null)),
                List.of("This report assesses the course, not your own ability."),
                new UniversityDetailedReport.Provenance(UUID.randomUUID(), UUID.randomUUID(), "university-decision-v2",
                        "university-module-v1", "university-module-policy-v1", "university-guidance-policy-v1",
                        "university-paid-narrative-v1", "AVAILABLE", "university-report-pdf-v2"), null);
    }

    /** The report stored for {@code assessmentId}, as the v2 lifecycle freezes it. */
    public static UniversityDetailedReportSnapshot stored(UUID assessmentId) {
        var report = report();
        return new UniversityDetailedReportSnapshot(UUID.randomUUID(), assessmentId, UUID.randomUUID(),
                "university-decision-v2", report.provenance().moduleAnalysisId(), "university-module-v1",
                "university-module-policy-v1", "university-guidance-policy-v1", "university-paid-narrative-v1",
                UniversityPaidNarrativeStatus.AVAILABLE, UniversityReportVersions.V2, UniversityReportVersions.PDF_V2,
                Instant.parse("2026-10-28T09:00:00Z"), report);
    }
}
