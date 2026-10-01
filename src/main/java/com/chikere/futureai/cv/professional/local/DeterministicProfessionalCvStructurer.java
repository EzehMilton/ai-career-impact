package com.chikere.futureai.cv.professional.local;

import com.chikere.futureai.cv.professional.application.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conservative heading-based extraction. Unknown or ambiguous data is left for user review.
 * CV text is data: instruction-like lines and obvious contact details are discarded.
 */
@Component
public final class DeterministicProfessionalCvStructurer implements ProfessionalCvStructurer {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?i)(?:\\+?\\d[\\d ()-]{7,}\\d)");
    private static final Pattern POSTCODE = Pattern.compile("(?i)\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}\\b");
    private static final Pattern YEARS = Pattern.compile("(?i)\\b(\\d{1,2})\\+?\\s+years?(?:'| of)? experience\\b");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i).*(?:19|20)\\d{2}\\s*(?:-|–|—|to)\\s*(?:present|current|(?:19|20)\\d{2}).*");
    private static final Set<String> PROFILE_HEADINGS = Set.of("profile", "professional profile", "summary",
            "professional summary", "personal statement");
    private static final Set<String> EXPERIENCE_HEADINGS = Set.of("experience", "work experience",
            "employment", "employment history", "career history", "professional experience");
    private static final Set<String> SKILL_HEADINGS = Set.of("skills", "key skills", "technical skills",
            "core skills", "competencies", "technical competencies");
    private static final Set<String> STOP_HEADINGS = Set.of("education", "qualifications", "certifications",
            "training", "references", "interests", "hobbies", "contact", "contact details");

    @Override
    public ProfessionalCvParseResult structure(String extractedText) {
        String text = extractedText == null ? "" : extractedText;
        var warnings = new ArrayList<String>();
        var lines = sanitisedLines(text, warnings);
        if (lines.stream().mapToInt(String::length).sum() < 80) {
            return new ProfessionalCvParseResult(Optional.empty(), Optional.empty(), List.of(), List.of(),
                    OptionalInt.empty(), ProfessionalCvParseStatus.INSUFFICIENT_TEXT,
                    List.of("The CV contained too little extractable text. Please enter your work details manually."),
                    ProfessionalCvVersions.PARSER_V1);
        }

        List<String> profile = section(lines, PROFILE_HEADINGS, allHeadingsExcept(PROFILE_HEADINGS));
        List<String> experience = section(lines, EXPERIENCE_HEADINGS, allHeadingsExcept(EXPERIENCE_HEADINGS));
        List<String> skillSection = section(lines, SKILL_HEADINGS, allHeadingsExcept(SKILL_HEADINGS));

        OptionalInt years = explicitYears(lines);
        Optional<String> profession = currentProfession(experience);
        if (profession.isEmpty()) {
            profession = labelledValue(lines, "current role", "current position", "profession", "job title");
        }

        List<String> responsibilities = currentRoleResponsibilities(experience, profession);
        List<String> skills = skills(skillSection);
        Optional<String> description = description(profile, responsibilities);

        long datedRoles = experience.stream().filter(line -> DATE_RANGE.matcher(line).matches()).count();
        if (datedRoles > 1) {
            warnings.add("This CV contains several roles. Only the first, most recent role was used; please review it.");
        }
        if (profession.isEmpty()) {
            warnings.add("A current job title could not be identified.");
        }
        if (responsibilities.isEmpty()) {
            warnings.add("Responsibilities could not be extracted reliably.");
        }
        if (skills.isEmpty()) {
            warnings.add("No explicit skills section could be identified.");
        }
        if (years.isEmpty()) {
            warnings.add("Years of experience was left blank because it could not be derived safely.");
        }

        boolean complete = profession.isPresent() && description.isPresent()
                && !responsibilities.isEmpty() && !skills.isEmpty();
        return new ProfessionalCvParseResult(profession, description, responsibilities, skills, years,
                complete ? ProfessionalCvParseStatus.EXTRACTED : ProfessionalCvParseStatus.PARTIAL,
                warnings, ProfessionalCvVersions.PARSER_V1);
    }

    private List<String> sanitisedLines(String text, List<String> warnings) {
        var result = new ArrayList<String>();
        boolean removedInstructions = false;
        for (String raw : text.replace('\u00a0', ' ').split("\\R")) {
            String line = raw.strip().replaceFirst("^[•▪◦●*-]\\s*", "");
            if (line.isBlank() || EMAIL.matcher(line).find()
                    || (!DATE_RANGE.matcher(line).matches() && PHONE.matcher(line).find())
                    || POSTCODE.matcher(line).find()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if ((lower.contains("ignore previous") || lower.contains("ignore all previous")
                    || lower.contains("system prompt") || lower.contains("assistant instruction")
                    || lower.contains("set profession to"))) {
                removedInstructions = true;
                continue;
            }
            result.add(line);
        }
        if (removedInstructions) {
            warnings.add("Instruction-like text in the document was ignored.");
        }
        return result;
    }

    private Optional<String> currentProfession(List<String> experience) {
        for (int index = 0; index < experience.size(); index++) {
            String line = experience.get(index);
            if (DATE_RANGE.matcher(line).matches()) {
                if (index > 0 && plausibleRole(experience.get(index - 1))) {
                    return Optional.of(rolePart(experience.get(index - 1)));
                }
                if (index + 1 < experience.size() && plausibleRole(experience.get(index + 1))) {
                    return Optional.of(rolePart(experience.get(index + 1)));
                }
            }
            if (line.contains("|") && plausibleRole(line)) {
                return Optional.of(rolePart(line));
            }
        }
        return experience.stream().filter(this::plausibleRole).findFirst().map(this::rolePart);
    }

    private boolean plausibleRole(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return line.length() <= 120 && line.split("\\s+").length <= 12
                && !DATE_RANGE.matcher(line).matches()
                && !lower.startsWith("responsible for") && !lower.startsWith("worked on")
                && !lower.startsWith("managed ") && !lower.startsWith("developed ");
    }

    private String rolePart(String line) {
        return line.split("\\s*[|–—]\\s*", 2)[0].strip();
    }

    private List<String> currentRoleResponsibilities(List<String> experience, Optional<String> profession) {
        if (experience.isEmpty()) return List.of();
        int roleIndex = profession.map(role -> indexContaining(experience, role)).orElse(-1);
        int start = roleIndex < 0 ? 0 : roleIndex + 1;
        if (start < experience.size() && DATE_RANGE.matcher(experience.get(start)).matches()) start++;
        var result = new ArrayList<String>();
        for (int index = start; index < experience.size() && result.size() < 8; index++) {
            String line = experience.get(index);
            if (DATE_RANGE.matcher(line).matches() && !result.isEmpty()) break;
            if (line.contains("|") && !result.isEmpty()) break;
            if (line.length() >= 20 && !DATE_RANGE.matcher(line).matches()) result.add(line);
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private int indexContaining(List<String> lines, String value) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).contains(value)) return index;
        }
        return -1;
    }

    private List<String> skills(List<String> section) {
        var values = new LinkedHashSet<String>();
        for (String line : section) {
            for (String part : line.split("[,;|]")) {
                String skill = part.strip();
                if (!skill.isBlank() && skill.length() <= 100) values.add(skill);
            }
        }
        return values.stream().limit(20).toList();
    }

    private Optional<String> description(List<String> profile, List<String> responsibilities) {
        String value = !profile.isEmpty() ? String.join(" ", profile)
                : responsibilities.isEmpty() ? "" : String.join(" ", responsibilities);
        if (value.length() > 4000) value = value.substring(0, 4000);
        return value.length() >= 40 ? Optional.of(value) : Optional.empty();
    }

    private OptionalInt explicitYears(List<String> lines) {
        for (String line : lines) {
            var match = YEARS.matcher(line);
            if (match.find()) {
                int value = Integer.parseInt(match.group(1));
                if (value <= 70) return OptionalInt.of(value);
            }
        }
        return OptionalInt.empty();
    }

    private Optional<String> labelledValue(List<String> lines, String... labels) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String label : labels) {
                if (lower.startsWith(label + ":")) {
                    String value = line.substring(line.indexOf(':') + 1).strip();
                    if (!value.isBlank() && value.length() <= 120) return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> section(List<String> lines, Set<String> starts, Set<String> stops) {
        var result = new ArrayList<String>();
        boolean collecting = false;
        for (String line : lines) {
            String heading = normalisedHeading(line);
            if (starts.contains(heading)) {
                collecting = true;
                continue;
            }
            if (collecting && stops.contains(heading)) break;
            if (collecting) result.add(line);
        }
        return result;
    }

    private Set<String> allHeadingsExcept(Set<String> selected) {
        var headings = new LinkedHashSet<String>();
        headings.addAll(PROFILE_HEADINGS);
        headings.addAll(EXPERIENCE_HEADINGS);
        headings.addAll(SKILL_HEADINGS);
        headings.addAll(STOP_HEADINGS);
        headings.removeAll(selected);
        return headings;
    }

    private String normalisedHeading(String line) {
        return line.toLowerCase(Locale.ROOT).replace(":", "").strip();
    }
}
