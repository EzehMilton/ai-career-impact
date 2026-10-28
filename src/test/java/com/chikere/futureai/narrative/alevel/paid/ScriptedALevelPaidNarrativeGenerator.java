package com.chikere.futureai.narrative.alevel.paid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Function;

/** A paid-narrative provider that answers from a script and counts every call. Never contacts OpenAI. */
public final class ScriptedALevelPaidNarrativeGenerator implements ALevelPaidNarrativeGenerator {

    private final Deque<Function<ALevelPaidNarrativeContext, ALevelPaidNarrative>> answers = new ArrayDeque<>();
    public int generateCalls;
    public int repairCalls;
    public RepairFeedback lastFeedback;

    /** Queues the next answer; a {@code null} result is returned as-is, and a thrown exception is propagated. */
    public ScriptedALevelPaidNarrativeGenerator then(Function<ALevelPaidNarrativeContext, ALevelPaidNarrative> answer) {
        answers.add(answer);
        return this;
    }

    public ScriptedALevelPaidNarrativeGenerator then(ALevelPaidNarrative answer) {
        return then(context -> answer);
    }

    public ScriptedALevelPaidNarrativeGenerator failing(RuntimeException failure) {
        return then(context -> {
            throw failure;
        });
    }

    public int calls() {
        return generateCalls + repairCalls;
    }

    @Override
    public GeneratedNarrative generate(ALevelPaidNarrativeContext context) {
        generateCalls++;
        return answer(context);
    }

    @Override
    public GeneratedNarrative repair(ALevelPaidNarrativeContext context, RepairFeedback feedback) {
        repairCalls++;
        lastFeedback = feedback;
        return answer(context);
    }

    private GeneratedNarrative answer(ALevelPaidNarrativeContext context) {
        var next = answers.isEmpty() ? null : answers.poll();
        if (next == null) {
            throw new IllegalStateException("no scripted answer left");
        }
        return new GeneratedNarrative(next.apply(context), Optional.of("req_scripted"), Optional.of("scripted-model"));
    }

    @Override
    public String providerIdentifier() {
        return "scripted";
    }

    @Override
    public Optional<String> modelIdentifier() {
        return Optional.of("scripted-model");
    }

    /** Validated prose for any context: no numbers, bands, claims or rankings. */
    public static ALevelPaidNarrative cleanNarrative() {
        return new ALevelPaidNarrative(
                "Your subject profile describes the combination you are taking, part by part.",
                "The two sides of your capability profile show different kinds of strength, side by side.",
                "Your subjects support further study in several directions; check each course's published "
                        + "entry requirements before you apply.",
                "AI may help you practise, explore ideas and check your understanding as you study.",
                "The plan below turns this into steps for now, the rest of sixth form and your applications.");
    }

    /** Different valid prose, to show a stored report ignores a later provider. */
    public static ALevelPaidNarrative differentNarrative() {
        return new ALevelPaidNarrative("A different profile view.", "A different capability view.",
                "A different options view.", "A different AI learning view.", "A different plan introduction.");
    }
}
