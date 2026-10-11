package com.chikere.futureai.cv.professional.application;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Years of relevant experience calculated from CV dates, and the jobs that were counted.
 *
 * @param years whole years, or empty when the dates could not be trusted
 * @param basis    one "{title} at {employer} ({start} – {end})" line per counted job; empty with no years
 * @param excluded the same kind of line for each dated job that was not counted; empty with no years
 */
public record ProfessionalExperience(OptionalInt years, List<String> basis, List<String> excluded) {
    public ProfessionalExperience {
        years = Objects.requireNonNull(years);
        basis = List.copyOf(basis == null ? List.of() : basis);
        excluded = List.copyOf(excluded == null ? List.of() : excluded);
    }

    public static ProfessionalExperience unknown() {
        return new ProfessionalExperience(OptionalInt.empty(), List.of(), List.of());
    }
}
