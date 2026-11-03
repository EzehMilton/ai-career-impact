package com.chikere.futureai.narrative.professional.paid;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a plan-action note only restates its deterministic action. A note earns its place by adding practical
 * interpretation (inputs, what to review, how to judge success); one whose content words are almost all already in
 * the action's title, rationale or review note adds nothing and reads as duplicated text. Word overlap after light
 * normalisation (case, punctuation, possessives, common suffixes, function words) is enough to catch identical
 * wording, punctuation changes and small rephrasings, without any semantic machinery.
 */
public final class PlanNoteRestatement {

    /** A note must add at least this many content words of its own... */
    static final int MIN_NEW_WORDS = 3;
    /** ...and they must make up at least this share of its content words. */
    static final double MIN_NEW_SHARE = 0.3;

    private static final Set<String> FUNCTION_WORDS = Set.of("a", "an", "and", "any", "are", "as", "at", "be", "before",
            "by", "can", "do", "each", "for", "from", "how", "if", "in", "into", "is", "it", "its", "may", "of", "on",
            "or", "so", "such", "that", "the", "their", "them", "then", "these", "this", "those", "to", "up", "what",
            "when", "where", "which", "while", "who", "will", "with", "you", "your", "yours", "first", "always",
            "should", "must", "could", "would", "about", "also", "all", "here", "there", "own", "per", "just");

    private PlanNoteRestatement() {
    }

    /** True when {@code note} adds too few content words beyond {@code deterministic} to be worth showing. */
    public static boolean restates(String note, String... deterministic) {
        Objects.requireNonNull(note, "note must not be null");
        Set<String> noteWords = words(note);
        if (noteWords.isEmpty()) {
            return true;
        }
        Set<String> known = Arrays.stream(deterministic).filter(Objects::nonNull).flatMap(text -> words(text).stream())
                .collect(Collectors.toSet());
        long added = noteWords.stream().filter(word -> !known.contains(word)).count();
        return added < MIN_NEW_WORDS || added < MIN_NEW_SHARE * noteWords.size();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("'s\\b", "")
                        .split("[^a-z0-9]+"))
                .filter(word -> word.length() > 1 && !FUNCTION_WORDS.contains(word))
                .map(PlanNoteRestatement::stem)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Crude suffix stripping, applied to both sides alike: "reviewing", "reviewed" and "reviews" match "review". */
    private static String stem(String word) {
        for (String suffix : new String[] {"ing", "ed", "es", "s"}) {
            if (word.length() > suffix.length() + 3 && word.endsWith(suffix)) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }
}
