package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentUseCase;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentForm;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentFormMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** As {@link LiveSmokeProfessionalAssessmentUseCase}, for the University journey. */
final class LiveSmokeUniversityAssessmentUseCase implements UniversityAssessmentUseCase {

    private final UniversityAssessmentUseCase workflow;
    private final LiveSmokeEntitlements entitlements;
    /** Each case's domain state, mapped once with an untraced mapper so matching never writes trace events. */
    private final Map<UniversityAssessmentState, LiveSmokeCase> casesByState;
    private final LiveSmokeRuns runs;

    LiveSmokeUniversityAssessmentUseCase(UniversityAssessmentUseCase workflow, LiveSmokeEntitlements entitlements,
            LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        this.workflow = Objects.requireNonNull(workflow);
        this.entitlements = Objects.requireNonNull(entitlements);
        var mapper = new UniversityAssessmentFormMapper();
        this.casesByState = catalog.forJourney(LiveSmokeJourney.UNIVERSITY).stream().collect(Collectors.toMap(
                value -> mapper.toState((UniversityAssessmentForm) catalog.form(value)), value -> value, (a, b) -> a));
        this.runs = Objects.requireNonNull(runs);
    }

    @Override
    public UUID submit(UniversityAssessmentState state) {
        UUID id = workflow.submit(state);
        runs.completed(LiveSmokeJourney.UNIVERSITY, id, Optional.ofNullable(casesByState.get(state)));
        entitlements.grant(LiveSmokeJourney.UNIVERSITY, id);
        return id;
    }
}
