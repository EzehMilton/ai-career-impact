package com.chikere.futureai.cv.professional.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/** Small structural safeguards; semantic interpretation remains the extractor's responsibility. */
@Component
public final class ProfessionalProfileValidator {
    public static final int MAXIMUM_EMPLOYER_LENGTH = 120;
    public static final int MAXIMUM_ROLE_DATE_LENGTH = 40;
    private static final Set<String> RESPONSIBILITY_PREFIXES = Set.of(
            "responsible for ", "managed ", "developed ", "worked on ", "designed ", "delivered ",
            "leading ", "managing ", "developing ", "working ", "designing ", "delivering ");

    public ProfessionalProfile validate(ProfessionalProfile candidate) {
        if (candidate == null) {
            throw new ProfessionalProfileExtractionException("Profile extraction returned no profile");
        }
        var warnings = new ArrayList<>(candidate.warnings());
        String role = clean(candidate.currentRole());
        if (!plausibleRole(role)) {
            warnings.add("A reliable current role could not be identified.");
            role = "";
        }

        List<String> responsibilities = entries(candidate.responsibilities(), 300, 40, warnings,
                "Some responsibility entries were omitted because they were too long.");
        if (!role.isBlank()) {
            String normalisedRole = normalise(role);
            boolean duplicate = responsibilities.removeIf(value -> normalise(value).equals(normalisedRole));
            if (duplicate) warnings.add("Text duplicated as both role and responsibility was omitted.");
        }
        List<String> technical = entries(candidate.technicalSkills(), 120, 40, warnings,
                "Some technical skill entries were omitted because they looked like paragraphs.");
        List<String> human = entries(candidate.humanSkills(), 120, 40, warnings,
                "Some human skill entries were omitted because they looked like paragraphs.");
        List<String> leadership = entries(candidate.leadershipResponsibilities(), 300, 20, warnings,
                "Some leadership entries were omitted because they were too long.");
        List<String> decisions = entries(candidate.decisionMakingResponsibilities(), 300, 20, warnings,
                "Some decision-making entries were omitted because they were too long.");
        List<String> domain = entries(candidate.domainContext(), 120, 20, warnings,
                "Some domain entries were omitted because they were too long.");

        OptionalInt years = candidate.yearsExperience();
        if (years.isPresent() && (years.getAsInt() < 0 || years.getAsInt() > 70)) {
            years = OptionalInt.empty();
            warnings.add("Years of experience was omitted because the extracted value was implausible.");
        }
        String summary = clean(candidate.roleSummary());
        if (summary.length() > 4_000) {
            summary = summary.substring(0, 4_000).strip();
            warnings.add("The role summary was shortened for review.");
        }
        if (role.isBlank() || summary.length() < 40 || responsibilities.isEmpty()
                || (technical.isEmpty() && human.isEmpty())) {
            throw new ProfessionalProfileExtractionException(
                    "The extracted profile did not contain enough reliable career information");
        }
        // Employer and dates only tell the user which job was used: an implausible value is dropped
        // silently and never rejects the profile.
        String employer = bounded(candidate.currentEmployer(), MAXIMUM_EMPLOYER_LENGTH);
        String start = bounded(candidate.currentRoleStart(), MAXIMUM_ROLE_DATE_LENGTH);
        String end = bounded(candidate.currentRoleEnd(), MAXIMUM_ROLE_DATE_LENGTH);
        boolean complete = years.isPresent() && warnings.isEmpty();
        return new ProfessionalProfile(role, employer, start, end, summary, responsibilities, technical, human,
                leadership, decisions, domain, years, years.isPresent() ? candidate.experienceBasis() : List.of(),
                years.isPresent() ? candidate.experienceExcluded() : List.of(),
                complete ? ProfessionalProfileCompleteness.COMPLETE : ProfessionalProfileCompleteness.PARTIAL,
                List.copyOf(new LinkedHashSet<>(warnings)));
    }

    private boolean plausibleRole(String role) {
        if (role.isBlank() || role.length() > 120 || role.contains("\n")
                || role.split("\\s+").length > 15 || role.endsWith(".")) return false;
        String lower = role.toLowerCase(Locale.ROOT);
        return RESPONSIBILITY_PREFIXES.stream().noneMatch(lower::startsWith)
                && !lower.contains(" responsible for ");
    }

    private List<String> entries(List<String> source, int maxLength, int maxItems,
            List<String> warnings, String warning) {
        var values = new LinkedHashSet<String>();
        boolean omitted = false;
        for (String item : source) {
            String value = clean(item).replaceFirst("^[•▪◦●*-]\\s*", "");
            if (value.isBlank()) continue;
            if (value.length() > maxLength || value.contains("\n")) {
                omitted = true;
                continue;
            }
            values.add(value);
            if (values.size() == maxItems) break;
        }
        if (omitted) warnings.add(warning);
        return new ArrayList<>(values);
    }

    private String bounded(String value, int maxLength) {
        String stripped = value == null ? "" : value.strip();
        return stripped.length() > maxLength ? "" : stripped;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\t ]+", " ");
    }

    private String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
