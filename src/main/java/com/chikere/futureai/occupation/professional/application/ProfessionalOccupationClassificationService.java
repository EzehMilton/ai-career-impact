package com.chikere.futureai.occupation.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.trace.AssessmentTrace;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.TraceStage;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalOccupationClassificationService implements ProfessionalOccupationClassificationUseCase {
    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalOccupationClassificationStore classificationStore;
    private final Optional<ProfessionalOccupationClassifier> classifier;
    private final String geography;
    private final Clock clock;
    private final AssessmentTrace trace;

    public ProfessionalOccupationClassificationService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalOccupationClassificationStore classificationStore,
            Optional<ProfessionalOccupationClassifier> classifier, String geography, Clock clock) {
        this(assessmentStore, classificationStore, classifier, geography, clock, AssessmentTrace.disabled());
    }

    public ProfessionalOccupationClassificationService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalOccupationClassificationStore classificationStore,
            Optional<ProfessionalOccupationClassifier> classifier, String geography, Clock clock,
                    AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.assessmentStore = Objects.requireNonNull(assessmentStore);
        this.classificationStore = Objects.requireNonNull(classificationStore);
        this.classifier = Objects.requireNonNull(classifier);
        if (geography == null || geography.isBlank()) throw new IllegalArgumentException("geography must not be blank");
        this.geography = geography.strip();
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<StoredProfessionalOccupationClassification> classifyFor(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        var existing = classificationStore.findByAssessmentId(assessmentId);
        if (existing.isPresent() || classifier.isEmpty()) {
            trace.event(JourneyType.PROFESSIONAL, TraceStage.OCCUPATION_CLASSIFICATION, () -> Map.of(
                    "status", "SKIPPED", "reason", existing.isPresent()
                            ? "classification already stored for this assessment"
                            : "no occupation classifier is configured"));
            return existing;
        }
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        var state = assessment.state();
        var request = new ProfessionalOccupationClassificationRequest(state.profession(), state.responsibilities(),
                state.skills(), geography);
        var configured = classifier.orElseThrow();
        return trace.timed(JourneyType.PROFESSIONAL, TraceStage.OCCUPATION_CLASSIFICATION, "occupationClassificationMs",
                () -> configured.classify(request), result -> Map.of(
                        "classifier", configured.getClass().getSimpleName(),
                        "request", request,
                        "classification",
                                result.<Object>map(value -> value).orElse("none: no configured classification matched")))
                .map(value -> {
            var stored = new StoredProfessionalOccupationClassification(assessmentId, value, Instant.now(clock));
            classificationStore.save(stored);
            return stored;
        });
    }

    public Optional<StoredProfessionalOccupationClassification> findFor(UUID assessmentId) {
        return classificationStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }
}
