# OpenAI Decisions migration — Phase 2 professional adapter

Date: 12 November 2026
Branch: `feat/change-decision-model-jev-to-openai`

## Scope and production status

This slice implements the professional assessment adapter and calibration tooling only. The production default is
still Jev. No decision formula, weight, threshold, report template, narrative prompt or persisted schema was changed.
The University, A-level, responsibility-analysis and module-analysis providers remain Jev-only.

OpenAI remains behind two explicit settings so an incomplete configuration fails instead of silently changing
provider or falling back:

```text
JUDGMENT_PROVIDER=openai
SPRING_AI_MODEL_DECISION=openai
OPENAI_API_KEY=...
```

Normal configuration resolves the professional provider to `jev` and sets the Spring AI Decision Model to `none`.

## Provider-independent contract

`com.chikere.futureai.judgment.professional.schema` now owns `professional-v1`: the version, nine question IDs,
instructions, predicate criteria, ordered score levels and professional evidence-state mapping. The Jev and OpenAI
representations are adapters over that schema. Parity tests compare every Jev question field with the canonical
definition.

The existing `ProfessionalJudgmentProvider`, `AssessmentJudgmentSet` and `DimensionJudgment` contracts remain the
application boundary. The existing `ProfessionalDecisionPolicyV2` remains the only owner of scores, weights,
thresholds, status, confidence and classifications.

## OpenAI mapping and validation

- `information_sufficient` maps to an OpenAI predicate. Its probability of yes is preserved as the existing numeric
  information-sufficiency input; the OpenAI thresholded boolean is not used for scoring.
- Each 0–4 question maps to an ordered score with labels `0` through `4` and the exact canonical level descriptions.
- OpenAI's expected score, nearest level, confidence and five-value distribution remain distinct. Only the expected
  score and confidence enter the unchanged Java policy, just as required by the existing domain contract.
- Jev and OpenAI values are not compared for numeric equality and no conversion factor is applied.
- Missing, unexpected, null, refused, wrong-type, non-finite, out-of-range and malformed-distribution results fail the
  provider call. There is no fixture/default substitution.

The Spring-managed adapter uses the auto-configured `DecisionModel`, retaining Spring AI Decision Model observation
support. The existing trace and provider-cost infrastructure records the OpenAI call and provider-reported usage.

## Historical provenance

`AssessmentSource.OPENAI` was added for new OpenAI snapshots. Existing database rows containing `JEV`, their model
identifiers, judgments, schema versions and deterministic results remain unchanged and readable. No migration
rewrites historical assessments.

## Offline verification

The test suite covers:

- canonical/Jev question parity;
- all-nine-question OpenAI domain mapping;
- strict refusal and malformed-response failure;
- explicit Jev-vs-OpenAI production wiring;
- a loopback contract request to `POST /v1/decisions` with all nine questions followed by the unchanged
  `professional-decision-v2` policy;
- calibration materiality rules and required missing-evidence, ambiguous, contradictory and boundary-focused cases.

## Calibration release gate

The opt-in calibration makes one Jev and one OpenAI request for each selected case, runs both judgment sets through
the same `ProfessionalDecisionPolicyV2`, and writes
`target/professional-decision-calibration/professional-v1-jev-openai.md`.

```text
OPENAI_API_KEY=... TYPESAFE_API_KEY=... \
  ./mvnw verify -Plive-professional-decision-calibration
```

For an incremental run, add `-Dprofessional.calibration.case=<case-id>`. To use an approved, sanitized export of
historical assessment inputs instead of the checked-in fictional evaluation set, add
`-Dprofessional.calibration.dataset=/absolute/path/to/professional-cases.json`. The JSON has the same input shape as
`src/test/resources/evaluations/professional-v1-cases.json`; the harness reads it without updating any stored
assessment. The report treats the following as material:

- assessment status, confidence or headline changes;
- a measure becoming available/unavailable;
- a measure crossing LOW/MODERATE/HIGH boundaries;
- a measure moving by at least 0.50 on the 0–10 scale;
- different displayed exposure drivers or protective factors.

Raw probability, expected-score and confidence differences are retained for inspection but are not themselves
calibration failures.

Neither provider key nor an approved historical-case export was available in the implementation environment, so the
comparative live calibration has not yet run. The checked-in cases exercise representative roles plus missing,
ambiguous, contradictory and boundary-focused inputs, but they are fictional and are not a substitute for historical
customer cases. Consequently, no evidence-backed claim about Jev/OpenAI customer-facing outcome differences can yet
be made, and OpenAI must not be activated for production until the historical comparison report is reviewed and
accepted.
