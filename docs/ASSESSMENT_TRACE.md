# Assessment trace

An opt-in diagnostic log that follows one assessment from the submitted form to the report a visitor sees:
what the form sent, what the decision model was asked and returned, how Java calculated every score and
chose every factor, what the narrative model was given and wrote, which guardrails it passed, what evidence
was found, how a paid detailed report was prepared, and how long each step took.

> **Assessment tracing may include user-entered assessment data and complete AI prompts/responses. It is
> intended for controlled development and diagnostic use and should not be enabled routinely in production.**

It is observability only. It changes no score, weight, threshold, band, prompt, schema, guardrail,
persistence behaviour, provider call or page.

## Switching it on and off

| Property | Environment variable | Default |
| --- | --- | --- |
| `app.assessment-trace.enabled` | `ASSESSMENT_TRACE_ENABLED` | `false` |

```bash
# Enable locally
export ASSESSMENT_TRACE_ENABLED=true

# Disable
unset ASSESSMENT_TRACE_ENABLED
# or
export ASSESSMENT_TRACE_ENABLED=false
```

The flag alone controls it, under every profile: `dev` (fixture decision and narrative models), `dev-no-jev`
(fixture decision model, OpenAI narratives) and `prod` (Jev and OpenAI). The only profile that changes the default
is the local `live-smoke` profile, where it is on and every event is labelled `"executionMode": "LIVE_SMOKE"` (see
[LIVE_SMOKE_TESTING.md](LIVE_SMOKE_TESTING.md)). Startup logs a `WARN` when it is on.

When it is off, every trace call returns immediately: no payload is built, nothing is serialised, nothing is
logged, and the narrative trace advisor is not even registered.

## Reading the output

Every event is one `INFO` entry from the `com.chikere.futureai.trace.AssessmentTrace` logger: a header line,
then the event as pretty-printed JSON.

```text
[ASSESSMENT-TRACE] SCORE_CALCULATION journey=PROFESSIONAL assessmentTraceId=a15578e1-… assessmentId=pending
{
  "stage" : "SCORE_CALCULATION",
  "journey" : "PROFESSIONAL",
  "assessmentTraceId" : "a15578e1-…",
  "assessmentId" : null,
  "sequence" : 8,
  "at" : "…",
  "payload" : { … }
}
```

- `assessmentTraceId` groups every event of one submission, starting at the form. It is never shown to visitors.
- `assessmentId` is `pending` until the assessment is saved, and the persisted UUID from `PERSISTENCE` onwards.
- `sequence` orders the events of one trace.
- Under `prod` (ECS JSON) the header fields are also emitted as structured key-values `assessmentTraceId`,
  `assessmentId`, `journey` and `traceStage`, alongside the usual `requestId`.

To follow one assessment: `grep` for its `assessmentTraceId`, or for its `assessmentId` to include the report
views, which happen on later requests (see below).

## Stages

A submission emits, in order:

| Stage | Emitted by | What it shows |
| --- | --- | --- |
| `START` | form mapper or workflow | trace opened; request id |
| `FORM_INPUT` | `*AssessmentFormMapper` | the form exactly as submitted |
| `DOMAIN_INPUT` | `*AssessmentFormMapper` | the domain state after trimming, splitting and mapping, and `formToDomainMs` |
| `FORM_MAPPING_FAILED` | `AssessmentTrace` | the form could not be mapped to a domain state, and why |
| `DECISION_MODEL_REQUEST` | `*AssessmentService` | provider (`JEV` or `LOCAL_FIXTURE`), model, fixture id, the state sent |
| `DECISION_MODEL_PROVIDER_REQUEST` | `*JevJudgmentService`, `ALevelV2JevJudgmentService` (Jev only) | the exact System One call: wire state, every question with its instructions and level criteria, model, timeout, retry policy |
| `DECISION_MODEL_PROVIDER_RESPONSE` | `*JevJudgmentService`, `ALevelV2JevJudgmentService` (Jev only) | raw answers before mapping (value, nearest level and label, confidence, probabilities), model, request id, tokens, timing and outcome |
| `DECISION_MODEL_RESPONSE` | `*AssessmentService` | every judgment as the policy receives it, timing and `SUCCEEDED` / `FAILED` / `TIMED_OUT` |
| `INFORMATION_SUFFICIENCY` | the decision policy | value, thresholds, resulting status and confidence, whether scores were produced or suppressed |
| `ASSESSMENT_CONFIDENCE` | the decision policy | mean and minimum judgment confidence against the thresholds (per measure under the v2 student policies) |
| `SCORE_CALCULATION` | the decision policy | one per measure: each input's raw score, transformation, weight and contribution, the weighted total, unrounded score, band and thresholds, measure confidence |
| `FACTOR_SELECTION` | the decision policy | every candidate's signed effect, sign, materiality, eligibility, rank, and why it was or was not displayed |
| `DETERMINISTIC_ASSESSMENT` | `*AssessmentService` | the complete result Java decided, with provenance: the boundary before any narrative AI |
| `PERSISTENCE` | `*AssessmentService` | saved assessment id and save time |
| `NARRATIVE_REQUEST` | `*NarrativeService` (Professional, and v1 student assessments) | purpose, provider, model, prompt/schema version and the full context given to the model |
| `NARRATIVE_PROVIDER_REQUEST` | `AssessmentTraceAdvisor` (OpenAI only) | the exact system and user messages, model, output JSON schema |
| `NARRATIVE_PROVIDER_RESPONSE` | `AssessmentTraceAdvisor` (OpenAI only) | raw response text before mapping, model, tokens, timing |
| `NARRATIVE_RESPONSE` | `*NarrativeService` | the typed narrative as returned, timing and outcome |
| `NARRATIVE_VALIDATION` | `*NarrativeValidator` | each guardrail: passed, failed (with reason and triggering text) or not evaluated |
| `NARRATIVE_REPAIR_REQUEST` / `NARRATIVE_OUTCOME` | `ProfessionalNarrativeService` | the one repair request after a failed draft, and the final outcome |
| `OCCUPATION_CLASSIFICATION` | Professional only | classifier, request and SOC result, or why it was skipped |
| `EVIDENCE_REQUEST` / `EVIDENCE_RESPONSE` | `*EvidenceService` | provider, request (including SOC code), the returned bundle and timing, or why it was skipped |
| `SUMMARY` | `*AssessmentWorkflow` | outcome and every measured timing |

A University or A-level `decision-v2` submission has no free narrative, so it emits no `NARRATIVE_*` stages.

Preparing a detailed report (`POST /{journey}/{id}/report/prepare`) emits, under its own trace joined by
`assessmentId`:

| Stage | What it shows |
| --- | --- |
| `RESPONSIBILITY_ANALYSIS_REQUEST`, `RESPONSIBILITY_MODEL_PROVIDER_REQUEST` / `_RESPONSE`, `RESPONSIBILITY_ANALYSIS_RESULT`, `RESPONSIBILITY_CLASSIFICATION`, `RESPONSIBILITY_ANALYSIS_PERSISTED` / `_REUSED` / `_FAILED` | Professional: the selected responsibilities, the Jev call, the validated judgments, the deterministic classification and the stored, reused or failed snapshot |
| `RESPONSIBILITY_GUIDANCE` | Professional: guidance generated, reused or returned from a concurrent request, with rule ids |
| `UNIVERSITY_MODULE_ANALYSIS_REQUEST` / `_DENIED` / `_REUSED` / `_PERSISTED` / `_FAILED` | University: the selected module ids, a refusal before any provider call and why, or the reused, stored or failed snapshot |
| `PAID_NARRATIVE_REQUEST`, `PAID_NARRATIVE_RESPONSE`, `PAID_NARRATIVE_VALIDATION`, `PAID_NARRATIVE_REPAIR`, `PAID_NARRATIVE_FALLBACK`, `PAID_NARRATIVE_REUSED` | All journeys: the compact provider view of the frozen findings, each response (attempt 1, or 2 for the repair), every validation rule's outcome, the repair request, a fallback and why, or a stored narrative returned without a call |

Report views emit, each under its own trace joined by `assessmentId`:

| Stage | What it shows |
| --- | --- |
| `FREE_ASSESSMENT_COMPLETE` | the structured free report exactly as it feeds the result page |
| `DETAILED_REPORT_COMPLETE` | the structured detailed report that feeds the HTML report and the PDF (never PDF bytes) |
| `REPORT_SUMMARY` | `reportAssemblyMs` and `totalReportMs` |

## The calculation comes from the policy itself

The decision policies compute each measure from a list of `WeightedContribution` records and sum those same
records. The trace prints that list, so there is one scoring implementation and the trace cannot disagree
with the score. `DecisionPolicyTraceTest` checks every fixture of every journey: tracing changes no
result, each logged raw score is the decision model's actual judgment, each contribution is
`weight × transformed value`, the contributions sum to the weighted total, and `weighted total × 10` is the
stored score.

Professional AI Exposure for the `senior_software_architect` fixture:

| Judgment | Raw | Transformation | Value | Weight | Contribution |
| --- | --- | --- | --- | --- | --- |
| task_repeatability | 0.57 | normalise: score / 4 | 0.1425 | 0.25 | 0.035625 |
| digital_executability | 3.71 | normalise: score / 4 | 0.9275 | 0.25 | 0.231875 |
| human_trust_dependency | 2.85 | inverse: 1 - score / 4 | 0.2875 | 0.15 | 0.043125 |
| physical_world_dependency | 0.02 | inverse: 1 - score / 4 | 0.995 | 0.15 | 0.14925 |
| contextual_complexity | 3.63 | inverse: 1 - score / 4 | 0.0925 | 0.10 | 0.00925 |
| original_judgment | 3.55 | inverse: 1 - score / 4 | 0.1125 | 0.07 | 0.007875 |
| accountability_responsibility | 3.46 | inverse: 1 - score / 4 | 0.135 | 0.03 | 0.00405 |

Weighted total 0.48105, score 4.8105, band MODERATE (3.5 ≤ score < 7.0), measure confidence 0.85.

## Timings

`SUMMARY` lists only what was actually measured: `formToDomainMs`, `decisionModelMs`, `decisionPolicyMs`,
`persistenceMs`, `narrativeModelMs`, `occupationClassificationMs` (Professional), `evidenceMs` and
`totalAssessmentMs`. Provider events carry their own `startedAt`, `completedAt` and `durationMs`, measured
around the call itself.

Stage timings exclude the time the trace spends writing its own events, reported separately as
`traceWritingMs`. Without that, the decision policy, which emits several events while it runs, would look
slower than it is. `totalAssessmentMs` is wall-clock time and includes trace writing.

## What is never logged

API keys and credentials (OpenAI, Jev/TypeSafe, Stripe, webhook secrets, database), authentication
headers, cookies, payment card or checkout data, complimentary access codes, environment secrets and PDF
bytes. Nothing in the trace reads client configuration beyond model names, timeouts and retry settings.
Every rendered event also passes through a redactor that masks credential-shaped text (`sk-…`,
`sk_live_…`, `whsec_…`, `Bearer …`, `api-key=…`, `password: …`) in case one ever appears in a provider's
exception message. `AssessmentTraceJourneyTest` configures recognisable credentials and asserts that none of
them appears anywhere in a full trace.

## Known limits

- **Jev's prompt is not visible.** System One is a structured API: the request is the state map plus the
  questions, logged in full, and TypeSafe builds the model prompt server-side. Jev returns values,
  confidence, probabilities and the nearest level label, but no rationale text, so none can be logged.
- **OpenAI is observed at the Spring AI boundary.** The advisor logs the messages, options and JSON schema
  Spring AI sends and the raw text it receives, not the HTTP body. A retry inside the OpenAI client counts
  as one timed call.
- **Malformed narrative output** fails when Spring AI maps it onto the narrative record (missing fields, or
  practical actions outside 3–5). It appears as `NARRATIVE_RESPONSE` with `status=FAILED` and the error,
  not as a `NARRATIVE_VALIDATION` rule.
- **The detailed report has its own AI call.** The paid narrative is a separate OpenAI call made when the customer
  prepares the detailed report, traced with the `PAID_NARRATIVE_*` stages above, not as part of the submission.
- **Report views have their own trace id.** The free result, the detailed report, the purchase snapshot and
  the PDF are each assembled on a later request, so they are joined to the submission by `assessmentId`.
  The submission's trace id is not persisted, because that would need a schema change.
- **Display rounding is not traced.** Scores are logged unrounded, as stored. Pages and the PDF round them
  in the view layer.
- **University and A-level evidence** comes only from the local fixture providers (`dev`, `dev-no-jev`). Where no
  provider is configured, as under `prod` and `live-smoke`, `EVIDENCE_RESPONSE` says `SKIPPED` rather than implying
  evidence exists.
- **Bean Validation failures** stop at the controller before the form mapper runs, so a rejected form leaves
  no trace.
