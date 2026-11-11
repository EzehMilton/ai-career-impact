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
| Jev / TypeSafe judgments (all three journeys) | **Real** (`*JevJudgmentService`, `ALevelV2JevJudgmentService`) |
| Paid Jev analysis (Professional responsibilities, University modules) | **Real**, when a detailed report is prepared |
| OpenAI narratives, free and paid | **Real** (`OpenAi*NarrativeGenerator`, `OpenAi*PaidNarrativeGenerator`); no fixture fallback exists. University and A-level v2 assessments have no free narrative, so their only OpenAI call is the paid narrative |
| Decision policies, guidance policies, narrative validation, reports, PDFs | **Real**, unchanged |
| PostgreSQL persistence | **Real** (`compose.yaml` via `spring-boot-docker-compose`, or your own datasource) |
| Security, CSRF, Bean Validation, form mapping | **Real**, unchanged |
| Stripe | **Bypassed.** No payment gateway, checkout or webhook bean exists |
| External evidence and occupation classification (all journeys) | **Not active** (the report says external information was unavailable) |
| Assessment tracing | **On by default**, every event labelled `"executionMode": "LIVE_SMOKE"` |

### How the payment bypass works

After each submission completes through the journey's normal workflow, the live-smoke decorator grants
detailed-report access through the **existing complimentary entitlement path**: an `ACTIVE` entitlement with
provider `COMPLIMENTARY` and whatever a purchase would freeze (the role-level Professional report, or the marker of a
v2 student purchase), exactly what a redeemed access code produces. No
access code is typed, Stripe is never called, no webhook is simulated and no Stripe payment ID is written. The
paid-report and PDF controllers are unchanged and still require an `ACTIVE` entitlement. Granting is
idempotent: reopening a result never creates a second entitlement.

## Required environment

| Variable | Purpose |
|---|---|
| `APP_LIVE_SMOKE_ENABLED=true` | Explicit switch. Without it the profile refuses to start |
| `TYPESAFE_API_KEY` | Real Jev / TypeSafe calls |
| `OPENAI_API_KEY` | Real OpenAI narratives |
| `UNIVERSITY_NARRATIVE_MODEL` (optional) | University narratives, free and paid; defaults to `gpt-6-luna` |
| `A_LEVEL_NARRATIVE_MODEL` (optional) | A-level narratives, free and paid; defaults to `gpt-6-luna` |
| `PROFESSIONAL_NARRATIVE_MODEL` (optional) | Professional narratives, free and paid; defaults to `gpt-6.1-sol` |
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

For a Professional result the panel also offers **Prepare detailed report (real Jev and OpenAI, stored once)**,
which posts to the customer's normal `/professional-assessment/{id}/report/prepare` route: real responsibility Jev
call, deterministic guidance, real `professional-paid-narrative-v2` OpenAI call, then the stored
`professional-report-v4` and its `professional-report-pdf-v6`. Each step is stored once; preparing again reuses
everything. **Detailed report v3 snapshot (JSON)** shows what was assembled, including the paid narrative status, and
the detailed report page shows a small live-smoke line with the versions and narrative status. The panel's
responsibility-analysis and responsibility-guidance links show and create those two stored steps on their own. To
exercise the more-than-eight selection screen, submit a custom Professional assessment with nine or more
responsibilities and open its Detailed Report.

For University and A-level, open **Detailed Report** and use the page's own **Prepare my detailed report** button.
University makes one real `university-module-v1` Jev call (choose up to eight modules if the course lists more), then
the guidance and the real paid narrative, and stores `university-report-v3`. A-level makes no Jev call at this point:
it derives the guidance from the stored subject profile, makes the paid-narrative call and stores `a-level-report-v2`.
If OpenAI gives no usable answer, the report is shown provisionally with an **Add the personalised explanation**
button and nothing is stored. The loading overlay appears while either request runs.

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
| Professional | Electrician, Secondary-school Teacher, Solicitor, Customer-service Adviser | Stage 4C responsibility calibration cases |
| University | BSc Computer Science | `university-example.html` |
| University | Economics | `UniversityJevJudgmentServiceTest` |
| University | History | `UniversityJevStateMapperTest` |
| University | BSc Computer Science, BA Business Management, BSc Nursing (Adult), LLB Law, BA Graphic Design, BEng Mechanical Engineering, BA English Literature | University calibration inputs (`UniversityBaselineCases`) |
| A-level | Mathematics, Economics and Geography | `a-level-example.html` |
| A-level | Mathematics and Physics | `ALevelJevStateMapperTest` |
| A-level | English Literature, History and Politics (subjects only) | `ALevelAssessmentFormMapperTest` |

The short Economics and History inputs exercise thin-input behaviour rather than a full course description. The
seven University calibration cases each list nine to eleven modules, so preparing their detailed report always
goes through the "Choose up to 8 modules" selection screen.

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

## Opt-in live harnesses

Each harness below is gated by its own Maven profile, a system property that only that profile sets, and the
provider key it needs. None is ever part of `./mvnw verify`, and each writes its results outside `target/` (except
where stated) so a later clean keeps them.

| Maven profile | Harness | Calls | Output |
| --- | --- | --- | --- |
| `live-jev-evaluation` | `ProfessionalJevLiveEvaluationIT`, `StudentJevLiveEvaluationIT` | Jev | `target/jev-evaluation/` (see [PROFESSIONAL_JEV_EVALUATION.md](PROFESSIONAL_JEV_EVALUATION.md), [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md)) |
| `live-openai-evaluation` | `LiveOpenAiNarrativeIT` | OpenAI (Professional also Jev) | `target/openai-evaluation/` |
| `live-evidence-evaluation` | `NomisLiveEvidenceIT`, `ProfessionalLiveEvidenceFlowIT` | Nomis (flow also OpenAI) | `target/live-evidence/` ([LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md)) |
| `live-responsibility-probe` | `ProfessionalResponsibilityJevProbeIT` | Jev | `.live-smoke/probes/` |
| `live-paid-narrative` | `ProfessionalPaidNarrativeLiveIT` | OpenAI | `.live-smoke/paid-narrative/` |
| `live-university` | `UniversityBaselineLiveIT` | Jev | `.live-smoke/university/` |
| `live-university-module-probe` | `UniversityModuleProbeLiveIT` | Jev | `.live-smoke/university-module/` |
| `live-a-level-calibration` | `ALevelV2LiveCalibrationIT` | Jev | `target/a-level-calibration/live/` |

## Responsibility probe

`ProfessionalResponsibilityJevProbeIT` (Stage 4B) runs real Jev responsibility analysis for the saved Professional
smoke cases at increasing batch sizes, to decide between one call for up to eight responsibilities and two calls of
four. It calls Jev only:

```
TYPESAFE_API_KEY=... ./mvnw -Plive-responsibility-probe verify [-Djev.responsibility.probe.sizes=1,4,8] [-Djev.responsibility.probe.case=registered-nurse]
```

It writes `.live-smoke/probes/responsibility-probe-<time>.md` and the raw results as JSON.

## Paid narrative live evaluation

`ProfessionalPaidNarrativeLiveIT` runs `professional-paid-narrative-v2` against real OpenAI for the seven
calibration roles. Each role goes through the real orchestration: a stored assessment, the real complimentary grant
freezing the role-level report, and the captured seven-role live Jev analysis as the stored snapshot (Jev is not
called again). Guidance, the paid narrative with real validation and repair, `professional-report-v4` assembly and
the v6 PDF are all real. It is never part of `./mvnw verify`:

```
OPENAI_API_KEY=... ./mvnw -Plive-paid-narrative verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
```

Results (per-role status, repair, failed rules, call and preparation timings, PDFs and the narrative text) are written
to `.live-smoke/paid-narrative/<timestamp>/`. Add `-Dopenai.paid.narrative.live.dry-run=true` to check the wiring
without any call (output in `target/paid-narrative-dry-run/`). `PROFESSIONAL_NARRATIVE_MODEL` selects the model (default `gpt-6.1-sol`), under the same
allowlist as the application; a GPT-6 model is sent reasoning effort `none` and no temperature. The four non-Stage-2 roles use placeholder role-level measures; their narratives explain those
placeholder scores, so review them for their responsibility-level content.

## University baseline capture

`UniversityBaselineLiveIT` (Stage 5B) makes one real `university-v1` Jev call for each of seven representative
courses and decides it with the unchanged `university-decision-v1` policy. It never calls OpenAI, Stripe or the
database, and it is never part of `./mvnw verify`:

```
TYPESAFE_API_KEY=... ./mvnw -Plive-university verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
```

It writes `results.json`, `README.md` and one capture per course (input, judgments and decision) to
`.live-smoke/university/<timestamp>/`. Add `-Duniversity.baseline.case=nursing` to run one case (one Jev call); each
capture also keeps Jev's raw answers and their probability sums. `UniversityBaselineHarnessTest` runs the same harness offline with synthetic
judgments and writes `target/university-baseline-dry-run/`.

## University module probe

`UniversityModuleProbeLiveIT` (Stage 5C) makes one real `university-module-v1` Jev call per representative course,
for up to eight modules each, through the production module adapter. It never calls OpenAI, Stripe or the database,
and it is never part of `./mvnw verify`:

```
TYPESAFE_API_KEY=... ./mvnw -Plive-university-module-probe verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
```

Add `-Duniversity.module.probe.case=nursing` for one course. Output goes to `.live-smoke/university-module/<timestamp>/`:
`results.json`, `README.md` and one capture per course with the selected modules and ids, the raw Jev answers, the
mapped judgments and an independent validation of the raw answers. The module selection is a probe convenience; the
product will ask the student to choose when a course has more than eight usable modules.

## A-level v2 live calibration capture

`ALevelV2LiveCalibrationIT` is the release gate for `a-level-decision-v2`. It makes one real `a-level-v2` Jev call
for each of seven representative subject combinations, with the production client settings (60-second timeout, no
retries), and applies the unchanged policy. It never calls OpenAI, Stripe or the database, and it is never part of
`./mvnw verify`:

```
TYPESAFE_API_KEY=... ./mvnw -Plive-a-level-calibration verify -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
```

It writes `target/a-level-calibration/live/a-level-v2-live-calibration.json` and a Markdown summary. Copy them out
of `target/` before the next clean. Run it only on a machine that can reach Jev (not under `dev-no-jev`).
`ALevelV2LiveCalibrationCaptureTest` checks the capture format offline.

A capture taken on 6 November 2026 (`jev-latest`) was reported as passing the gate. Promoting it means committing
that JSON unaltered under `src/test/resources/a-level/calibration-v2/` and moving `ALevelV2CalibrationTest` onto it;
until then the offline test still uses the synthetic `offline-calibration.json`.
