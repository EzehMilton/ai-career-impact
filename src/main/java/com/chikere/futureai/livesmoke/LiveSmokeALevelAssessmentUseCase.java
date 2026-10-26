package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentForm;
import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentFormMapper;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** As {@link LiveSmokeProfessionalAssessmentUseCase}, for the A-level journey. */
final class LiveSmokeALevelAssessmentUseCase implements ALevelAssessmentUseCase {

    private final ALevelAssessmentUseCase workflow;
    private final LiveSmokeEntitlements entitlements;
    /** Each case's domain state, mapped once with an untraced mapper so matching never writes trace events. */
    private final Map<ALevelAssessmentState, LiveSmokeCase> casesByState;
    private final LiveSmokeRuns runs;

    LiveSmokeALevelAssessmentUseCase(ALevelAssessmentUseCase workflow, LiveSmokeEntitlements entitlements,
            LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        this.workflow = Objects.requireNonNull(workflow);
        this.entitlements = Objects.requireNonNull(entitlements);
        var mapper = new ALevelAssessmentFormMapper();
        this.casesByState = catalog.forJourney(LiveSmokeJourney.A_LEVEL).stream().collect(Collectors.toMap(
                value -> mapper.toState((ALevelAssessmentForm) catalog.form(value)), value -> value, (a, b) -> a));
        this.runs = Objects.requireNonNull(runs);
    }

    @Override
    public UUID submit(ALevelAssessmentState state) {
        UUID id = workflow.submit(state);
        runs.completed(LiveSmokeJourney.A_LEVEL, id, Optional.ofNullable(casesByState.get(state)));
        entitlements.grant(LiveSmokeJourney.A_LEVEL, id);
        return id;
    }
}
