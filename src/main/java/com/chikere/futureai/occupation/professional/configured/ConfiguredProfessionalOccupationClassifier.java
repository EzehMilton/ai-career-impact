package com.chikere.futureai.occupation.professional.configured;

import com.chikere.futureai.occupation.professional.application.*;

import java.util.Optional;

/** Exact, operator-configured mapping used until a broader official lookup is introduced. */
public final class ConfiguredProfessionalOccupationClassifier implements ProfessionalOccupationClassifier {
    private final String profession;
    private final String canonicalTitle;
    private final String soc2020Code;

    public ConfiguredProfessionalOccupationClassifier(String profession, String canonicalTitle, String soc2020Code) {
        this.profession = required(profession, "configured profession");
        this.canonicalTitle = required(canonicalTitle, "configured canonical title");
        this.soc2020Code = required(soc2020Code, "configured SOC 2020 code");
    }

    @Override
    public Optional<ProfessionalOccupationClassification> classify(
            ProfessionalOccupationClassificationRequest request) {
        if (!request.profession().equals(profession)) return Optional.empty();
        return Optional.of(new ProfessionalOccupationClassification(canonicalTitle,
                OccupationClassificationSystem.SOC_2020, soc2020Code,
                OccupationClassificationSource.CONFIGURED_MAPPING,
                OccupationClassificationQuality.EXACT_CONFIGURED_MAPPING,
                "configured-soc2020-mapping", ProfessionalOccupationClassifierVersions.V1));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
