# AI Career Impact architecture

AI Career Impact is intended to remain a modular monolith. Its journeys share delivery and operational infrastructure, but each journey keeps its own inputs and decision policy.

## Assessment flow

```text
User input
    ↓
Journey-specific AssessmentState
    ↓
Jev atomic judgments
    ↓
Java journey-specific decision policy
    ↓
AssessmentDecision
    ↓
OpenAI explanation
    ↓
Jev verification for premium reports
    ↓
User-facing result/report
```

Jev supplies small, explicit judgments rather than an overall business decision. Deterministic Java policies combine those judgments using journey-specific weights, thresholds, and rules. OpenAI turns the resulting decision and supporting evidence into accessible narrative, but does not determine the authoritative score. Premium reports add a final Jev verification step before presentation.

External facts, including salaries and labour-market statistics, must come from an evidence layer with traceable sources rather than from model memory.

## Journeys and decision semantics

- **Professional** assesses how AI may affect a person's current work: task exposure, durable value, and adaptability. Its indicators describe the current role and the person's ability to respond.
- **University** assesses how a course supports future readiness and how its possible career paths may be exposed to AI. A high or low value here cannot be interpreted as the same kind of risk as a professional assessment.
- **A-level** assesses whether a subject combination preserves useful future options, given the student's interests and strengths. Its central idea is optionality, not present-day job exposure.

The journeys therefore share the `AssessmentDecision` result shape, but not a universal meaning for its indicator. Each Java decision policy will define its own dimensions, aggregation, labels, and thresholds.

## Intended modules

- `assessment`: journey inputs and assessment lifecycle.
- `judgment`: atomic judgment contracts and later Jev adapters.
- `decision`: deterministic, journey-specific Java policies.
- `narrative`: explanation generation from authoritative decisions and evidence.
- `evidence`: sourced external facts and provenance.
- `career`: career and occupation concepts shared where their meaning is genuinely common.
- `report`: report assembly, premium verification, and presentation models.
- `payment`: payment and entitlement workflows.
- `analytics`: product and assessment analytics.
- `security`: identity, access, and data-protection concerns.
- `configuration`: application wiring and external-client configuration.

Packages should be introduced only when they contain real code. Module boundaries should remain explicit without creating empty packages or separate deployable services prematurely.

## Professional decision pipeline

```text
ProfessionalAssessmentState
    ↓
Jev professional-v1
    ↓
AssessmentJudgmentSet
    ↓
ProfessionalDecisionPolicyV1
    ↓
AI Exposure | Human Advantage | AI Complementarity | Role Adaptability
    ↓
AssessmentDecision
```

`ProfessionalDecisionPolicyV1` is deterministic Java policy. It validates the atomic judgments, normalises their 0–4 values, applies explicit weights, assigns indicator bands, derives confidence and status, and selects structured explanation factors. It does not call Jev or OpenAI.

The weights and thresholds in `professional-decision-v1` are transparent V1 product policy. They are not claimed to be scientifically validated universal truths. Changing them requires a new policy version so an existing version never silently changes meaning.

AI Exposure describes how readily the current work may be performed, automated, or materially changed by AI. It is not a probability of job loss. Human Advantage is separately calculated because a role can combine meaningful AI exposure with strong reasons for continued human involvement. AI Complementarity describes potential augmentation rather than automation exposure.

Role Adaptability describes characteristics of the role that support evolution toward higher-value, AI-assisted work. The current schema does not assess personal motivation, willingness to retrain, learning ability, or financial flexibility, so this measure is not a complete assessment of personal career adaptability.
