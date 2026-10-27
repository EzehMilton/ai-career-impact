# Live smoke testing

> **Never use `live-smoke` as a deployment profile.** It makes real, billable Jev and OpenAI calls, writes full
> assessment inputs and AI prompts and responses to the logs, and unlocks detailed reports without payment.

## Purpose

A fast, repeatable end-to-end check of the Professional, University and A-level journeys against the real
providers. Known inputs are loaded into the normal forms with one click, so nothing has to be retyped and no
Stripe checkout is needed to reach the detailed report and PDF.

## What is real and what is bypassed

| Part | Under `live-smoke` |
|---|---|
| Jev / TypeSafe judgments (all three journeys) | **Real** (`*JevJudgmentService`) |
| OpenAI narratives (all three journeys) | **Real** (`OpenAi*NarrativeGenerator`); no fixture fallback exists |
| Decision policies, narrative validation, reports, PDFs | **Real**, unchanged |
| PostgreSQL persistence | **Real** (`compose.yaml` via `spring-boot-docker-compose`, or your own datasource) |
| Security, CSRF, Bean Validation, form mapping | **Real**, unchanged |
| Stripe | **Bypassed.** No payment gateway, checkout or webhook bean exists |
| Professional external evidence and occupation classification | **Not active** (the report says external information was unavailable) |
| Assessment tracing | **On by default**, every event labelled `"executionMode": "LIVE_SMOKE"` |

### How the payment bypass works

After each submission completes through the journey's normal workflow, the live-smoke decorator grants
detailed-report access through the **existing complimentary entitlement path**: an `ACTIVE` entitlement with
provider `COMPLIMENTARY` and the frozen PAID report snapshot, exactly what a redeemed access code produces. No
access code is typed, Stripe is never called, no webhook is simulated and no Stripe payment ID is written. The
paid-report and PDF controllers are unchanged and still require an `ACTIVE` entitlement. Granting is
idempotent: reopening a result never creates a second entitlement.

## Required environment

| Variable | Purpose |
|---|---|
| `APP_LIVE_SMOKE_ENABLED=true` | Explicit switch. Without it the profile refuses to start |
| `TYPESAFE_API_KEY` | Real Jev / TypeSafe calls |
| `OPENAI_API_KEY` | Real OpenAI narratives |
| `OPENAI_MODEL` (optional) | Defaults to `gpt-4o-mini` |
| `ASSESSMENT_TRACE_ENABLED` (optional) | Defaults to `true` here; set `false` to turn tracing off |
| `APP_LIVE_SMOKE_TRACE_DIRECTORY` (optional) | Trace file directory; defaults to `.live-smoke/traces` |

No Stripe or evidence variables are needed. Docker must be running if you rely on `compose.yaml` for PostgreSQL.

Startup is refused with a clear message if the switch or either key is missing, or if `live-smoke` is combined
with `prod`, `dev` or `dev-no-jev`, for example:

```
live-smoke profile requires APP_LIVE_SMOKE_ENABLED=true (real Jev and OpenAI calls are made and payments are bypassed)
```

## Start

```
APP_LIVE_SMOKE_ENABLED=true \
TYPESAFE_API_KEY=... \
OPENAI_API_KEY=... \
./mvnw spring-boot:run -Dspring-boot.run.profiles=live-smoke
```

The startup log lists what this run is wired to (`LIVE SMOKE TEST MODE Jev: LIVE …`, `Stripe: BYPASSED …`) and
`Assessment tracing enabled for LIVE SMOKE TEST MODE`, followed by the standard warning that trace logs contain
user-entered data and complete AI prompts and responses.

## Use

1. Open **http://localhost:8080/dev/live-smoke**. Check the status table: Jev LIVE, OpenAI LIVE, Stripe BYPASSED,
   Assessment trace ENABLED, and the database.
2. Choose **Open pre-filled form** for a saved case, or **New custom … assessment** for a journey's normal blank
   form. Either only opens the form; nothing is submitted.
3. Review, edit or type the inputs and submit the form as usual. A custom assessment gets exactly the same
   behaviour as a saved case: real providers, detailed-report access, PDF and trace.
4. On the result page, the live-smoke panel offers **Detailed Report**, **Download PDF**, **Download Trace**,
   **Run Again** (unedited saved cases only) and **Back to Live Smoke Dashboard**.

All three journeys have a normal paid PDF, rendered by their usual renderer; there is no smoke-specific PDF.

For a Professional result the panel also offers **Prepare detailed report v3**, which posts to the customer's
normal `/professional-assessment/{id}/report/prepare` route: real responsibility Jev call, deterministic guidance,
real paid-narrative OpenAI call, then the stored professional-report-v3 and its v5 PDF. Each step is stored once;
preparing again reuses everything. **Detailed report v3 snapshot (JSON)** shows what was assembled, including the
paid narrative status, and the v3 page shows a small live-smoke line with the versions and narrative status. To
exercise the more-than-eight selection screen, submit a custom Professional assessment with nine or more
responsibilities and open its Detailed Report.

## Trace files

Each assessment's trace is written to `.live-smoke/traces/{assessmentId}.jsonl`, one JSON object per line.
The lines are the same events the console trace shows (form input, Jev request and response, information
sufficiency, confidence, every score calculation, factor selection and materiality, persistence, the OpenAI
request and response, narrative validation and any repair, evidence and classification, report assembly and the
timing summaries), serialised and redacted by the same code. They come from a sink on the existing
`AssessmentTrace`, not a second tracing implementation. A final `LIVE_SMOKE_METADATA` line records `smokeCaseId`:
the saved case id, or `CUSTOM`. Viewing the detailed report later appends its report events to the same file.

**Download Trace** returns only that assessment's file, named `{smokeCaseId|custom}-{assessmentId}.jsonl`. The
route accepts nothing but a UUID and resolves the file only inside the trace directory.

Trace files are sensitive: they contain the submitted inputs, prompts, judgments, model responses and narratives.
Credentials are never traced, and as a second safeguard every configured secret value (OpenAI and TypeSafe keys,
datasource password, Stripe secrets, complimentary code) is replaced with `[REDACTED]` before a line reaches
disk. The files live under `.live-smoke/` in the working directory, which Git ignores and `./mvnw clean` leaves
alone, so traces survive rebuilds; delete the folder yourself to remove them. They are never stored in the
database.

Every page shows the red **LIVE SMOKE TEST MODE** banner. Neither the banner, the panel nor any `/dev/live-smoke`
route exists outside this profile.

## Cases

| Journey | Case | Source |
|---|---|---|
| Professional | Registered Nurse | Agreed live regression inputs |
| Professional | Java Software Developer | Agreed live regression inputs |
| Professional | Management Accountant | Agreed live regression inputs |
| University | BSc Computer Science | `university-example.html` |
| University | Economics | `UniversityJevJudgmentServiceTest` |
| University | History | `UniversityJevStateMapperTest` |
| A-level | Mathematics, Economics and Geography | `a-level-example.html` |
| A-level | Mathematics and Physics | `ALevelJevStateMapperTest` |
| A-level | English Literature, History and Politics (subjects only) | `ALevelAssessmentFormMapperTest` |

The Economics and History inputs are short, so they exercise thin-input behaviour rather than a full course
description.

## Adding a case

Add one `LiveSmokeCase` entry to `LiveSmokeCaseCatalog.CASES` (`src/main/java/.../livesmoke`). Field names are
the journey form's property names, and list fields use one line per item, as in the form. The dashboard, the
prefill route and `LiveSmokeModeTest` (which checks every case passes the normal validation) pick it up
automatically. Use only genuine inputs and record where they came from in `source`.

## Provenance

Smoke assessments are not marked in the database. That would need a migration on three journey tables for a
local-only tool. They can be told apart by the trace label `executionMode: LIVE_SMOKE`, the
`LIVE SMOKE detailed-report access … executionMode=LIVE_SMOKE` log line, and their `COMPLIMENTARY` entitlement.
Run smoke tests against a local or disposable database, never a shared or production one.

## Paid narrative live evaluation

`ProfessionalPaidNarrativeLiveIT` runs professional-paid-narrative-v1 against real OpenAI for the seven calibration
roles, using the captured responsibility analyses (no Jev call), real guidance, validation and repair, report-v3
assembly and the v5 PDF. It is never part of `./mvnw verify`:

```
OPENAI_API_KEY=... ./mvnw -Plive-paid-narrative verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
```

Results (per-role status, repair, failed rules, call and preparation timings, PDFs and the narrative text) are written
to `.live-smoke/paid-narrative/<timestamp>/`. Add `-Dopenai.paid.narrative.live.dry-run=true` to check the wiring
without any call. `OPENAI_MODEL` selects the model (default `gpt-4o-mini`); a GPT-6 model such as `gpt-6-luna` is
sent reasoning effort `none` and no temperature. The four non-Stage-2 roles use placeholder role-level measures; their narratives explain those
placeholder scores, so review them for their responsibility-level content.
