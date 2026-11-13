# OpenAI Decisions primary-provider migration

The explicit provider switch is `app.judgment.provider`, exposed as `JUDGMENT_PROVIDER`. Its default is `openai`.
Set it to `jev` to select the contingency provider deliberately. A failed OpenAI request is never retried through
Jev and never replaced with fixture data.

Implemented OpenAI adapters:

- `professional-v1`
- `university-v1`
- `a-level-v2`
- `professional-responsibility-v1`
- `university-module-v1`

All adapters stop at atomic judgments. Existing Java policies still own weights, thresholds, bands and final
classifications. Existing schema versions, question identifiers, instructions and ordered level descriptions are
unchanged. Jev adapters and historical provenance values remain available.

## Calibration

`openai-primary-calibration-v1.json` covers clear low, moderate and high examples, boundaries, missing information,
ambiguity, contradictions, repeated identical inputs and semantically equivalent wording for University and
A-level v2. Acceptance is based on defensible atomic judgments and stable downstream Java outcomes, not equality
with Jev probabilities. A live run should record each raw response, final Java classification, refusal/failure,
latency and token/cost data, compare repeat pairs and semantic-variant pairs, and flag any boundary crossing for
review. Live calls remain explicitly opt-in; ordinary Maven tests use scripted DecisionModel responses only.

The current catalog is an evaluation input, not a fixture fallback and not an assertion that a particular
probability is universally correct. Cases near a Java threshold must be reviewed at the final classification level.
