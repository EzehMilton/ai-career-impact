package com.chikere.futureai.occupation.professional.application;

import java.util.Optional;

public interface ProfessionalOccupationClassifier {
    Optional<ProfessionalOccupationClassification> classify(ProfessionalOccupationClassificationRequest request);
}
