package com.chikere.futureai.narrative.professional.application;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a prohibited claim in narrative text, sentence by sentence, while letting an explicit disclaimer of
 * that claim through.
 *
 * <p>A match is disregarded only when a denial such as "does not predict", "does not imply that" or "is not a
 * probability" appears earlier in the same clause. A clause ends at a semicolon or at "but" / "however", so
 * "This does not mean much, but AI cannot replicate it" is still caught.</p>
 */
final class NarrativeClaimMatcher {

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile("(?i);|\\bbut\\b|\\bhowever\\b");
    private static final Pattern DISCLAIMER = Pattern.compile("(?i)"
            + "\\b(?:does|do|did|is|are|was|were|should|can|could|would|will)\\s*(?:not|n't)\\s+(?:\\w+\\s+){0,3}?"
            + "(?:predict|show|mean|imply|indicate|suggest|claim|say|tell|assess|measure|forecast|establish|prove"
            + "|determine|guarantee|describe|reflect|evaluate|judge|consider)\\w*"
            + "|\\bnot\\s+(?:a|an)\\s+(?:prediction|probability|forecast|guarantee|measure|assessment|statement"
            + "|judgement|verdict|claim)\\b"
            + "|\\bwithout\\s+(?:predicting|implying|suggesting|claiming)\\b"
            + "|\\bno\\s+(?:prediction|claim|forecast|guarantee)\\b");

    private NarrativeClaimMatcher() {
    }

    /** The first sentence that makes one of the claims without disclaiming it. */
    static Optional<String> firstUndisclaimed(List<String> texts, List<Pattern> claims) {
        for (String text : texts) {
            for (String sentence : sentences(text)) {
                for (Pattern claim : claims) {
                    Matcher matcher = claim.matcher(sentence);
                    while (matcher.find()) {
                        // Up to the end of the match, so "not a probability that…" disclaims its own noun.
                        if (!disclaimed(sentence.substring(0, matcher.end()))) {
                            return Optional.of(sentence.strip());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static List<String> sentences(String text) {
        return List.of(SENTENCE_BREAK.split(text.strip()));
    }

    /** Whether the text up to and including a claim disclaims it within the claim's clause. */
    static boolean disclaimed(String before) {
        Matcher breaks = CLAUSE_BREAK.matcher(before);
        int clauseStart = 0;
        while (breaks.find()) {
            clauseStart = breaks.end();
        }
        return DISCLAIMER.matcher(before.substring(clauseStart)).find();
    }
}
