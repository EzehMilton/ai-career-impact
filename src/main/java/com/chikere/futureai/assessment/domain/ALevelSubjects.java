package com.chikere.futureai.assessment.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic canonicalisation of A-level subject names.
 *
 * <p>This is a naming aid only. It maps common abbreviations and spellings onto a consistent
 * display name so the same combination is stored, judged and shown identically however a student
 * typed it.</p>
 *
 * <p>It deliberately carries <strong>no scores, weights, rankings or ordering of subjects</strong>.
 * There is no "Mathematics is worth more than Geography" table here or anywhere else: the
 * assessment judges the combination's capabilities, not a list of subject points. Unknown subjects
 * are accepted and simply title-cased, so a student can enter any subject their school offers.</p>
 */
public final class ALevelSubjects {

    public static final String COMPUTER_SCIENCE = "Computer Science";

    private static final Map<String, String> ALIASES;

    static {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "Mathematics", "maths", "math", "mathematics", "a level maths", "core maths");
        alias(aliases, "Further Mathematics", "further maths", "further math", "further mathematics",
                "fm maths");
        alias(aliases, COMPUTER_SCIENCE, "computer science", "comp sci", "compsci", "computing",
                "computer studies");
        alias(aliases, "Physics", "physics", "phys");
        alias(aliases, "Chemistry", "chemistry", "chem");
        alias(aliases, "Biology", "biology", "bio");
        alias(aliases, "Economics", "economics", "econ", "econs");
        alias(aliases, "Geography", "geography", "geog");
        alias(aliases, "History", "history", "hist");
        alias(aliases, "Politics", "politics", "government and politics", "government & politics",
                "gov and politics");
        alias(aliases, "Sociology", "sociology", "socio");
        alias(aliases, "Psychology", "psychology", "psych");
        alias(aliases, "English Literature", "english literature", "english lit", "eng lit");
        alias(aliases, "English Language", "english language", "english lang", "eng lang");
        alias(aliases, "English Language and Literature", "english language and literature",
                "english lang and lit");
        alias(aliases, "Business Studies", "business studies", "business", "bus studies");
        alias(aliases, "Art and Design", "art and design", "art", "fine art", "art & design");
        alias(aliases, "Design and Technology", "design and technology", "design technology",
                "d and t", "dt", "product design");
        alias(aliases, "Media Studies", "media studies", "media");
        alias(aliases, "Drama and Theatre", "drama and theatre", "drama", "theatre studies");
        alias(aliases, "Music", "music");
        alias(aliases, "Physical Education", "physical education", "pe", "sport science",
                "sports science");
        alias(aliases, "Religious Studies", "religious studies", "re", "rs", "philosophy and ethics");
        alias(aliases, "Philosophy", "philosophy");
        alias(aliases, "French", "french");
        alias(aliases, "German", "german");
        alias(aliases, "Spanish", "spanish");
        alias(aliases, "Statistics", "statistics", "stats");
        alias(aliases, "Law", "law");
        ALIASES = Map.copyOf(aliases);
    }

    private ALevelSubjects() {
    }

    /**
     * Returns the canonical display name for a subject.
     *
     * <p>An unrecognised subject is accepted and title-cased rather than rejected or scored.</p>
     */
    public static String canonicalise(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        String collapsed = subject.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        String lookup = collapsed.toLowerCase(Locale.UK)
                .replaceAll("^a[ -]?levels? ", "")
                .replaceAll("[.]", "")
                .strip();
        String canonical = ALIASES.get(lookup);
        return canonical != null ? canonical : titleCase(collapsed);
    }

    private static String titleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean newWord = true;
        for (char character : value.toCharArray()) {
            if (newWord && Character.isLetter(character)) {
                out.append(Character.toUpperCase(character));
                newWord = false;
            }
            else {
                out.append(character);
                if (character == ' ' || character == '-' || character == '(') {
                    newWord = true;
                }
            }
        }
        return out.toString();
    }

    private static void alias(Map<String, String> target, String canonical, String... forms) {
        for (String form : forms) {
            if (target.put(form, canonical) != null) {
                throw new IllegalStateException("duplicate subject alias: " + form);
            }
        }
    }
}
