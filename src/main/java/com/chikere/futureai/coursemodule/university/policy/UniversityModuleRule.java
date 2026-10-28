package com.chikere.futureai.coursemodule.university.policy;

/** The stable ids of the {@code university-module-policy-v1} rules, in the order they are evaluated. */
public enum UniversityModuleRule {
    /** Practical application is HIGH and leads human and technical demands by the dominance margin. */
    UM1_PRACTICAL_DOMINANCE,
    /** Practical and interpersonal work are both HIGH and close, and practical clearly leads technical depth. */
    UM1B_HANDS_ON_WITH_PEOPLE,
    /** max(human judgement, interpersonal work) is HIGH and leads practical and technical by the margin. */
    UM2_HUMAN_JUDGEMENT_DOMINANCE,
    /** Technical/quantitative depth is HIGH and leads practical and human demands by the margin. */
    UM3_TECHNICAL_CORE,
    /** No characteristic dominates; AI learning assistance and independent demonstration are both HIGH. */
    UM4_AI_WITH_INDEPENDENT_DEMONSTRATION,
    /** No characteristic is HIGH; AI learning assistance is HIGH and independent demonstration is not. */
    UM5_AI_ASSISTED_LEARNING,
    /** Nothing above applies. */
    UM6_MIXED
}
