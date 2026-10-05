package com.chikere.futureai.occupation.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalOccupationClassificationService implements ProfessionalOccupationClassificationUseCase {
    private final ProfessionalAssessmentStore assessmentStore;
    private final ProfessionalOccupationClassificationStore classificationStore;
    private final Optional<ProfessionalOccupationClassifier> classifier;
    private final String geography;
    private final Clock clock;

    public ProfessionalOccupationClassificationService(ProfessionalAssessmentStore assessmentStore,
            ProfessionalOccupationClassificationStore classificationStore,
            Optional<ProfessionalOccupationClassifier> classifier, String geography, Clock clock) {
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
        if (existing.isPresent() || classifier.isEmpty()) return existing;
        var assessment = assessmentStore.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        var state = assessment.state();
        var request = new ProfessionalOccupationClassificationRequest(state.profession(), state.responsibilities(),
                state.skills(), geography);
        return classifier.orElseThrow().classify(request).map(value -> {
            var stored = new StoredProfessionalOccupationClassification(assessmentId, value, Instant.now(clock));
            classificationStore.save(stored);
            return stored;
        });
    }

    public Optional<StoredProfessionalOccupationClassification> findFor(UUID assessmentId) {
        return classificationStore.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }
}
