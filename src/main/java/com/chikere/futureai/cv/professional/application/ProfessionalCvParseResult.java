package com.chikere.futureai.cv.professional.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record ProfessionalCvParseResult(
        Optional<String> profession,
        Optional<String> workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsOfExperience,
        ProfessionalCvParseStatus status,
        List<String> warnings,
        String parserVersion) {

    public ProfessionalCvParseResult {
        profession = Objects.requireNonNull(profession);
        workDescription = Objects.requireNonNull(workDescription);
        responsibilities = List.copyOf(Objects.requireNonNull(responsibilities));
        skills = List.copyOf(Objects.requireNonNull(skills));
        yearsOfExperience = Objects.requireNonNull(yearsOfExperience);
        status = Objects.requireNonNull(status);
        warnings = List.copyOf(Objects.requireNonNull(warnings));
        if (parserVersion == null || parserVersion.isBlank()) {
            throw new IllegalArgumentException("parserVersion must not be blank");
        }
    }
}
