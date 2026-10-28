package com.chikere.futureai.narrative.university.paid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Function;

/** A paid-narrative provider that answers from a script and counts every call. Never contacts OpenAI. */
public final class ScriptedPaidNarrativeGenerator implements UniversityPaidNarrativeGenerator {

    private final Deque<Function<UniversityPaidNarrativeContext, UniversityPaidNarrative>> answers = new ArrayDeque<>();
    public int generateCalls;
    public int repairCalls;
    public RepairFeedback lastFeedback;

    /** Queues the next answer; a {@code null} result is returned as-is, and a thrown exception is propagated. */
    public ScriptedPaidNarrativeGenerator then(Function<UniversityPaidNarrativeContext, UniversityPaidNarrative> answer) {
        answers.add(answer);
        return this;
    }

    public ScriptedPaidNarrativeGenerator then(UniversityPaidNarrative answer) {
        return then(context -> answer);
    }

    public ScriptedPaidNarrativeGenerator failing(RuntimeException failure) {
        return then(context -> {
            throw failure;
        });
    }

    public int calls() {
        return generateCalls + repairCalls;
    }

    @Override
    public GeneratedNarrative generate(UniversityPaidNarrativeContext context) {
        generateCalls++;
        return answer(context);
    }

    @Override
    public GeneratedNarrative repair(UniversityPaidNarrativeContext context, RepairFeedback feedback) {
        repairCalls++;
        lastFeedback = feedback;
        return answer(context);
    }

    private GeneratedNarrative answer(UniversityPaidNarrativeContext context) {
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

    /** Validated prose for any context: no numbers, bands or claims. */
    public static UniversityPaidNarrative cleanNarrative() {
        return new UniversityPaidNarrative(
                "Your course profile describes the course as you outlined it, measure by measure.",
                "AI may help you prepare, explore ideas and check your understanding as you study.",
                "Much of the course asks you to apply your own judgement and practical skill.",
                "The reasoning and communication you build here carry into many settings.",
                "The plan below turns these findings into steps for now, the coming months and later study.");
    }

    /** Different valid prose, to show a stored report ignores a later provider. */
    public static UniversityPaidNarrative differentNarrative() {
        return new UniversityPaidNarrative("A different overall perspective.", "A different AI learning view.",
                "A different human view.", "A different transferable view.", "A different plan introduction.");
    }
}
