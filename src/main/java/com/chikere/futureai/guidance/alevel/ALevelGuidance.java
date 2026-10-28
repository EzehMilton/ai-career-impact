package com.chikere.futureai.guidance.alevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The deterministic {@code a-level-guidance-policy-v1} content of an A-level paid report: decided by Java from the
 * frozen {@code a-level-decision-v2} subject profile and the student's guidance-only context. It never changes a
 * score, band or factor, and no model writes or chooses any of it. Every item names its provenance.
 */
public record ALevelGuidance(
        String policyVersion,
        List<Item> combinationShape,
        List<Item> strengths,
        List<Item> areasToStrengthen,
        Options options,
        AiStudy aiStudy,
        List<Item> skillPriorities,
        ActionPlan actionPlan,
        List<Item> safeguards) {

    public ALevelGuidance {
        Objects.requireNonNull(policyVersion);
        combinationShape = List.copyOf(combinationShape);
        strengths = List.copyOf(strengths);
        areasToStrengthen = List.copyOf(areasToStrengthen);
        Objects.requireNonNull(options);
        Objects.requireNonNull(aiStudy);
        skillPriorities = List.copyOf(skillPriorities);
        Objects.requireNonNull(actionPlan);
        safeguards = List.copyOf(safeguards);
    }

    /**
     * One piece of guidance.
     *
     * @param provenance the frozen dimensions, measures or guidance-only inputs it was decided from
     */
    public record Item(String id, String title, String text, List<String> provenance) {
        public Item {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(text);
            provenance = List.copyOf(provenance);
            if (provenance.isEmpty()) {
                throw new IllegalArgumentException("guidance item " + id + " must name its provenance");
            }
        }
    }

    /**
     * How to read Options Kept Open: what the assessment can say, what it cannot, and the checks the student's own
     * preferences call for. It never states an entry requirement, a grade, an acceptance or an accreditation rule.
     */
    public record Options(List<String> canSay, List<String> cannotSay, List<Item> checks,
            Optional<String> gradesNote) {
        public Options {
            canSay = List.copyOf(canSay);
            cannotSay = List.copyOf(cannotSay);
            checks = List.copyOf(checks);
            Objects.requireNonNull(gradesNote);
        }
    }

    /** Ways to use AI that support learning, always with the caution; depth follows Learning Alongside AI. */
    public record AiStudy(String caution, List<Item> uses) {
        public AiStudy {
            Objects.requireNonNull(caution);
            uses = List.copyOf(uses);
        }
    }

    /** Three horizons: now, the rest of sixth form, and applications and next-step decisions. */
    public record ActionPlan(List<Item> now, List<Item> sixthForm, List<Item> applications) {
        public ActionPlan {
            now = List.copyOf(now);
            sixthForm = List.copyOf(sixthForm);
            applications = List.copyOf(applications);
        }
    }
}
