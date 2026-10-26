package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentFormMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs every Professional submission through the normal workflow unchanged, then grants detailed-report access
 * through {@link LiveSmokeEntitlements}. Exists only under the live-smoke profile.
 */
final class LiveSmokeProfessionalAssessmentUseCase implements ProfessionalAssessmentUseCase {

    private final ProfessionalAssessmentUseCase workflow;
    private final LiveSmokeEntitlements entitlements;
    /** Each case's domain state, mapped once with an untraced mapper so matching never writes trace events. */
    private final Map<ProfessionalAssessmentState, LiveSmokeCase> casesByState;
    private final LiveSmokeRuns runs;

    LiveSmokeProfessionalAssessmentUseCase(ProfessionalAssessmentUseCase workflow, LiveSmokeEntitlements entitlements,
            LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        this.workflow = Objects.requireNonNull(workflow);
        this.entitlements = Objects.requireNonNull(entitlements);
        var mapper = new ProfessionalAssessmentFormMapper();
        this.casesByState = catalog.forJourney(LiveSmokeJourney.PROFESSIONAL).stream().collect(Collectors.toMap(
                value -> mapper.toState((ProfessionalAssessmentForm) catalog.form(value)), value -> value, (a, b) -> a));
        this.runs = Objects.requireNonNull(runs);
    }

    @Override
    public UUID submit(ProfessionalAssessmentState state) {
        return completed(state, workflow.submit(state));
    }

    @Override
    public UUID submit(ProfessionalAssessmentState state, ProfessionalAssessmentInputMetadata inputMetadata) {
        return completed(state, workflow.submit(state, inputMetadata));
    }

    private UUID completed(ProfessionalAssessmentState state, UUID id) {
        runs.completed(LiveSmokeJourney.PROFESSIONAL, id, Optional.ofNullable(casesByState.get(state)));
        entitlements.grant(LiveSmokeJourney.PROFESSIONAL, id);
        return id;
    }
}
