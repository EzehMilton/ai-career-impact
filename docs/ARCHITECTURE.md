# AI Career Impact architecture

AI Career Impact is intended to remain a modular monolith. Its journeys share delivery and operational infrastructure, but each journey keeps its own inputs and decision policy.

## Assessment flow

```text
User input
    ↓
Journey-specific AssessmentState
    ↓
Jev atomic judgments                      (one call per assessment)
    ↓
Java journey-specific decision policy
    ↓
Persisted assessment snapshot
    ↓
Free result                               (Professional: plus an OpenAI narrative and evidence;
    ↓                                      University and A-level: deterministic, no model call)
Payment or complimentary access → ACTIVE entitlement
    ↓
Customer asks for the detailed report     (explicit POST, never on payment, never on a page view)
    ↓
Optional paid Jev analysis                (Professional responsibilities, University modules; A-level none)
    ↓
Deterministic Java guidance
    ↓
One OpenAI paid narrative, validated, one repair, deterministic fallback
    ↓
Immutable detailed report snapshot → web page and PDF
```

Jev supplies small, explicit judgments rather than an overall business decision. Deterministic Java policies combine those judgments using journey-specific weights, thresholds, and rules. OpenAI turns the resulting decision into accessible narrative, but never determines an authoritative score, band, factor, classification or ranking. External evidence is gathered and displayed separately; it is not sent to OpenAI. Saved records are never recalculated: a page view or PDF download reads stored snapshots and makes no provider call.

External facts, including salaries and labour-market statistics, must come from an evidence layer with traceable sources rather than from model memory.

## Journeys and decision semantics

New assessments are decided by these policies. Earlier policies stay in the code, unchanged, because every saved assessment is read back with the policy version it was decided by.

```text
                              SHARED PLATFORM
                                     |
          +--------------------------+--------------------------+
          |                          |                          |
          v                          v                          v
   PROFESSIONAL                 UNIVERSITY                   A-LEVEL
 professional-decision-v2   university-decision-v2      a-level-decision-v2
          |                   (course profile,           (subject profile,
          |                    no headline score)         no headline score)
   AI Exposure               Working Alongside AI       Options Kept Open
   Human Advantage           Human and Practical        Analytical & Quantitative
   AI Complementarity          Capability               Communication, Interpretation
   Role Adaptability         Transferable Capability      & Context
                                                        Learning Alongside AI
```

- **Professional** assesses how AI may affect a person's current work: AI Exposure, Human Advantage, AI Complementarity, and Role Adaptability. Its indicators describe characteristics of the current role.
- **University** assesses what a course develops for an AI-shaped labour market. Under `university-decision-v2` it is a course profile of three measures with no composite headline: Working Alongside AI, Human and Practical Capability, and Transferable Capability. It deliberately does not answer "will AI take this job?". Historical `university-decision-v1` assessments keep their five measures (Future Readiness, AI Complementarity, Human Differentiation, Skill Transferability, Career Adaptability).
- **A-level** assesses what a subject combination develops and how many directions it keeps open. Under `a-level-decision-v2` it is a subject profile with no composite headline: Options Kept Open, a two-sided Capability Profile (Analytical & Quantitative, and Communication, Interpretation & Context, shown together and never added up), and Learning Alongside AI. Historical `a-level-decision-v1` assessments keep their six measures headed by Future Optionality.

The journeys therefore share the `AssessmentDecision` result shape, but not a universal meaning for any number. Each Java decision policy defines its own dimensions, aggregation, labels, and thresholds.

### Why a shared platform does not imply shared score semantics

The three journeys measure **different constructs**. They share a 0–10 presentation scale and a persistence, delivery and payment pattern, and nothing else:

- They answer different questions about different subjects of assessment: a person's current work, a course of study, and a combination of school subjects.
- They are computed from different atomic judgments. `professional-v1` and `university-v1` share only `information_sufficient`, `ai_complementarity` and `contextual_complexity`; `university-v1` and `a-level-v2` share only `information_sufficient`, `analytical_reasoning` and `original_judgment_creativity`; `professional-v1` and `a-level-v2` share only `information_sufficient`. Every rubric is written for its own subject of assessment, so even a shared identifier is a different question.
- They use different weights, versioned separately: `professional-decision-v2`, `university-decision-v2` and `a-level-decision-v2`, with the v1 policies kept for historical assessments.
- Professional and the two v2 student profiles all band at 3.5 and 7.0 as `LOW` / `MODERATE` / `HIGH`, but each journey has its own band enum (`IndicatorBand`, `ProfileBand`, `ALevelProfileBand`), and its pages say what a band means for that measure only. The historical v1 student policies used deliberately disjoint vocabularies (`LIMITED` / `DEVELOPING` / `STRONG` and `FOCUSED` / `BALANCED` / `BROAD`), which their stored assessments keep.
- Their direction of merit differs. A high AI Exposure is a neutral-to-cautionary observation about work; a high student measure describes something a course or a combination develops. None is the inverse of AI Exposure.

A professional 7.2 and a student 7.2 therefore mean unrelated things and must never be compared, averaged, ranked together or combined into one universal AI career score. Each journey's measures are separate enums precisely so no shared score object can exist; each decision policy refuses another journey's judgment set outright; and `CrossJourneySeparationTest` pins the separation. Each paid report states it in its methodology section.

Genuinely shared, journey-neutral types are reused: `JourneyType`, `AssessmentStatus`, `AssessmentConfidence`, `AssessmentDecision`, `AssessmentJudgmentSet`, `DimensionJudgment`, the security configuration and the persistence/payment/PDF patterns. Narrative, evidence, payment, report and PDF status enums are deliberately duplicated per journey rather than hoisted into a generic framework: the small duplication keeps each journey's semantics legible and avoids coupling one journey's release to another's. There is deliberately no `Journey<T>` abstraction; three clear vertical slices are easier to reason about than one parameterised framework that hides which semantics apply.

## Modules

Top-level packages under `com.chikere.futureai`, each organised by journey inside:

- `assessment`: journey forms, form mappers, assessment application services, workflows and their persistence.
- `judgment`: the atomic judgment contracts, the Jev/TypeSafe adapters and schemas, and the development fixtures.
- `decision`: deterministic, journey-specific Java policies.
- `responsibility`: the paid Professional responsibility analysis, its classification policy and its guidance.
- `coursemodule`: the paid University module analysis, its classification policy and its guidance.
- `guidance`: the deterministic A-level guidance policy.
- `narrative`: free and paid explanation generation from authoritative decisions, with validators.
- `evidence`: sourced external facts and provenance.
- `occupation`: Professional occupation classification.
- `report`: deterministic report assembly, the detailed paid reports and their presentation models.
- `payment`: checkout, Stripe webhooks, entitlement, complimentary access and payment status.
- `pdf`: PDF rendering and storage of purchased reports.
- `product`: the shell the three journeys are presented inside — landing page, shared chrome, informational pages, request filters, the two first-party scripts and development-mode signalling. It owns no assessment logic.
- `security`: the security filter chain, CSRF and response headers.
- `configuration`: profiles, startup validation, metrics, the OpenAI model policy and external-client configuration.
- `cost`: estimated provider cost per assessment.
- `trace`: the opt-in assessment trace.
- `livesmoke`: the local `live-smoke` regression tool.

Packages should be introduced only when they contain real code. Module boundaries should remain explicit without creating empty packages or separate deployable services prematurely.

## Professional decision pipeline

```text
Browser
    |
    v
ProfessionalAssessmentForm
    |
    v
ProfessionalAssessmentController
    |
    v
ProfessionalAssessmentFormMapper
    |
    v
ProfessionalAssessmentState
    |
    v
ProfessionalAssessmentService
    |
    v
ProfessionalJudgmentProvider
    |
    +-------------------------------+
    |                               |
    v                               v
ProfessionalJevJudgmentService    LocalFixtureProfessionalJudgmentProvider
    |                               |
    +---------------+---------------+
                    |
                    v
          AssessmentJudgmentSet
                    |
                    v
       ProfessionalDecisionPolicyV2
                    |
                    v
      ProfessionalAssessmentResult
                    |
                    v
       ProfessionalAssessmentStore
                    |
                    v
       JPA adapter / PostgreSQL
                    |
                    v
     Redirect to saved result GET
```

`ProfessionalAssessmentForm` is a web DTO shaped for HTML fields and Bean Validation. It collects multiline responsibilities and skills as text. `ProfessionalAssessmentFormMapper` trims those fields, drops blank lines, and creates `ProfessionalAssessmentState`, the domain value describing actual work. Keeping them separate prevents HTML input concerns from entering the assessment domain. The MVC controller handles validation and calls the application workflow once for valid input. Validation problems return to the form with field errors. Processing or persistence failures return a generic error page without a fixture fallback or vendor details.

`ProfessionalJudgmentProvider` is the professional application port: it states the judgment capability the use case needs without TypeSafe, Jev, Spring AI, or HTTP types. `ProfessionalJevJudgmentService` is the normal adapter that supplies this capability through one Jev System One request for all nine `professional-v1` judgments. `ProfessionalAssessmentService` coordinates one provider call and one decision policy call, then stores a snapshot with source and version metadata. The application service does not score work itself.

Adapters are chosen by Spring profile, never by exception handling. The application understands exactly four profiles, and `StartupConfigurationValidator` refuses any combination of them:

| Profile | Jev judgments | Paid Jev analysis (responsibilities, modules) | OpenAI narratives | Evidence and classification | Payment |
| --- | --- | --- | --- | --- | --- |
| `dev` | fixture | none | fixture free narratives; no paid narrative | fixture | simulated local checkout |
| `dev-no-jev` | fixture | none | real | fixture | real Stripe |
| `live-smoke` | real | real | real | none | bypassed through complimentary access |
| `prod` | real | real | real | real (Professional Nomis only) | real Stripe |

Without a paid analysis provider, preparing a Professional or University detailed report stops with a safe "unavailable" message; the A-level detailed report needs no Jev call. Without a paid narrative generator, a detailed report is shown provisionally from deterministic copy and nothing is stored.

`dev-no-jev` is for a machine that cannot reach the TypeSafe/Jev network. `live-smoke` is a local regression tool and must never be deployed; see [LIVE_SMOKE_TESTING.md](LIVE_SMOKE_TESTING.md). `prod` requires `TYPESAFE_API_KEY`, `OPENAI_API_KEY`, every Stripe setting, the Professional occupation mapping and a public base URL; `StartupConfigurationValidator` names anything missing without logging a value. Live AI verification history and the explicit Jev command are recorded in [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md).

### Saved professional assessments

Write flow: browser POST → form validation → form mapper → application service → Jev or explicit local fixture → `AssessmentJudgmentSet` → `ProfessionalDecisionPolicyV2` → `ProfessionalAssessmentResult` → `ProfessionalAssessmentStore` → JPA adapter → PostgreSQL → redirect to `/professional-assessment/{UUID}`.

Read flow: GET `/professional-assessment/{UUID}` → `ProfessionalReportService` → saved package stores → `ProfessionalReportAssembler` → free view mapper → Thymeleaf. The GET does not invoke the judgment provider or policy. Post/Redirect/Get means a browser refresh repeats only the GET, avoiding a second paid Jev request.

`ProfessionalAssessmentStore` is a vendor-neutral application port. Its JPA adapter maps the immutable application snapshot to dedicated persistence entities. Domain and decision records remain free of JPA annotations. Flyway V1 creates an assessment row with input and provenance, judgment rows for all nine dimensions, measure rows, and ordered factor rows. JSONB holds only the ordered responsibility/skill and decision ID lists, plus each judgment's probability distribution. Nullable columns preserve absent Noul confidence rather than inventing a value. The saved decision includes its original scores, bands, confidence, status, and structured factors; reading an old assessment never recalculates it under today's policy. Schema and policy versions identify the methodology that produced it.

The external Jev call and Java policy execute before `JpaProfessionalAssessmentStore.save` starts its short database transaction. `findById` also maps inside a read-only transaction. A database transaction cannot make a Jev network call atomic with PostgreSQL: if saving fails after a successful call, the request fails safely and no redirect is issued. No automatic retry or fixture fallback occurs.

The UUID is an opaque temporary access token. There is no public list endpoint; result responses use `private, no-store`, and the page requests `noindex,nofollow`. Anyone who has a saved URL can currently read that assessment, so this pre-authentication access model requires proper account-based authorization before production use. The configured Jev model name (currently `jev-latest`) is saved when known; this alias is not an immutable underlying model version and the adapter does not claim it is. The TypeSafe client is configured with a 60-second timeout (`TYPESAFE_TIMEOUT`) and no automatic retries (`TYPESAFE_MAX_RETRIES=0`), so one user action is at most one billed Jev request.

`LocalFixtureProfessionalJudgmentProvider` is an adapter for explicit local development only. It is active under the `dev` and `dev-no-jev` profiles, which select an exact fixture ID through `app.professional.fixture-id` (`PROFESSIONAL_FIXTURE_ID`, default `senior_software_architect`): `routine_software_developer`, `senior_software_architect`, `electrician`, or `registered_nurse`. The fixture ID is technical configuration; `ProfessionalAssessmentState.profession` remains the user's real job title. Missing or unsupported IDs fail at startup. Fixture data is illustrative development data, not product intelligence. Those profiles select only the fixture provider; `prod` and `live-smoke` select only the Jev provider. Fixtures never activate automatically, and a Jev failure never falls back to fixtures. `ProfessionalAssessmentResult.source` carries `JEV` or `LOCAL_FIXTURE` explicitly for future callers, along with the judgment schema and decision policy versions. The results view prominently marks local fixture output as development data.

For local startup, run:

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` needs no Jev, OpenAI or Stripe key and defaults every journey's fixture ID; the existing PostgreSQL development configuration still applies. Visit `/professional-assessment` and enter a real job title and work details. Spring Security permits this public form and its stylesheet while keeping CSRF protection for the POST and requiring authentication for unrelated routes.

The Maven `spring-boot:run` development path has the optional Spring Boot Docker Compose dependency on its runtime classpath, so it discovers the running PostgreSQL service and supplies the datasource connection. The repackaged executable JAR excludes that development dependency. `application.properties` intentionally has no production datasource URL or credentials. A packaged deployment must provide its own PostgreSQL connection configuration, for example `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; running the JAR locally without them fails at datasource startup. This is deployment configuration to supply when a deployment environment is introduced, not a reason to add H2 or embed development credentials.

`ProfessionalDecisionPolicyV1` is deterministic Java policy. It validates the atomic judgments, normalises their 0–4 values, applies explicit weights, assigns indicator bands, derives confidence and status, and selects structured explanation factors. It does not call Jev or OpenAI.

New assessments use `ProfessionalDecisionPolicyV2` (`professional-decision-v2`). Its scores, weights, bands, confidence and status are identical to v1, so the same judgments produce the same four scores. The one difference is factor materiality: an AI Exposure factor is presented only when its signed effect is at least 0.015 (about 0.15 points on the 0–10 score). Smaller effects still count towards the score but are not shown, so either factor list may legitimately be empty. Both policies share `ProfessionalDecisionEngine`.

## Professional personalised narrative

```text
Stored Professional Assessment
        |
        v
ProfessionalNarrativeService
        |
        v
ProfessionalNarrativeGenerator
        |
        +---------------------+
        |                     |
        v                     v
OpenAI adapter        Local fixture adapter
        |                     |
        +----------+----------+
                   v
       ProfessionalNarrative
                   |
                   v
             Persistence
                   |
                   v
              Result page
```

Narrative generation is downstream of the saved deterministic assessment. `ProfessionalAssessmentWorkflow` first asks `ProfessionalAssessmentService` to complete and persist the Jev judgments and Java decision. Only after that transaction has committed does `ProfessionalNarrativeService` reload the snapshot and build `ProfessionalNarrativeContext`. The context contains the submitted work description, responsibilities, skills and optional experience; the saved status and confidence; all saved measure values, bands and confidences; the structured exposure drivers and protective factors; and the assessment source, judgment schema version and decision policy version. It contains no JPA entity, database access, credentials or provider SDK type.

`ProfessionalNarrativeGenerator` is a vendor-neutral application port. Its only operation explains an already-decided context and returns the immutable, HTML-free `ProfessionalNarrative` record. This boundary prevents controllers, persistence entities and view mappers from calling a model. It also makes the same structured result suitable for the web page and a possible future report without coupling the model to either presentation.

`OpenAiProfessionalNarrativeGenerator` is the normal adapter under `prod`, `dev-no-jev` and `live-smoke`. It uses the existing Spring AI 2.x `ChatClient` and one typed `entity(ProfessionalNarrative.class, ...)` request with provider-native structured output. It does not request free-form prose and scrape headings. The response record requires every prose section and between three and five non-blank practical actions. `ProfessionalNarrativeValidator` additionally rejects score-like or percentage claims; the prompt instructs the model not to repeat numeric assessment values at all. Invalid or absent output fails the narrative attempt and can never change assessment state.

The current prompt and output contract is `professional-narrative-v7`; v2 to v6 identify narratives saved under earlier prompts. Since v4 the provider input is reduced to customer-facing meaning (no dimension identifiers or effect codes), the AI Exposure explanation leads with the dominant material factor, and a validator failure gets one repair attempt; since v6 an empty factor list is explained as meaningful rather than filled. The contract states that saved scores, bands, confidence, status, drivers and protective factors are authoritative. The model must not recalculate or invent scores, introduce decision factors, contradict the decision, predict job loss, claim certainty or disappearance, fabricate labour-market or salary facts, cite invented studies or statistics, recommend abandoning a career, or recommend courses and providers. It must distinguish exposure from complementarity, treat Human Advantage as independent rather than the inverse of exposure, and explain Role Adaptability as a property of role structure rather than the person's motivation, learning ability, willingness, finances, geography or circumstances. Actions are limited to modest steps grounded in the assessment. User-supplied text is explicitly data rather than instructions.

`LocalFixtureProfessionalNarrativeGenerator` is selected only by the `dev` profile. It uses fixed templates plus saved bands and structured factors; it has no alternate scoring method and no OpenAI dependency. Its persisted source and provider are `LOCAL_FIXTURE` and `local-fixture`, and the existing DEVELOPMENT MODE warning remains visible. Normal OpenAI failures never select this bean or call it: profile wiring permits exactly one generator, and `ProfessionalNarrativeService` stores `FAILED` instead of looking up another implementation.

### Narrative lifecycle and history

Flyway V2 adds `professional_assessment_narrative`, a separate one-to-one table keyed by the immutable assessment ID. No row means `NOT_REQUESTED`; an attempt writes `PENDING` before calling the generator and then updates that row to `AVAILABLE` with all structured content or `FAILED` without content. The table records source, provider identifier, configured model identifier when known, prompt/schema version, attempt timestamp and successful generation timestamp. It never stores API keys, prompts, exceptions, hidden reasoning or chain-of-thought. A database check constraint ensures only an available row can contain narrative content and a generation timestamp.

Keeping narrative lifecycle in a separate table lets the assessment snapshot remain immutable. A generator or narrative-persistence failure occurs after the assessment commit, so it cannot delete or invalidate the deterministic result. The submission still redirects to the saved result. The result GET always renders the measures and factors; when narrative is missing, pending or failed it shows a safe temporary-unavailability message without provider details.

Result GETs are read-only. They load a saved narrative when one exists and never invoke `ProfessionalNarrativeGenerator`, Jev or `ProfessionalDecisionPolicyV1`. `generateFor` also returns any existing lifecycle row without replacing it, including older prompt versions and failed attempts. Consequently an `AVAILABLE` historical narrative remains exactly the narrative originally saved: there is no implicit regeneration or upgrade from one prompt/schema version to another. A future regeneration feature would need to be an explicit operation with its own audit semantics.

The model identifier is persisted: the model the provider reported, or else the journey's configured narrative model (`app.narrative.professional.model`, configurable through `PROFESSIONAL_NARRATIVE_MODEL`). It may be a provider alias rather than an immutable underlying model revision, so it is audit metadata with that limitation rather than a claim that the exact provider-side build is known. The prompt/schema version remains the authoritative identifier for this application's narrative contract.

The weights and thresholds in `professional-decision-v1` are transparent V1 product policy. They are not claimed to be scientifically validated universal truths. Changing them requires a new policy version so an existing version never silently changes meaning.

AI Exposure describes how readily the current work may be performed, automated, or materially changed by AI. It is not a probability of job loss. Human Advantage is separately calculated because a role can combine meaningful AI exposure with strong reasons for continued human involvement. AI Complementarity describes potential augmentation rather than automation exposure.

Role Adaptability describes characteristics of the role that provide scope for continued human value and AI-enabled evolution. It does not measure the individual's personal motivation, willingness to retrain, learning ability, financial flexibility, geographic mobility, or personal circumstances.

## Professional evidence layer

```text
Persisted Professional Assessment
        |
        v
ProfessionalEvidenceService
        |
        v
ProfessionalEvidenceProvider
        |
        +----------------------+----------------------+
        |                                             |
        v                                             v
Nomis/ONS live adapter                 Local fixture adapter
        |                                             |
        +----------------------+----------------------+
                               v
                 ProfessionalEvidenceBundle
                               |
                               v
                         Persistence
                               |
                               v
                         Results page
```

External evidence is factual career and labour-market context, not an input to `ProfessionalDecisionPolicyV1`. It cannot change AI Exposure, Human Advantage, AI Complementarity, Role Adaptability, status or confidence. Evidence is deliberately downstream of the persisted assessment: assessment and narrative remain valid if an evidence provider or its persistence fails. The result page labels evidence as “External career context” and explicitly says that it does not determine or prove the scores.

`ProfessionalEvidenceProvider` is the vendor-neutral application port. It receives a narrow `ProfessionalEvidenceRequest` containing the assessment UUID, profession, responsibilities, skills, journey, explicit geography and an optional occupation classification. It never receives JPA entities, credentials, Jev types or OpenAI types. Providers return the same immutable `ProfessionalEvidenceBundle` and `ProfessionalEvidenceItem` model, whether the source is live or a fixture. Items preserve category, factual summary, source and publisher, reference or safe HTTP(S) link, geography, publication and retrieval dates, optional complete metric triplets, source-quality classification and a relevance note. `ProfessionalEvidenceVersions.EVIDENCE_SCHEMA` defines `professional-evidence-v1` once.

OpenAI does not produce or rewrite this evidence. Salary figures, employment estimates, skill demand and research claims must come from traceable sources rather than model memory. The narrative generator is unchanged and receives no evidence in this phase.

### Live and fixture adapters

The live adapter uses the official ONS Annual Population Survey occupation dataset exposed by the Nomis API (`APS218`, API dataset `NM_218_1`). It makes two bounded HTTP requests: the official SOC 2020 codelist resolves the configured four-digit code to Nomis's dataset-specific value, then one observation request obtains the latest UK employment estimate. The adapter validates dataset, occupation, geography, measure and numeric value before accepting the response. It has a 5-second connection timeout and a 10-second read timeout. It supports United Kingdom only; another configured geography fails safely instead of receiving a misleading UK label. The reference period and retrieval date are retained, while publication date is absent when the response does not supply one. A single historical employment count is not a trend or forecast. It does not scrape web pages and does not infer or fuzzily match an occupation code from the submitted title. For now `prod` uses an exact operator-configured mapping requiring `PROFESSIONAL_EVIDENCE_PROFESSION`, `PROFESSIONAL_EVIDENCE_CANONICAL_OCCUPATION` and `PROFESSIONAL_EVIDENCE_SOC2020_CODE`; the submitted title must match exactly. This is intentionally limited: V1 proves the replaceable classifier and evidence-adapter boundaries but does not yet provide broad official occupation lookup, trend series, salary, skills or automation research for every role. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md) for endpoints, live results and source limits.

Live evidence is active only under `prod`. Under `live-smoke` there is no provider and evidence remains `NOT_REQUESTED` (represented by no row). Ordinary tests and development therefore make no live evidence request. No API key is required for the bounded anonymous Nomis request; if a future adapter needs credentials, they must come from environment configuration and must never be persisted or logged.

The `dev` and `dev-no-jev` profiles select `LocalFixtureProfessionalEvidenceProvider` alongside the fixture judgments. It supports only the exact configured IDs `routine_software_developer`, `senior_software_architect`, `electrician` and `registered_nurse`. The representative item is deterministic, carries `LOCAL_FIXTURE` / `DEVELOPMENT_FIXTURE` provenance, has no fake government URL, and is shown under both the main development warning and an evidence-specific “not live official data” label. Unsupported or approximate fixture IDs fail clearly.

Profiles, rather than exception handling, select adapters. `ProfessionalEvidenceService` has exactly one optional configured provider and never knows about the fixture implementation. A live exception is recorded as `FAILED`; it cannot cause fixture lookup or fallback. This also keeps the boundary cache-friendly: a future shared cache can sit behind a provider without changing assessment orchestration, but V1 adds neither Redis nor a cache framework.

### Lifecycle, persistence and historical behaviour

`ProfessionalAssessmentWorkflow` persists the authoritative assessment, runs the independent narrative lifecycle, then requests evidence when a provider is configured. Evidence generation stores `PENDING` before the provider call, followed by `AVAILABLE`, `PARTIAL` or `FAILED`. `PARTIAL` must contain the items that succeeded; `FAILED` contains none. A provider result is accepted only if its assessment ID, source, provider ID and `professional-evidence-v1` version match the configured provider. Exceptions are not stored or displayed.

Flyway V3 adds `professional_assessment_evidence_bundle`, keyed one-to-one by assessment UUID, and ordered `professional_assessment_evidence_item` rows. The bundle stores status, version, source/provider provenance and retrieval timestamp. Items store all displayable factual content and metadata. Foreign keys associate and clean up only with the parent assessment; providers do not mutate assessment rows. V1 and V2 remain unchanged.

Evidence is a historical snapshot. `gatherFor` returns any existing row—including `FAILED`, `PARTIAL` or an older version—without another provider call. GET `/professional-assessment/{UUID}` uses only `findFor`; it does not call the evidence provider, Jev, the decision policy or narrative generator. Refresh and regeneration require a future explicit operation. Persisting one compact provider response per new assessment, avoiding requests per UI section, and never refreshing on page load controls marginal cost for a low-priced report. Larger official downloadable datasets can later be ingested or cached behind another adapter instead of downloaded per customer.

The current geography is the explicit `app.professional.evidence.geography` configuration, defaulting to `United Kingdom`, because the professional form does not capture location. The application does not infer geography from IP address. Adding user-supplied geography and a broad official occupation lookup are the main architectural gaps before broad live coverage.

## Professional free and paid report composition

```text
Persisted Assessment Package
     |
     +-- Assessment decision
     +-- Narrative
     +-- Evidence
     +-- Occupation classification
     |
     v
ProfessionalReportAssembler
     |
     +-------------------+
     |                   |
     v                   v
FREE report          PAID report
     |                   |
     v                   v
Free web view        Paid development preview
```

`ProfessionalReportAssembler` is a pure, deterministic application component. It accepts a `ProfessionalReportSource` containing only saved historical records and creates the immutable, delivery-neutral `ProfessionalReport`. It cannot call Jev, the Java decision policy, OpenAI, an evidence provider or any external API. `ProfessionalReportService` only reads the four persistence ports, while the web layer maps the already tiered report to presentation data. Templates contain no entitlement rules. The same stored input, tier and composition version therefore produce the same report. New reports are `professional-report-v2` (plain measure descriptions, revised factor sentences, and no longer presenting confidence, caution or the visitor's own responsibilities as insight); `professional-report-v1` snapshots stay readable.

Payment never affects assessment. Both tiers use exactly the same saved status, confidence, four scores, bands, factors, narrative and evidence. `FREE` includes the profession, all authoritative measures and concise definitions, top exposure and protective factors, a short saved interpretation, the explicit job-loss disclaimer and a concise methodology caveat. It intentionally remains useful. `PAID` includes all of that plus the complete saved narrative, actions, per-measure explanations, deterministic skill-development priorities, conservative work-focus guidance, saved external evidence with attribution, saved occupation classification and fuller methodology/version information. The product boundary is expressed by report composition rather than scattered `if (paid)` checks in Thymeleaf.

Skill priorities are modest labels (`HIGH_PRIORITY` or `USEFUL`), not scores. They follow only from saved role-level factor directions and the saved AI Complementarity band. Work-focus guidance likewise does not classify individual tasks: general assistance, oversight and human-value areas follow from saved role factors, while every submitted responsibility is placed in `USER_REVIEW_REQUIRED` with an explicit statement that no task-level judgement occurred. This avoids inventing a second assessment engine.

### Occupation classification

`ProfessionalOccupationClassifier` is a vendor-neutral port receiving only profession, responsibilities, skills and explicit geography. Its output records canonical occupation, classification system/code, provenance, mapping quality and `professional-occupation-classifier-v1`. It cannot alter assessment scores. The fixture implementation (`dev`, `dev-no-jev`) maps only the four exact configured fixture IDs to deterministic SOC 2020 occupations and never fuzzily matches a title. The `prod` implementation is currently an exact operator-configured mapping; a broader official lookup can replace it behind the same port later without changing assessment or evidence orchestration.

Classification runs after the assessment and narrative have been persisted and before evidence is gathered. Flyway V4 adds one immutable `professional_occupation_classification` row per assessment, keyed by the assessment UUID. The evidence request factory reads that saved classification rather than environment values. An existing row is never overwritten or automatically upgraded, GET requests never classify again, and absent classification degrades cleanly. V4 is necessary because the code and timestamp used for historical evidence selection are audit data; V1–V3 remain unchanged.

### Report history, access and price

Ordinary assembled reports are not separately persisted because they would duplicate the assessment, narrative and evidence snapshots. The report version is defined once and exposed in report methodology. A confirmed purchase is the exception: its exact role-level paid report is frozen as an immutable purchased snapshot, as described below, and the detailed report prepared after it is stored as its own snapshot.

The paid route, `GET /professional-assessment/{UUID}/report`, requires an `ACTIVE` entitlement and renders the stored detailed report, or otherwise the frozen purchased snapshot with a "Prepare your detailed report" step. URL knowledge, a redirect query parameter and a pending Checkout Session do not grant access. No price is hard-coded: the expected low-cost consumer range is £1.99–£4.99, while Stripe Price configuration is authoritative for charging and optional display text comes from configuration.

Missing or failed optional data does not prevent a report. A failed narrative leaves the deterministic assessment and report available; a failed evidence bundle gives a safe unavailable message; and missing classification simply omits that section. Reports expose no exceptions, credentials, prompt text, chain-of-thought, JPA entities or provider implementation details. Normal development remains offline, and there is no live-to-fixture fallback.

## Professional report purchase and entitlement

```text
FREE report
   |
checkout request
   |
ProfessionalReportPurchaseService
   |
ProfessionalPaymentGateway
   |
Stripe / local fixture / complimentary code
   |
verified payment event
   |
ACTIVE entitlement
   |
freeze ProfessionalReport
   |
PurchasedProfessionalReportSnapshot
   |
PAID report access
```

Payment grants access to presentation only. It never participates in Jev judgments, the Java decision policy, narrative generation, evidence retrieval, occupation classification or report composition. The vendor-neutral `ProfessionalPaymentGateway` exchanges `ProfessionalCheckoutRequest`, `ProfessionalCheckoutSession` and signed-notification models with the application layer; Stripe SDK objects remain inside the Stripe adapter. Browser card entry is entirely on Stripe-hosted Checkout. The Checkout request contains one configured Stripe Price and only the assessment and entitlement UUIDs as metadata—never work descriptions or other submitted content.

`ProfessionalReportPurchaseService.startCheckout` first verifies that a complete saved PAID report can be composed, then calls the gateway without a database transaction. A short transaction persists the returned session as a `PENDING` entitlement. This avoids holding a database connection or lock during a network call. The trade-off is that a successful Stripe session can become orphaned if the subsequent database write fails; it grants no access and can expire harmlessly. A failed entitlement may be retried using the same entitlement identity and a new session. There is no Stripe-to-fixture exception fallback.

The Stripe adapter is selected under `prod` and `dev-no-jev`, and startup fails if `STRIPE_SECRET_KEY`, any journey's webhook secret or Price ID is missing. It uses the official Stripe Java SDK and hosted Checkout Sessions in one-time payment mode. Optional `PROFESSIONAL_REPORT_DISPLAY_PRICE` controls CTA display only; Stripe's configured Price remains authoritative for the charge and currency. `live-smoke` has no payment gateway. The `dev` profile instead selects a deterministic, zero-network gateway and an explicit local confirmation screen with prominent development warnings. It never represents its session or entitlement as Stripe provenance.

**Complimentary access.** When `app.complimentary-report.enabled` (`COMPLIMENTARY_REPORT_ENABLED`) is on, the free result offers an access-code form (`POST /{journey}/{UUID}/complimentary-access`). One shared code (`COMPLIMENTARY_REPORT_CODE`) unlocks the ordinary paid report for any assessment, under every profile, without Stripe: it creates an `ACTIVE` entitlement with provider `COMPLIMENTARY` and freezes the same paid snapshot a purchase would. While the flag is off the form is absent and the route answers like one that does not exist. The code arrives in the POST body and is never echoed, logged or rendered. `live-smoke` uses this same path to grant access automatically.

### Confirmation, idempotency and freezing

The success redirect is navigation, not proof of payment. It returns the browser to the free result, which shows "Confirming your payment" while the entitlement is `PENDING`. A small first-party script (`static/js/payment-confirmation.js`) polls `GET /{journey}/{UUID}/payment-status`, a read-only JSON view of the saved entitlement status, every 1.5 seconds for up to 30 seconds, and moves to the paid report once the webhook has activated it. The script never talks to Stripe or changes state, and the page works without it. Production confirmation comes through `POST /payments/stripe/webhook`. That exact path alone is exempt from browser CSRF because it is authenticated by Stripe's signature; checkout and local-confirmation POSTs retain CSRF protection. The adapter verifies the signature with the configured webhook secret, accepts only `checkout.session.completed`, requires the session's payment status to be `paid`, and validates its minimal metadata against the saved provider, session, assessment and entitlement identifiers.

Stripe can redeliver events. Flyway V5 records each provider event ID under a composite provider/event primary key. Processing locks the entitlement row, rechecks state and identity, and atomically writes the purchased snapshot, changes `PENDING` to `ACTIVE`, and records the event. A duplicate event returns an idempotent already-processed result and neither rebuilds nor duplicates the report. If report assembly, JSON conversion or persistence fails, the transaction rolls back: no `ACTIVE` entitlement can exist without its purchased snapshot, and Stripe may retry the event.

The purchased snapshot is JSONB because `ProfessionalReport` is an immutable presentation document with nested, versioned sections; normalising it would duplicate the report model into many purchase-only tables. Entitlement status, provider/session/payment references, timestamps and `purchased_report_version` remain relational for constraints and queries. The JSON contains only user-facing paid report content—scores, explanations, factors, narrative, actions, skill and work-focus guidance, displayed evidence and classification, methodology and limitations. It excludes secrets, prompts, exceptions, SDK data and hidden reasoning.

The frozen snapshot, not today's assembler, is authoritative after purchase. A `professional-report-v1` bought in 2026 therefore remains V1 after a future V2 deploy. `GET /professional-assessment/{UUID}/report` denies absent, `PENDING`, `FAILED`, `REFUNDED` and `REVOKED` states and renders the snapshot only for `ACTIVE`. The free assessment remains available throughout checkout and all payment failures.

There are no accounts yet. Entitlement is associated with the assessment UUID, so anyone who obtains that opaque URL may potentially reach both the assessment and an entitled report. There is no public listing route, but this is explicitly temporary pre-authentication access rather than final production-grade authorisation. Account-bound ownership belongs in a later phase.

## Paid professional responsibility analysis

A paid Professional assessment can gain one additional, immutable responsibility analysis: one Jev call for the
selected responsibilities (`professional-responsibility-v1`), classified by `professional-responsibility-policy-v1`,
and stored as a snapshot with its inputs, raw judgments and classifications. It never changes the role-level scores,
bands, factors or narrative, and a stored snapshot is never regenerated or re-classified.

- **Access.** Reuse and generation both require the assessment's entitlement to be ACTIVE now. A refunded or revoked
  entitlement keeps its stored snapshot but can no longer obtain it.
- **Responsibility limit.** An assessment may hold more than eight responsibilities; detailed analysis supports one
  to eight. More than eight is refused as `RESPONSIBILITY_SELECTION_REQUIRED`; nothing is truncated and no automatic
  "first eight" selection is permitted. `generateOrGet(id, selection)` accepts the customer's explicit choice (1-based
  positions, one to eight, distinct); an invalid choice is `INVALID_SELECTION`. The detailed-report preparation page
  offers that choice (see below).
- **Payment boundary.** The Stripe webhook only establishes the ACTIVE entitlement. Responsibility analysis is
  generated later by the paid-report lifecycle; payment confirmation makes no responsibility provider call.
- **Concurrency.** The unique assessment constraint makes one snapshot canonical. Two truly simultaneous first
  requests may both call the provider, and both return the canonical snapshot. This is accepted for v1.

### Responsibility guidance

`ProfessionalGuidancePolicyV1` (`professional-guidance-policy-v1`) is pure Java. It derives deterministic guidance
from the frozen analysis snapshot only: up to three AI-assistance opportunities (an explicit ranking formula with a
governance penalty: O1 0.4, O2 0.2, regulated high accountability and safety-critical physical O1 work, rule IDs
`GV1`–`GV4`, which lowers sensitive work's rank without making it ineligible), up to
three human-value areas, two to four consolidated skill priorities, a three-phase 90-day plan and up to four
responsible-use safeguards. Every item carries a rule ID. It reads neither the role-level scores nor the user-entered
skills and profession, and it calls no provider.

`ProfessionalResponsibilityGuidanceService` checks the ACTIVE entitlement, returns stored guidance unchanged if it
exists, and otherwise obtains the analysis through `ProfessionalResponsibilityAnalysisService` (which makes the only
possible Jev call), runs the policy once and stores an immutable snapshot (Flyway V20,
`professional_responsibility_guidance`, unique per assessment and per analysis). Each run emits one
`RESPONSIBILITY_GUIDANCE` trace event (GENERATED, REUSED or CONCURRENT_SNAPSHOT_RETURNED) carrying IDs, keys and
rule IDs only. Since Stage 4F the guidance is presented by the detailed report (v3, now v4).

## Detailed professional report (professional-report-v4)

The £4.99 report, assembled once from stored artefacts and frozen:

```text
purchased professional-report-v2 snapshot (frozen at payment: scores, bands, factors, v7 narrative)
  + responsibility-analysis snapshot (4D)  + guidance snapshot (4E)
  + paid narrative (professional-paid-narrative-v2, or deterministic FALLBACK)
        -> ProfessionalDetailedReportAssembler -> professional-report-v4 snapshot (V21 tables)
        -> ProfessionalDetailedReportView -> paid-report-v4.html  and  professional-report-pdf-v6 (stored bytes)
```

`professional-report-v4` with `professional-paid-narrative-v2` adds a bounded practical layer to the deterministic findings: concrete uses, what stays with the person, a first experiment, role-specific skill manifestations and an adaptation strategy, all keyed to the deterministic ids. Universal safeguards are shown once, under Responsible AI use, instead of on every opportunity. Stored `professional-report-v3` reports (with `professional-paid-narrative-v1`) keep their own page template (`paid-report-v3.html`) and their stored `professional-report-pdf-v5`; nothing is reassembled. The rest of this section applies to both.

- **Generation is explicit.** `GET /professional-assessment/{id}/report` shows a stored v3 report, or otherwise the
  purchased v2 report with a "Prepare your detailed report" step. `POST .../report/prepare` (CSRF) runs
  `ProfessionalDetailedReportService`: ACTIVE entitlement, stored v3 if any, otherwise analysis and guidance through
  the 4E service (the only possible Jev call), the paid narrative once, assembly and insert-once storage. Viewing a
  page or downloading a PDF never generates anything, and payment confirmation never reaches this code.
- **Selection.** With more than eight usable responsibilities and no analysis yet, the page shows "Choose up to 8
  responsibilities for detailed analysis" (nothing pre-selected). The service validates the choice; once the analysis
  exists a later selection is ignored, so the analysed set is frozen.
- **Paid narrative.** One OpenAI call explains the frozen findings, with request settings from
  `OpenAiNarrativeRequestOptions` (see "OpenAI request settings" below). Its structured output is keyed
  by deterministic ids (opportunity, human-value area, skill, plan action); `ProfessionalPaidNarrativeValidator`
  requires every id exactly once in order and rejects reclassification, reranking, new recommendations or skills,
  unsafe delegation, job-loss, capability and future claims, vendors, numbers, internal identifiers, US spelling and
  hype. One repair attempt. Outcomes:
  - **AVAILABLE**: validated; stored, immutable, never regenerated.
  - **FALLBACK** (final): the provider returned content that still failed validation after its repair; stored with
    the failed rules, immutable, never retried. The report renders completely from deterministic copy.
  - **RETRYABLE_FALLBACK**: no usable provider response (not configured, timeout, connection failure, rate limit,
    provider error, empty response, or a failed repair call). Nothing is stored. The customer sees the complete
    deterministic report provisionally (assembled from the stored analysis and guidance on each view, no provider
    call; its PDF is rendered on demand and not stored) with an explicit "Add the personalised explanation" button.
    Viewing never retries; only that explicit POST does.
- **Freezing.** The canonical v3 snapshot is stored only with an AVAILABLE or final FALLBACK narrative, so a
  transient outage never becomes the permanent result and a stored report is never rewritten.
- **Oversight presentation.** When at least 75% of the analysed responsibilities carry the stored oversight marker,
  one section-level note replaces per-card badges and per-opportunity conditions; below that only affected
  responsibilities are marked. The markers themselves are unchanged.
- **Immutability and coexistence.** The v3 snapshot references its entitlement, analysis, guidance and narrative and
  records every version (role report, decision, responsibility schema and policy, guidance policy, paid narrative,
  report, PDF). It is read back as stored. v2 snapshots and their v4 PDFs are untouched and still served wherever no
  detailed report exists; `report.pdf` serves the detailed report's stored PDF (v5 or v6) once one exists.
- **Concurrency.** As in 4D/4E, unique assessment constraints keep one canonical narrative and report; truly
  simultaneous first requests may duplicate provider calls. Accepted for v1.

#### OpenAI narrative models per journey

Each journey has one OpenAI narrative model, used by its free and paid narratives alike and sent explicitly on every
request, initial and repair: `app.narrative.university.model` and `app.narrative.a-level.model` (default and minimum
`gpt-6-luna`) and `app.narrative.professional.model` (default and minimum `gpt-6.1-sol`). `OpenAiNarrativeModelPolicy`
holds one explicit allowlist per journey; a model outside it refuses startup and refuses to create the generator.
Spring AI's global `spring.ai.openai.chat.model` remains only as its infrastructure default. There is no fallback
model: a failure takes the journey's existing deterministic fallback. Stored narratives keep the model they were
written with; changing a journey's model never regenerates them.

#### OpenAI request settings for the Professional narratives

Both Professional OpenAI generators, role-level `professional-narrative-v7` and the paid narrative,
take their sampling settings from one shared `OpenAiNarrativeRequestOptions`, so the model rule lives in one place.
The University and A-level generators, free and paid, use the same helper, which also carries each request's model.
A setting that is absent is left out of the request entirely, never filled with a default:

- a GPT-6 model (for example `gpt-6-luna`) sends reasoning effort `none` and no temperature, because these models
  reject a custom temperature;
- an explicit reasoning effort (`app.professional.narrative.openai.reasoning-effort` for v7,
  `app.professional.paid-narrative.openai.reasoning-effort` for the paid narrative) is sent for any model, and
  temperature is then omitted;
- any other model keeps the existing temperature 0.2 and sends no reasoning effort.

The repair call uses the same settings as the first call. **Versioning:** this is a provider-parameter
compatibility fix, so `professional-narrative-v7` and the paid narratives keep their versions. Their
prompts, structured-output schemas, validators, repair rules and fallback behaviour are unchanged; the semantic
contract is unchanged. Stored narratives are immutable and are returned as stored, never regenerated because the
configured model or its settings changed.

## Purchased professional report PDF

```text
Purchased professional-report-v1/v2 snapshot
        |
        v
ProfessionalReportPdfService
        |
        v
ProfessionalReportPdfRenderer
        |
        v
professional-report-pdf-v4
        |
        v
immutable persisted PDF artifact
        |
        v
authorised download
```

The PDF is a delivery artifact of the exact report already bought. `ProfessionalReportPdfService` reads an `ACTIVE` entitlement and its frozen purchased snapshot directly through persistence ports. It never calls `ProfessionalReportAssembler`, Jev, the decision policy, OpenAI, narrative generation, evidence retrieval, occupation classification or Stripe. This makes payment-confirmed report content—not current application state—the sole source of PDF text. This section describes the PDF of the role-level purchased snapshot; once a detailed report exists, `report.pdf` serves that report's stored PDF instead (see above).

`ProfessionalReportPdfRenderer` is a vendor-neutral application port receiving `ProfessionalReportPdfModel`, which contains the purchased content version, purchase timestamp, PDF template version and immutable purchased report. `OpenHtmlProfessionalReportPdfRenderer` is the local pure-Java implementation. It converts internally generated XHTML and conservative print CSS through OpenHTMLToPDF/PDFBox. The template uses built-in font families, inline packaged CSS and no images, remote stylesheets, CDN resources, tracking elements or other network inputs. Persisted safe HTTP(S) evidence URLs are emitted only as PDF hyperlinks; they are never fetched.

Content and presentation versions are deliberately separate. The report version (`professional-report-v1` or `-v2`) identifies what the customer bought. The PDF version (currently `professional-report-pdf-v4`) identifies how those purchased fields are laid out; a stored PDF keeps the layout it was rendered with. The purchased-snapshot codec switches explicitly on the stored report version, giving each version a small reader seam rather than assuming that every historical JSON document matches the latest Java model. Future incompatible versions must add a reader/mapper and must not reinterpret an earlier one.

### Artifact lifecycle and persistence

On the first authorised `GET /professional-assessment/{UUID}/report.pdf`, the service first checks for an existing artifact. If present it returns those exact bytes without loading the snapshot or invoking the renderer. Otherwise it loads the matching frozen snapshot, renders once, requires a `%PDF-` signature, rejects empty or over-limit output, calculates SHA-256, sanitises a profession-based filename and calls `ProfessionalReportPdfStore.saveIfAbsent`.

Flyway V6 adds `purchased_professional_report_pdf`, with one row per entitlement and a unique assessment reference. It stores purchased report version, PDF template version, generation timestamp, safe filename, byte count, SHA-256 and PDF bytes in PostgreSQL `BYTEA`. The database checks positive and matching byte length plus hash format. BYTEA is appropriate for these small bounded consumer documents and avoids premature object-storage infrastructure. The storage port permits a later S3/object-store adapter without changing the service or controller.

The insert uses PostgreSQL `ON CONFLICT DO NOTHING`, followed by reading the single stored winner. If simultaneous first requests both render, the entitlement primary key ensures one immutable artifact wins and both callers return that same persisted artifact. No distributed lock or queue is needed. Existing rows are never updated or regenerated; subsequent downloads and application restarts return identical bytes and SHA-256.

Only an `ACTIVE` entitlement reaches generation or download. Missing, pending, failed, refunded and revoked states receive HTTP 403. Successful responses use `application/pdf`, an attachment disposition with a sanitised filename, an exact content length, `nosniff`, and private/no-store caching. Rendering or persistence failure leaves the entitlement, purchased snapshot and paid HTML report unchanged and returns a generic temporary PDF-unavailability message without provider or renderer details.
## Professional input provenance

Professional input is manual only: `ProfessionalAssessmentForm` maps to `ProfessionalAssessmentState`
and every new assessment is saved with `ProfessionalAssessmentInputMetadata.manual()`.

An earlier version also accepted a CV upload. That path has been removed, but its provenance columns
remain so historical assessments load unchanged: V7 stores `MANUAL`/`CV_UPLOAD`, the sanitised
filename, canonical media type, parser version and parse timestamp, and V18 adds
`cv_extractor_version`. `professional_assessment_cv_metadata_consistent` still requires a manual row
to have none of these fields and a `CV_UPLOAD` row to have all of them. These fields never affected
scoring; judgments, policies, reports and PDFs do not read them.

## University journey

The university journey answers a different product question from the professional journey: *what does this course develop for an AI-shaped labour market?* It is a distinct vertical slice that reuses the proven delivery pattern without reusing professional scoring semantics.

```text
University input
    ↓
UniversityAssessmentState
    ↓
UniversityJudgmentProvider            (one university-v1 Jev call)
    ↓
UniversityDecisionPolicyV2            (course profile: three measures, no headline)
    ↓
saved assessment + evidence
    ↓
deterministic free result             (no model call)
    ↓
university entitlement (university-report-v2 purchase)
    ↓
"Prepare your detailed report"        (explicit POST)
    ↓
module analysis (one university-module-v1 Jev call, ≤ 8 modules)
    ↓
university-guidance-policy-v2 → university-paid-narrative-v2
    ↓
university-report-v3 snapshot → web page and university-report-pdf-v3
```

The sections below describe each step. Where a section is headed v1, it describes the historical path that assessments decided before `university-decision-v2` still follow when they are read back.

```text
Browser
    |
    v
UniversityAssessmentForm
    |
    v
UniversityAssessmentController
    |
    v
UniversityAssessmentFormMapper
    |
    v
UniversityAssessmentState
    |
    v
UniversityAssessmentService
    |
    v
UniversityJudgmentProvider
    |
    +-----------------------------+
    |                             |
    v                             v
UniversityJevJudgmentService    LocalFixtureUniversityJudgmentProvider
    |                             |
    +--------------+--------------+
                   |
                   v
         AssessmentJudgmentSet
                   |
                   v
        UniversityDecisionPolicyV2
                   |
                   v
        UniversityAssessmentResult
                   |
                   v
        UniversityAssessmentStore
                   |
                   v
        JPA adapter / PostgreSQL
                   |
                   v
      Redirect to saved result GET
```

### University input

`UniversityAssessmentState` is a dedicated immutable domain record with no JPA annotations: course title, course description, key modules, skills developed, preferred career areas, and optional institution and country. It is deliberately not `ProfessionalAssessmentState` and never requires employment experience.

`UniversityAssessmentForm` is the web DTO with Bean Validation; `UniversityAssessmentFormMapper` trims fields, drops blank lines and creates the domain value. The form uses language aimed at sixth-form and university applicants, and states that career areas are context rather than a recommendation. Institution and country are recorded but the assessment does not rank institutions or use league tables.

### University judgment schema

`UniversityJudgmentSchema` defines `university-v1` as one Noul plus nine 0–4 Score questions, issued in a single Jev System One request:

| Dimension | 0 | 4 |
| --- | --- | --- |
| `information_sufficient` | Noul: only a course title or an insufficient description | Enough about content, modules, skills or how the course is studied |
| `technical_digital_depth` | Little technical/digital development | Core, sustained technical or digital capability development |
| `analytical_reasoning` | Little structured analysis or problem solving | Rigorous analysis, quantitative reasoning or systematic problem solving defines the course |
| `original_judgment_creativity` | Mostly fixed procedure or content reproduction | Original synthesis, judgment, design or creative work |
| `human_relationship_dependency` | Little interpersonal capability | Deep interpersonal, communication or relationship capability is central |
| `contextual_complexity` | Highly structured, routine contexts | Ambiguity, competing factors and changing conditions define the course |
| `practical_real_world_application` | Mainly theoretical | Extensive real-world practice, placement, clinical, studio or project work |
| `ai_complementarity` | Little useful AI augmentation | Strong productive AI augmentation while human contribution remains valuable |
| `skill_transferability` | Narrowly tied to one context | Broadly useful across many roles and sectors |
| `career_breadth` | Very narrow plausible pathway | Very broad range of plausible pathways |

Each Score defines all five rubric levels explicitly, in ascending order. The schema never asks whether a degree is good, whether someone should study it, whether graduates will get jobs, or whether it is future-proof: those are product conclusions for deterministic Java, not atomic judgments.

`UniversityJudgmentProvider` is the vendor-neutral application port. `UniversityJevJudgmentService` is the normal adapter; TypeSafe/Jev SDK types never leave `judgment.typesafe.university`. It returns the shared `AssessmentJudgmentSet` / `DimensionJudgment` types. `UniversityJevAutoConfiguration` is registered in `AutoConfiguration.imports`, is active only under `prod` and `live-smoke`, and requires a `TypeSafeClient` bean.

### University decision policy v1 (historical assessments)

New assessments are decided by `university-decision-v2` (see [University decision v2](#university-decision-v2-the-course-profile) below). This section describes the v1 policy that earlier assessments were decided by and are still read back with.

`UniversityDecisionPolicyV1` (`university-decision-v1`) is pure deterministic Java: no Jev SDK, no OpenAI, no persistence, no evidence. It validates the judgment set, normalises 0–4 scores to 0–1, applies explicit weights, assigns bands, derives confidence and status, and selects structured explanation factors. Every weight map is validated at class-initialisation time to sum to exactly 1 and to reference only known dimensions.

| Measure | Weights |
| --- | --- |
| `FUTURE_READINESS` | analytical reasoning 0.20, technical/digital depth 0.15, original judgment/creativity 0.15, AI complementarity 0.15, skill transferability 0.15, contextual complexity 0.10, career breadth 0.10 |
| `HUMAN_DIFFERENTIATION` | original judgment/creativity 0.30, human relationship capability 0.30, contextual complexity 0.20, practical real-world application 0.20 |
| `AI_COMPLEMENTARITY` | ai_complementarity 0.70, technical/digital depth 0.15, analytical reasoning 0.15 |
| `SKILL_TRANSFERABILITY` | skill_transferability 0.60, analytical reasoning 0.20, career breadth 0.20 |
| `CAREER_ADAPTABILITY` | skill transferability 0.25, career breadth 0.25, analytical reasoning 0.20, contextual complexity 0.15, AI complementarity 0.15 |

These weights are transparent, provisional V1 **product policy**. They are not claimed to be validated educational research. Changing any of them requires a new policy version so an existing version never silently changes meaning. They are not the professional weights, and no dimension is inverted: every university dimension contributes positively, which is itself a semantic difference from `professional-decision-v1`.

Bands are `LIMITED` below 3.50, `DEVELOPING` below 7.00, and `STRONG` at or above 7.00, using student-facing language rather than "safe", "risky" or "future-proof".

Sufficiency and confidence reuse the shared `AssessmentStatus` and `AssessmentConfidence`:

- `information_sufficient` below 0.60 produces `MORE_INFORMATION_REQUIRED`, `LOW` confidence, no indicator and no measures.
- 0.60 to below 0.75 produces `READY_WITH_CAUTION`.
- 0.75 and above produces `READY` only when confidence is `HIGH`, which additionally requires sufficiency at or above 0.80, mean judgment confidence at or above 0.80 and minimum judgment confidence at or above 0.60. Confidence is derived from the provider's own per-dimension confidence and is never fabricated.

### University explainability (v1)

Explainability uses signed contribution logic, following the established professional pattern. A separate `EXPLANATION_WEIGHTS` map covers **all nine** Score dimensions and sums to 1; it is used only to select and order factors and never contributes to an authoritative measure. This lets human and practical dimensions be explained even though they do not enter Future Readiness.

For each dimension the signed effect is `weight × (normalised − 0.5)`. Positive effects become `STRENGTH` factors ("key strengths", "readiness drivers"); negative effects become `DEVELOPMENT_OPPORTUNITY` factors ("areas you may want to strengthen"). Because a dimension has exactly one signed effect, the same dimension can never appear on both sides. The top three of each side are kept. `UniversityFactorDirection` records which side of the neutral midpoint was observed, and the report maps each direction to course-specific language rather than professional "exposure driver" wording.

### University persistence

Flyway V8 adds `university_assessment` with input, provenance and headline decision, plus ordered `university_assessment_judgment`, `university_assessment_measure` and `university_assessment_factor` rows. JSONB holds only the ordered module/skill/career lists, the factor ID lists and each judgment's probability distribution. Nullable columns preserve absent Noul confidence rather than inventing a value. A check constraint pins both journey columns to `UNIVERSITY`.

V9 adds `university_assessment_narrative`, V10 adds `university_assessment_evidence_bundle` and `university_assessment_evidence_item`, V11 adds `university_report_entitlement`, `purchased_university_report_snapshot` and `processed_university_payment_event`, and V12 adds `purchased_university_report_pdf`. University tables are entirely separate from professional tables; no university record is ever forced into a professional table. V1–V7 are unchanged.

V22 adds the paid module analysis and V23 the `university-decision-v2` course-profile measures and contributions plus the stored detailed report; both are described below. A v1 assessment keeps its five measures in `university_assessment_measure` and has no V23 rows.

`UniversityAssessmentStore` is a vendor-neutral application port whose JPA adapter inserts only — a completed assessment is never merged over an old snapshot. The saved decision keeps its original scores, bands, confidence, status and structured factors, so reading an old assessment never recalculates it under today's policy. `GET /university-assessment/{UUID}` loads saved records only: it never invokes the judgment provider, the decision policy, the narrative generator or an evidence provider.

### University free narrative (v1 assessments only)

A `university-decision-v2` assessment has a deterministic free result and no free narrative: submission makes one Jev call and no OpenAI call. The free narrative below exists only for historical v1 assessments; `UniversityNarrativeService.generateIfApplicable` does nothing for a v2 profile.

`UniversityNarrativeService` runs after the assessment transaction has committed. `UniversityNarrativeContextFactory` builds `UniversityNarrativeContext` from the persisted snapshot: course details, saved status and confidence, all five saved measures with values, bands and confidences, the structured strengths and development opportunities, and the source and version metadata. It contains no JPA entity, database access, credential or provider SDK type.

`UniversityNarrativeGenerator` is the vendor-neutral port returning the immutable, HTML-free `UniversityNarrative`. `OpenAiUniversityNarrativeGenerator` is the normal adapter, using one typed `entity(UniversityNarrative.class, ...)` request with provider-native structured output. `LocalFixtureUniversityNarrativeGenerator` is selected only by the `dev` profile and uses fixed templates plus saved bands and factors. Profile wiring permits exactly one generator; an OpenAI failure stores `FAILED` rather than looking up another implementation.

The free narrative prompt (`university-narrative-v2`, after `-v1`) states that saved measures, bands, confidence, status, strengths and development opportunities are authoritative. The model must not recalculate or invent them, repeat numeric values, add new assessment factors, or contradict the decision. It must not invent module content, teaching arrangements or institutions; state salary figures, graduate employment rates, league table positions, entry requirements or study costs; cite studies or sources; guarantee employment; call a course future-proof, safe, risky, a bad degree or a waste; or tell the student to abandon the degree. It must explain Future Readiness as a property of the course rather than a probability that a job will be automated, treat AI Complementarity as independent, and treat Skill Transferability and Career Adaptability as properties of the capabilities and pathways rather than the student's motivation, ability, finances or circumstances. Career areas are context only, phrased as "a relevant area to explore"; "you should become X" is forbidden. User-supplied text is explicitly data rather than instructions.

`UniversityNarrativeValidator` is a deterministic backstop applied after typed mapping. It rejects repeated scores and percentages, currency and salary figures, and unsupported employment or quality claims (`future-proof`, employment rates, job guarantees, "bad degree", "you will struggle to find work"). Anything it rejects fails the narrative attempt and can never change assessment state.

Narrative lifecycle is a separate one-to-one table keyed by the immutable assessment ID: no row means `NOT_REQUESTED`; an attempt writes `PENDING`, then `AVAILABLE` with content or `FAILED` without. `generateFor` returns any existing row without replacing it, including older prompt versions and failed attempts, so a historical narrative remains exactly what was originally saved. Regeneration would need to be an explicit future operation with its own audit semantics.

### University evidence

`UniversityEvidenceProvider` is the vendor-neutral port. It receives a narrow `UniversityEvidenceRequest` containing the assessment UUID, course title, modules, skills, preferred career areas, optional institution, explicit geography and journey — never a JPA entity, credential or SDK type. Providers return the immutable `UniversityEvidenceBundle` and `UniversityEvidenceItem` model. Categories are `COURSE_CONTENT`, `GRADUATE_OUTCOMES`, `OCCUPATIONAL_DESTINATIONS`, `SKILL_DEMAND` and `LABOUR_MARKET_CONTEXT`. The version is `university-evidence-v1`.

Evidence is external factual context, not an input to `UniversityDecisionPolicyV1`. It cannot change any measure, status or confidence. It is deliberately downstream of the persisted assessment: assessment and narrative remain valid if an evidence provider or its persistence fails. Geography comes from the submitted country when supplied, otherwise from the explicit `app.university.evidence.geography` configuration, defaulting to `United Kingdom`. The application does not infer geography from IP address.

`LocalFixtureUniversityEvidenceProvider` (`dev`, `dev-no-jev`) supports the six exact configured IDs, carries `LOCAL_FIXTURE` / `DEVELOPMENT_FIXTURE` provenance, emits no fake government URL, and is labelled as development data in the UI. No live university evidence adapter exists. Prompt 18B examined official Discover Uni/HESA data but deferred a live adapter: the request has a course title and optional institution, without stable course/provider identifiers or entry year. Course content requires an identified provider course; Graduate Outcomes aggregates must not be presented as outcomes for an arbitrary course; occupational and labour-market data require an independently justified mapping. All five live categories therefore remain unavailable until a defensible source mapping is implemented. Under `prod` and `live-smoke` there is no provider and evidence remains `NOT_REQUESTED`. A provider exception is recorded as `FAILED` and never causes fixture lookup. Historical evidence is never refreshed by a GET. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md).

### University report composition

For a `university-decision-v2` assessment, `UniversityCourseProfileComposer` turns the stored course profile into the free result: the three measures with their bands and the material factors that drive and limit each, worded deterministically and never combined into an overall score or ranked. The paid product is the detailed report described below.

For a historical v1 assessment, `UniversityReportAssembler` is a pure, deterministic application component. It accepts a `UniversityReportSource` containing only saved historical records and produces the immutable, delivery-neutral `UniversityReport` (`university-report-v1`). It cannot call Jev, the decision policy, OpenAI, an evidence provider or any external API. `UniversityReportService` only reads the three persistence ports; the web layer maps the already tiered report to presentation data, so templates contain no entitlement rules.

Payment never affects assessment. Both tiers use exactly the same saved status, confidence, five measures, bands and factors. `FREE` includes the course, all authoritative measures with concise definitions, key strengths, development areas, a short saved interpretation, an explicit statement that the result is not an employment guarantee, and a concise methodology caveat. It intentionally remains useful. `PAID` adds the full narrative, per-measure explanations, practical actions, deterministic skill-development priorities, career-area context, saved external evidence with attribution, and fuller methodology and limitations.

Skill priorities are modest labels (`WORTH_STRENGTHENING` or `USEFUL`), not scores, and follow only from saved factor directions and the saved AI Complementarity band. Career-area guidance uses the student's own supplied areas as context: "a relevant area to explore", "skills that may help", "additional preparation worth considering". V1 is not a job recommendation engine and never says "you should become X".

### University payment, entitlement and purchase freezing

```text
FREE report
   |
checkout request
   |
UniversityReportPurchaseService
   |
UniversityPaymentGateway
   |
Stripe / local fixture / complimentary code
   |
verified payment event
   |
ACTIVE entitlement
   |
freeze UniversityReport
   |
PurchasedUniversityReportSnapshot
   |
PAID report access
```

University entitlement is **entirely separate** from professional entitlement. The two live in different tables with foreign keys to their own assessment tables, so a professional purchase grants no university access and a university entitlement cannot even be created for a professional assessment ID. There is a separate Stripe Price (`UNIVERSITY_REPORT_STRIPE_PRICE_ID`), a separate webhook endpoint (`POST /payments/stripe/university-webhook`), a separate configurable webhook secret, and separate local-fixture provenance. No price is hard-coded. Complimentary access and the payment-status poller work exactly as in the Professional journey.

`UniversityReportPurchaseService.startCheckout` first verifies that a complete saved PAID report can be composed, then calls the gateway without a database transaction, then persists the returned session as a `PENDING` entitlement in a short transaction. A Stripe failure never persists an entitlement and never falls back to the fixture gateway.

The Stripe adapter is selected under `prod` and `dev-no-jev` and fails startup if its secret key, webhook secret or Price ID is missing. Its Checkout Session carries `product=university_report` metadata alongside the assessment and entitlement UUIDs — never course descriptions or other submitted content. The webhook verifier requires that product tag, so an event for another product is ignored rather than treated as an error. The success redirect is navigation, not proof of payment.

Confirmation is idempotent: each provider event ID is recorded under a composite provider/event primary key, processing locks the entitlement row, rechecks state and identity, and atomically writes the purchased snapshot, flips `PENDING` to `ACTIVE` and records the event. A duplicate event returns an already-processed result and neither rebuilds nor duplicates the report. If anything fails the transaction rolls back, so no `ACTIVE` entitlement can exist without its purchased snapshot.

What is frozen depends on the assessment. A v1 assessment's purchase (`university-report-v1`) freezes the exact paid report document, and it stays v1 whatever is deployed later. A v2 assessment's purchase (`university-report-v2`) freezes no document: its course profile is already immutable in the assessment, and its detailed report is generated once, later, on the customer's request, and stored as its own snapshot. `PurchasedUniversityReportSnapshotCodec` switches explicitly on the stored version rather than reinterpreting an earlier one. Only `ACTIVE` reaches `GET /university-assessment/{UUID}/report`; absent, `PENDING`, `FAILED`, `REFUNDED` and `REVOKED` are denied. The free assessment remains available throughout checkout and all payment failures.

### Purchased university report PDF (v1 purchases)

This section describes the PDF of a historical `university-report-v1` purchase. The detailed report of a v2 purchase is rendered differently; see [University detailed report](#university-detailed-report-university-report-v3).

`UniversityReportPdfService` reads an `ACTIVE` entitlement and its frozen purchased snapshot directly through persistence ports. It never calls `UniversityReportAssembler`, Jev, the decision policy, OpenAI, narrative generation, evidence retrieval or Stripe, so payment-confirmed `university-report-v1` content — not current application state — is the sole source of PDF text.

Content and presentation versions are separate: `university-report-v1` identifies what the customer bought; `university-report-pdf-v1` identifies how those purchased fields are laid out. `OpenHtmlUniversityReportPdfRenderer` is a local pure-Java implementation using OpenHTMLToPDF/PDFBox with built-in fonts, inline CSS and no images, remote stylesheets, CDN resources or other network inputs. Persisted safe HTTP(S) evidence URLs are emitted only as hyperlinks and are never fetched.

On the first authorised `GET /university-assessment/{UUID}/report.pdf` the service returns an existing artifact's exact bytes without loading the snapshot or invoking the renderer; otherwise it renders once, requires a `%PDF-` signature, rejects empty or over-limit output, calculates SHA-256, sanitises a course-based filename and calls `saveIfAbsent`. The insert uses PostgreSQL `ON CONFLICT DO NOTHING`, so simultaneous first requests converge on one immutable artifact without a distributed lock. Existing rows are never updated; subsequent downloads and application restarts return identical bytes and SHA-256. Rendering or persistence failure leaves the entitlement, purchased snapshot and paid HTML report unchanged and returns a generic message without provider detail.

The university PDF is a deliberately parallel implementation rather than a generic abstraction over the professional PDF. Forcing shared generic rendering would have required changing working professional code for no current benefit; the two renderers can be unified later behind a small port if a third journey justifies it.

### University web routes, UX and local development

| Route | Purpose |
| --- | --- |
| `GET /university-assessment` | Public form |
| `GET /university-assessment/example` | A worked example of a completed form |
| `POST /university-assessment` | Validate, submit once, Post/Redirect/Get |
| `GET /university-assessment/{UUID}` | Read-only free result |
| `POST /university-assessment/{UUID}/checkout` | Start checkout (CSRF-protected) |
| `POST /university-assessment/{UUID}/complimentary-access` | Redeem the complimentary code; 404 while disabled |
| `GET /university-assessment/{UUID}/payment-status` | Read-only entitlement status (JSON) for the confirmation poller |
| `GET /university-assessment/{UUID}/report` | Paid report, `ACTIVE` only: the stored detailed report, the preparation step, or a frozen v1 report |
| `POST /university-assessment/{UUID}/report/prepare` | Prepare the detailed report, with an optional module selection (CSRF-protected) |
| `GET /university-assessment/{UUID}/report.pdf` | PDF of the stored report, `ACTIVE` only |
| `GET`/`POST /university-assessment/{UUID}/checkout/local[/confirm]` | `dev` only |
| `POST /payments/stripe/university-webhook` | `prod` and `dev-no-jev`, signature-authenticated |

The result page is written for students, not software professionals. It explains what Future Readiness means before showing numbers, presents strengths and development areas side by side, uses "This assessment suggests…", "This course appears to develop…" and "One area you may want to strengthen…", and states plainly that no assessment can tell you whether a degree is right for you. Alarmist and verdict language is absent by construction and asserted against in tests. Result responses use `private, no-store` and request `noindex,nofollow`. CSRF is preserved for every browser POST; only the two Stripe webhook paths are exempt because Stripe's signature authenticates them.

For local startup, run `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. `app.university.fixture-id` (`UNIVERSITY_FIXTURE_ID`, default `computer_science`) supports only the exact IDs `computer_science`, `economics`, `graphic_design`, `history`, `mechanical_engineering` and `nursing`. There is no fuzzy matching: a missing or unsupported ID fails at startup. The fixture ID is technical configuration; `UniversityAssessmentState.courseTitle` remains the user's real course. Fixture data is illustrative development data chosen to exercise contrasting presentation paths — it is not empirical intelligence about any subject, course or institution, and the UI marks it prominently as development data. Each journey wires its own fixture provider independently, so each has its own fixture ID under `dev` and `dev-no-jev`.

### University decision v2: the course profile

`UniversityDecisionPolicyV2` (`university-decision-v2`) decides every new University assessment. It is a pure policy over the unchanged `university-v1` judgments, designed from the frozen Stage 5B live baseline (`src/test/resources/university/baseline-5b/`). It is a course profile of three measures with no composite headline:

| Measure | Weights |
| --- | --- |
| Working Alongside AI | AI complementarity 0.55, technical/digital depth 0.15, analytical reasoning 0.15, contextual complexity 0.15 |
| Human and Practical Capability | human relationship capability 0.30, practical real-world application 0.25, contextual complexity 0.25, original judgement/creativity 0.20 |
| Transferable Capability | skill transferability 0.40, career breadth 0.35, analytical reasoning 0.15, contextual complexity 0.10 |

Each measure is a weighted mean of normalised judgments on a 0–10 scale. Contextual complexity contributes to all three on purpose, in a different role in each. Bands are `LOW` below 3.5, `MODERATE` below 7.0 and `HIGH` from 7.0. A measure's factors are the signed effects of its own formula: an effect is material when it moves the measure by at least 0.25 points, and at most two drivers and two limits are kept per measure. Confidence is per measure (the weighted confidence of its own dimensions); a measure below 0.60 is marked lower confidence, and two lower-confidence measures make the profile `READY_WITH_CAUTION`. The information-sufficiency thresholds (0.60 and 0.75) are v1's.

The weights and thresholds are provisional product policy, not validated educational research, and changing any of them needs a new policy version. V23 stores the three measures and every signed contribution, so drivers and limits are never re-selected by a later policy. v1 assessments and their purchases are never recalculated.

### University module analysis (`university-module-v1`)

`coursemodule/university` holds the module-level Jev contract of the paid report: a request of 1 to 8 modules with stable keys `m1`–`m8` (more are refused, never truncated), the `university-module-v1` schema, and a strict adapter (`UniversityModuleJevAutoConfiguration`, `prod` and `live-smoke` only). Preferred career areas are never sent. Each module gets two truth judgments (`described_clearly`, `professional_standards`) and six 0–4 scores (`ai_learning_assistance`, `independent_demonstration`, `human_judgement`, `interpersonal_work`, `practical_application`, `technical_quantitative`).

Decisions locked by the Stage 5C live probe (seven courses, eight modules each, every call returning all 64 expected answers and nothing unexpected):

- **One Jev call for up to eight modules.** No batching fallback. Strict checks remain for missing and unexpected answers, wrong types, invalid ranges and module identity or order.
- **Distributions may differ from 1.0 by at most 0.01**, because providers round probabilities. The comparison carries a 1e-9 floating-point allowance: two live answers summing to 0.99 were first rejected because |0.99 − 1.0| evaluates to 0.010000000000000009. The business tolerance is unchanged, and the course-level `university-v1` check is unchanged.
- **`described_clearly` is diagnostic only**: it never excludes or downgrades a module and never directly determines a classification. Across the 40 modules first accepted it ranged 0.41–0.94 (mean 0.80); the only value below 0.50 was "Approaches to Literature" (0.41), which is still analysable from the course context.

The seven accepted live captures are frozen in `src/test/resources/university/module-probe-v1/` (Computer Science and Mechanical Engineering from their reruns after the tolerance fix), so the policy and guidance tests work offline without further Jev calls.

Findings that shaped the classification policy:

- `ai_learning_assistance` is high almost everywhere (about 2.53–3.78, mean 3.52), so a module is never classified from it alone. Classification combines it with independent demonstration, human judgement, interpersonal work, practical application, professional standards and, where relevant, technical/quantitative context.
- **Invariant:** AI learning assistance describes where AI may support learning or preparation. It does not determine what use is permitted in assessed work. High AI learning assistance together with high independent demonstration is an integrity-sensitive combination, never permission to use AI in assessment; the paid report defers to institution and module policies.

### University module classification (`university-module-policy-v1`, frozen)

`UniversityModulePolicyV1` gives each module one primary category and safeguard flags. On the 0–4 scale HIGH is at least 2.5 (MODERATE from 1.5), a characteristic dominates when it is HIGH and leads the others by 0.25, and comparisons carry a 1e-9 allowance. Only practical application, human demand (the stronger of human judgement and interpersonal work) and technical depth compete; AI learning assistance never does, and `described_clearly` is not read. Rules, first match wins: UM1 practical dominance; UM1B hands-on work with people (practical and interpersonal both HIGH, practical within the margin of human demand and ahead of technical by it); UM2 human judgement; UM3 technical core; UM4 AI with strong independent demonstration (no dominance, AI and independent demonstration HIGH); UM5 AI-assisted learning (nothing HIGH, AI HIGH, independent demonstration not HIGH); UM6 mixed.

Flags never change the category. `INDEPENDENT_DEMONSTRATION_SENSITIVE` needs AI assistance at least 2.5 and independent demonstration at least 3.5 (17 of the 56 live modules). `PROFESSIONAL_STANDARDS_RELEVANT` needs a professional-standards judgement of at least 0.75, or at least 0.50 with practical and interpersonal work both HIGH (8 of 56). An internal close-call note records values within 0.15 of HIGH or leads within 0.15 of the margin; it is never shown to customers.

### University module analysis lifecycle

`UniversityModuleAnalysisService.generateOrGet` produces the one immutable module-analysis snapshot of a paid assessment (V22: `university_module_analysis`, its items, its judgments, and failures). Only the detailed-report service calls it, on the customer's explicit request; the Stripe webhook never does. It requires an ACTIVE entitlement for generation and for reuse; a refunded or revoked entitlement hides a stored snapshot but never deletes it.

A course of eight or fewer modules is always analysed in full, in course order, and any supplied selection is ignored, so a chosen subset cannot distort the analysis. A course of more than eight needs an explicit choice of 1 to 8 module ids (course positions); the preparation page asks for it ("Choose up to 8 modules for your detailed report", nothing pre-selected), and the choice is put back into course order. The form rejects a module listed twice (ignoring case), and a saved course-profile assessment that lists a module twice is not offered checkout or complimentary access, because its detailed report could never be prepared.

With no snapshot, the service makes one Jev call, classifies once with `university-module-policy-v1` and stores the COMPLETED snapshot in one transaction; there is never a PENDING row. A stored snapshot is returned with no Jev call and no reclassification, whatever selection is passed. Generation failures are recorded by category (provider transient, provider invalid response, classification, persistence, internal) and an explicit later request retries; entitlement and selection refusals are traced, not recorded as failures. With no module provider configured (`dev`, `dev-no-jev`) preparation is refused as unavailable. The unique `assessment_id` constraint settles concurrent first requests: both may call Jev, and the loser returns the winner's snapshot.

### University paid guidance (`university-guidance-policy-v2`)

`UniversityGuidancePolicyV1.decide(snapshot)` turns one module-analysis snapshot into deterministic guidance: up to four AI learning opportunities, up to four areas where the student's own capability matters most, up to three technical foundations, aggregated safeguards (independent demonstration, professional standards, checking AI technical output, checking AI claims against sources), three to five skill priorities and a two-to-four-action plan for each of the next 30 days, this term and the next 6–12 months. It reads only the frozen judgments and classifications; titles are data inside fixed wording, never rules, and provider metadata is ignored. Every item records its rule, module ids, categories and evidence. Each action has a strict evidence gate tied to a selected skill theme; generic AI actions never qualify on high AI learning assistance alone, and an action that meets only its looser gate is a marked fallback used solely to reach two actions.

The detailed report uses `UniversityGuidancePolicyV2` (`university-guidance-policy-v2`), which is v1 with one change. v1 threw when critical evaluation was selected only through an independent-demonstration module (for example a final-year project) and no judgement-led or high-interpersonal module existed to name in action M4 ("collect examples of your judgement"). v2 omits M4 in that case and builds the rest of the plan exactly as v1 does. v1 stays frozen for guidance already produced under it. Guidance is not stored on its own: it is the same deterministic function of the stored module analysis on every run, and the detailed report snapshot keeps the guidance it was assembled with. Any change to a rule, template or threshold needs a new policy version.

### University detailed report (`university-report-v3`)

`UniversityDetailedReportService` runs on `POST /university-assessment/{UUID}/report/prepare` for an ACTIVE `university-report-v2` purchase of a `university-decision-v2` assessment:

```text
stored detailed report?  → return it as stored (a v2 document is never upgraded)
otherwise
  module analysis (stored, or the one Jev call)
  + university-guidance-policy-v2
  + one university-paid-narrative-v2 attempt (validated, one repair)
        → UniversityDetailedReportAssembler → university-report-v3 snapshot (V23)
        → paid-report-v3.html, and university-report-pdf-v3 rendered on demand from the snapshot
```

- **Paid narrative.** `university-paid-narrative-v2` explains the frozen findings with module-specific learning uses, independent capability, study experiments, skill interpretation, plan notes and an AI-study strategy, keyed by the deterministic module, skill and action ids. Its request carries the journey's narrative model (`gpt-6-luna` by default). `UniversityPaidNarrativeValidator` is a set of conservative pattern rules, not a language model. It requires every id exactly once in deterministic order and the three course-profile measures with their stored bands only. It rejects reclassification, overall scores or rankings, numbers, salary and employment or replacement predictions, claims about what AI cannot do, claims that AI use is permitted, invented institutional rules or regulation claims, named products or vendors, AI acting with clinical authority (`NO_AI_CLINICAL_AUTHORITY`: diagnosing, prescribing, triaging, deciding treatment or setting doses outside a study frame), vocabulary the course as described does not support, and generic, repeated or restated content. The prompt anchors each entry to its own module's description so entries differ from module to module. Anything rejected is repaired once.
- **Outcomes** are those of the Professional detailed report: AVAILABLE and final FALLBACK are stored with the snapshot; RETRYABLE_FALLBACK (no usable provider response) stores nothing and shows the complete deterministic report provisionally with an "Add the personalised explanation" button. Viewing never retries.
- **Versions.** A v2 purchase stays a v2 purchase; only the stored document is v3. Detailed reports stored before `university-paid-narrative-v2` are `university-report-v2` documents with `university-paid-narrative-v1`, shown with their own template (`paid-report-v2.html`, unchanged) and PDF (`university-report-pdf-v2`).
- **PDF.** The detailed report's PDF is rendered on demand from the stored snapshot, in the PDF version the snapshot records; no PDF bytes are stored for it, and a provisional report has no PDF.
- **Concurrency.** The unique assessment constraints keep one module analysis and one report canonical. Two truly simultaneous first requests may duplicate provider calls; both receive the canonical snapshot.

A historical v1 assessment or v1 purchase is never routed here: it keeps its frozen `university-report-v1` and stored PDF.

### University limitations and next sub-phases

- There is no live university evidence adapter yet. Only the port, model, persistence and deterministic fixture exist.
- There is no prospectus-upload input for the university journey; input is manual only.
- There is no occupation-classification equivalent, because the university evidence request is course-shaped rather than occupation-shaped.
- The detailed report needs the module Jev adapter, so it can be prepared only under `prod` and `live-smoke`.
- As in the professional journey, there are no user accounts. The opaque assessment UUID is a temporary pre-authentication access reference, not production-grade authorisation. Anyone holding a saved URL may reach that assessment and an entitled report. There is no public listing route, but account-bound ownership belongs in a later phase.

## A-level journey

The A-level journey answers a third product question: *what does this subject combination develop, and how many directions does it keep open, in an AI-shaped economy?* It is aimed at UK sixth-form students, who are often 16 to 18 and reading with a parent, so its wording is deliberately the most careful of the three.

```text
A-level input
    ↓
ALevelAssessmentState
    ↓
ALevelJudgmentProvider                (one a-level-v2 Jev call: the whole assessment's only Jev call)
    ↓
ALevelDecisionPolicyV2                (subject profile, no headline)
    ↓
saved assessment + evidence
    ↓
deterministic free result             (no model call)
    ↓
A-level entitlement (a-level-report-v2 purchase)
    ↓
"Prepare your detailed report"        (explicit POST)
    ↓
a-level-guidance-policy-v1 → a-level-paid-narrative-v1
    ↓
a-level-report-v2 snapshot → web page and a-level-report-pdf-v2
```

Sections headed v1 describe the historical path that assessments decided before `a-level-decision-v2` still follow when they are read back.

### A-level input and subject canonicalisation

`ALevelAssessmentState` is a dedicated immutable domain record with no JPA annotations: selected subjects, optional predicted grades, strengths and interests, preferred degree areas, preferred career areas, and optional country and free-text context. Only the subjects are required, and between two and five distinct subjects are accepted (UK students usually take three or four). A student is never required to know a degree or a career: every other field may be empty.

`ALevelSubjects.canonicalise` maps common abbreviations and spellings onto consistent display names — `maths` and `Maths` become `Mathematics`, `comp sci` and `computing` become `Computer Science` — and is applied inside the record's constructor so the same combination is stored, judged and displayed identically however it was typed. It is idempotent, and an unrecognised subject is accepted and title-cased rather than rejected, so a student is never blocked because their school offers something unusual.

The alias table **maps text to text only**. There is no subject-points table anywhere in the journey: neither decision policy ever learns that Mathematics exists, and the local fixture is keyed by a configured identifier rather than by the subjects submitted. `ALevelSubjectsTest` asserts by reflection that the alias table holds no numeric value, and `ALevelDecisionPolicyV1Test` asserts that two completely different subject lists produce an identical result for the same fixture. Together these pin the rule that the journey scores a judged combination, never a ranked list of subjects.

### A-level judgment schema v2 (`a-level-v2`)

`ALevelJudgmentSchemaV2` is the contract of every new assessment: one Noul (`information_sufficient`) and nine 0–4 Scores, every one about the **subject combination** only. The request carries the canonical subject names and nothing about the student, so no question can be answered from grades, interests or ambitions. `ALevelV2JevJudgmentService` issues it as one Jev System One request. Changes from `a-level-v1`, which is untouched and still describes historical assessments:

- The six capability questions (`quantitative_reasoning`, `analytical_reasoning`, `written_verbal_communication`, `original_judgment_creativity`, `contextual_social_understanding`, `technical_digital_foundation`) and `subject_skill_diversity` are kept, reworded to name the subjects.
- `degree_pathway_breadth`, a product conclusion, is replaced by `discipline_family_coverage`: a countable observation from which Java derives breadth.
- `skill_transferability` is removed: it was holistic, asked about work contexts and overlapped breadth and diversity.
- `ai_era_complementarity` is replaced by `ai_learning_support`: how far AI can support *learning* in these subjects while the student still develops their own understanding. The meaning is materially different, so it does not reuse the v1 identifier.

Nothing in either schema asks about university entry requirements: those are external facts for the evidence layer.

### A-level judgment schema v1 (historical assessments)

`ALevelJudgmentSchema` defines `a-level-v1` as one Noul plus ten 0–4 Score questions, issued in a single Jev System One request. Every rubric level is defined explicitly, not only 0 and 4:

| Dimension | 0 | 4 |
| --- | --- | --- |
| `information_sufficient` | Noul: too vague to tell which subjects are taken | Subjects identified clearly enough to assess the capabilities developed |
| `quantitative_reasoning` | Little mathematical or quantitative capability | Rigorous mathematical, statistical or quantitative reasoning throughout |
| `analytical_reasoning` | Little structured analysis or problem solving | Disciplined analysis and problem solving define the combination |
| `written_verbal_communication` | Little extended communication or argumentation | Sustained extended writing, argument and explanation |
| `original_judgment_creativity` | Mainly fixed procedure or reproduction | Original interpretation, synthesis, creativity or design |
| `contextual_social_understanding` | Little engagement with human/social/economic context | Interpreting complex human, social, economic or geographical systems |
| `technical_digital_foundation` | Little technical, computational or digital foundation | A strong technical, computational or digital foundation throughout |
| `subject_skill_diversity` | The subjects develop very similar kinds of capability | Markedly diverse capability types across the combination |
| `degree_pathway_breadth` | A very narrow range of plausible degree directions | A very broad range of plausible degree directions |
| `skill_transferability` | Relatively narrow, tied to a particular context | Transfers broadly across many study and work contexts |
| `ai_era_complementarity` | A limited foundation for AI-assisted study or work | A particularly strong mix likely to combine productively with AI tools |

The schema never asks whether the subjects are good, whether the student will get a job, whether they should drop a subject, or whether the combination is future-proof. `ALevelJudgmentSchemaTest` asserts those phrasings are absent.

### Why degree prerequisites are evidence, not a judgment

Whether a course requires A-level Chemistry, Mathematics or anything else is a **fact with a publisher and a date**, not a model judgment. It changes by university, by course and by year, a model cannot know it reliably, and getting it wrong could change a teenager's subject choices for the worse.

The separation is enforced in four places:

1. No `a-level-v2` question mentions requirements at all. In `a-level-v1`, the `degree_pathway_breadth` question explicitly instructed the model to judge only the general capability profile and **not** to apply any specific university's published entry requirements.
2. Neither decision policy has a concept of a prerequisite; both combine capability judgments only. `ALevelGuidancePolicyV1` never states an entry requirement, grade, acceptance, accreditation rule or admission likelihood.
3. `ALevelPaidNarrativeValidator` (and, for v1 assessments, `ALevelNarrativeValidator`) rejects any narrative that states a requirement, an eligibility or admission claim, or a grade requirement such as `AAA`. The safe phrasing — telling the student to check a course's own published requirements — is explicitly permitted and tested.
4. The evidence layer has a dedicated `DEGREE_PREREQUISITES` category, and every item carries a source, publisher, geography and date.

The report keeps fact and assessment visually and structurally separate. Evidence carries a standing note: *"These are factual notes with their own sources. They are shown separately because subject requirements are facts to check, not part of your assessment, and they do not change any measure above."* A prerequisite note is therefore presented as something to look up, never translated into "your subject choices are bad".

### A-level decision policy v2: the subject profile

`ALevelDecisionPolicyV2` (`a-level-decision-v2`) decides every new assessment from `a-level-v2` judgments. It is a subject profile with no composite headline score, designed and calibrated offline in Stages A1/A2:

| Measure | Weights |
| --- | --- |
| Options Kept Open | discipline family coverage 0.60, subject skill diversity 0.40 |
| Capability Profile: Analytical & Quantitative | quantitative reasoning 0.40, analytical reasoning 0.40, technical/digital foundation 0.20 |
| Capability Profile: Communication, Interpretation & Context | written/verbal communication 0.40, original judgement/creativity 0.30, contextual/social understanding 0.30 |
| Learning Alongside AI | AI learning support 0.70, analytical reasoning 0.15, original judgement/creativity 0.15 |

The two Capability Profile sides are shown together and never added up; a lighter side is described as the shape of the combination, never as a weakness. Learning Alongside AI takes one input from each side so it does not become another analytical score. Thresholds, materiality, factor counts and confidence rules are those of `university-decision-v2`: bands `LOW` / `MODERATE` / `HIGH` at 3.5 and 7.0, material factors from 0.25 points, at most two drivers and two limits per measure, per-measure confidence with a lower-confidence mark below 0.60. The weights are provisional product policy, not validated educational research, and changing any of them needs a new policy version. V24 stores the four scored measures and every signed contribution. The policy names no subject and calls no provider.

**Live calibration gate.** Before the v2 report could be released, seven representative combinations had to be judged by live Jev and checked against the policy. `ALevelV2LiveCalibrationIT` (Maven profile `live-a-level-calibration`, never part of `./mvnw verify`) captures them with the production client settings; see [LIVE_SMOKE_TESTING.md](LIVE_SMOKE_TESTING.md#a-level-v2-live-calibration-capture). A capture was run on 6 November 2026 against `jev-latest` and reported as passing the gate. It has not yet been promoted into the repository: `ALevelV2CalibrationTest` still runs on the synthetic `src/test/resources/a-level/calibration-v2/offline-calibration.json`.

### A-level decision policy v1 (historical assessments)

`ALevelDecisionPolicyV1` (`a-level-decision-v1`) is pure deterministic Java: no Jev SDK, no OpenAI, no persistence, no evidence. Weight maps are validated at class-initialisation time to sum to exactly 1 and to reference only known dimensions.

| Measure | Weights |
| --- | --- |
| `FUTURE_OPTIONALITY` | degree pathway breadth 0.250, skill transferability 0.200, analytical reasoning 0.150, quantitative reasoning 0.100, subject skill diversity 0.100, original judgement/creativity 0.050, written/verbal communication 0.050, contextual/social understanding 0.050, technical/digital foundation 0.025, AI-era complementarity 0.025 |
| `DEGREE_PATHWAY_BREADTH` | degree pathway breadth 0.75, subject skill diversity 0.25 |
| `ANALYTICAL_FOUNDATION` | analytical reasoning 0.40, quantitative reasoning 0.40, technical/digital foundation 0.20 |
| `HUMAN_AND_CREATIVE_STRENGTH` | written/verbal communication 0.35, original judgement/creativity 0.35, contextual/social understanding 0.30 |
| `SKILL_TRANSFERABILITY` | skill transferability 0.45, subject skill diversity 0.25, analytical reasoning 0.15, written/verbal communication 0.15 |
| `AI_ERA_ADAPTABILITY` | AI-era complementarity 0.35, analytical reasoning 0.20, skill transferability 0.20, technical/digital foundation 0.15, original judgement/creativity 0.10 |

These weights are transparent, **provisional V1 product policy**. They are not educational science, they are not validated against outcome data, and they do not represent any admissions body's view. Changing any of them requires a new policy version.

Breadth and transferability dominate Future Optionality because they are the most direct expressions of optionality; analytical and quantitative reasoning follow as gateway capabilities for a wide span of degrees; diversity contributes because variety itself preserves options. The remaining capability types contribute modestly to the headline but are carried in full by their own supporting measures, so a combination strong in communication or creativity is not under-reported overall — `english_history_politics` scores `BROAD` on Human and Creative Strength while sitting mid-range on the headline.

Bands are `FOCUSED` below 3.50, `BALANCED` below 7.00, and `BROAD` at or above 7.00. `FOCUSED` is deliberately neutral: a combination that concentrates on one direction is a legitimate choice, not a mistake. There is no `NARROW`, `WEAK`, `BAD`, `SAFE` or `FUTURE_PROOF` band.

Sufficiency and confidence reuse the shared enums on the same thresholds as the other journeys: below 0.60 produces `MORE_INFORMATION_REQUIRED` with no measures; 0.60 to below 0.75 produces `READY_WITH_CAUTION`; 0.75 and above produces `READY` only when confidence is `HIGH`, which additionally requires sufficiency at or above 0.80, mean judgment confidence at or above 0.80 and minimum judgment confidence at or above 0.60.

### A-level explainability (v1)

Explainability uses signed contribution logic over a separate `EXPLANATION_WEIGHTS` map covering all ten Score dimensions (summing to 1). It is deliberately flatter than the Future Optionality weights so a capability carried mainly by a supporting measure can still be named as a strength, and it never contributes to an authoritative measure. For each dimension the signed effect is `weight × (normalised − 0.5)`; positive effects become `STRENGTH` factors and negative effects `DEVELOPMENT_OPPORTUNITY` factors, so a dimension can never appear on both sides.

Factor wording explains capability contribution rather than judging a subject: "Your combination gives you a strong quantitative foundation", or "Your combination develops less technical and digital depth than some alternatives". Nothing says "Maths is good" or "Geography is weak", and no factor names a subject at all.

### A-level persistence and migrations

Flyway **V13** adds `a_level_assessment` with input, provenance and headline decision, plus ordered judgment, measure and factor tables; a check constraint pins both journey columns to `A_LEVEL`. **V14** adds `a_level_assessment_narrative`, **V15** the evidence bundle and items, **V16** the entitlement, purchased snapshot and processed payment event, and **V17** the purchased PDF. A-level tables are entirely separate from professional and university tables. **V24** adds the `a-level-decision-v2` measures and contributions of new assessments (a v1 assessment keeps its six measures and has no V24 rows; a v2 assessment has no rows in the v1 measure and factor tables), and **V25** adds the stored `a-level-report-v2` detailed report. The repository is at Flyway **V25**; see [systemdesign.md](systemdesign.md) for the full list.

Selected subjects, strengths, degree areas and career areas are JSONB arrays (order preserved); predicted grades are a JSONB object keyed by canonical subject (PostgreSQL does not preserve jsonb object key order, so grade display order follows the subject array). `ALevelAssessmentStore` inserts only, and `GET /a-level-assessment/{UUID}` loads saved records without invoking any provider or policy.

### A-level free narrative (v1 assessments only)

An `a-level-decision-v2` assessment has a deterministic free result (`ALevelSubjectProfileComposer`) and no free narrative: submission makes one Jev call and no OpenAI call. The free narrative below exists only for historical v1 assessments.

`ALevelNarrativeService` runs after the assessment transaction commits, building `ALevelNarrativeContext` from the persisted snapshot. `OpenAiALevelNarrativeGenerator` is the normal adapter with one typed structured-output request; `LocalFixtureALevelNarrativeGenerator` is selected only by `dev`. Profile wiring permits exactly one generator, and a failure stores `FAILED` rather than looking up another implementation. Historical narratives are never regenerated or upgraded.

The free narrative prompt (`a-level-narrative-v2`, after `-v1`) states that saved measures, bands, confidence, status, strengths and development opportunities are authoritative and must never be recalculated or repeated numerically. It forbids stating university entry requirements or subject prerequisites, quoting grade requirements, guaranteeing or predicting admission, inventing salary figures or employment rates, citing studies, calling a combination future-proof/safe/risky/wrong/bad, ranking subjects, telling the student to drop or change a subject, telling them which career to pursue, and saying their choices will limit their future. It requires explaining `FOCUSED` as a legitimate choice rather than a limitation, and restricts practical actions to things a student can do *alongside* existing subjects.

`ALevelNarrativeValidator` is the deterministic backstop: it rejects numeric and percentage claims, currency figures, invented prerequisites and grade requirements, and admission promises, subject verdicts or drop instructions. Rejection fails the narrative attempt and can never change the saved assessment.

### A-level evidence

`ALevelEvidenceProvider` is the vendor-neutral port receiving a narrow request (assessment UUID, subjects, preferred degree and career areas, explicit geography, journey). Categories are `DEGREE_PREREQUISITES`, `COURSE_ENTRY_CONTEXT`, `SUBJECT_PATHWAY_CONTEXT`, `LABOUR_MARKET_CONTEXT` and `SKILL_DEMAND_CONTEXT`; the version is `a-level-evidence-v1`. Evidence cannot change any measure, status or confidence, and a provider failure is recorded as `FAILED` with no fallback. Geography comes from the submitted country when supplied, otherwise the configured default.

No live A-level evidence adapter exists. The request contains subjects and preferred areas, without a specific university, course and entry year. Individual course providers set their own entry requirements, so general knowledge cannot establish a prerequisite or admission guarantee. Prompt 18B chose verified ingestion of official course/admissions pages with provider, course, year, source URL and review date as the safer future strategy; generic search scraping and cross-site CSS crawlers are excluded. Degree prerequisites, course entry context, subject pathway context, labour-market context and skill-demand context remain unavailable as live evidence. Fixture items are framed as neutral factual context, carry no fake university or government URL, and state explicitly that they do not change the assessment. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md).

### A-level detailed report (`a-level-report-v2`)

`ALevelDetailedReportService` runs on `POST /a-level-assessment/{UUID}/report/prepare` for an ACTIVE `a-level-report-v2` purchase of an `a-level-decision-v2` assessment. **No Jev call ever happens here**: the subject profile was decided once, at submission, and there is no per-subject analysis, so one A-level assessment is one Jev call in total.

```text
stored detailed report?  → return it as stored
otherwise
  frozen subject profile + the student's guidance-only context
  → a-level-guidance-policy-v1 (deterministic)
  → one a-level-paid-narrative-v1 attempt (validated, one repair)
  → ALevelDetailedReportAssembler → a-level-report-v2 snapshot (V25)
  → paid-report-v2.html, and a-level-report-pdf-v2 rendered on demand from the snapshot
```

- **Guidance.** `ALevelGuidancePolicyV1` (`a-level-guidance-policy-v1`) decides the combination's shape, strengths, areas to strengthen, how to read Options Kept Open (what the assessment can and cannot say, and the checks the student's own preferences call for), AI study, skill priorities, an action plan and safeguards. The student's strengths, interests, preferred areas and grades only personalise wording and actions; they never change a score, band or factor. It names no subject, has no subject-specific branch, and never states an entry requirement, grade, acceptance, accreditation rule, admission likelihood or career outcome. Every item names its provenance.
- **Paid narrative.** `a-level-paid-narrative-v1` writes five short fields (profile, capability, options and AI-learning explanations, and an action-plan introduction) explaining the deterministic report. `ALevelPaidNarrativeValidator` rejects missing fields, numbers, scores or percentages, an overall score or ranking, a band other than the stored one, admission or eligibility claims, invented entry or grade requirements, employment or salary claims, claims about what AI cannot do, permission to use AI in assessed work, and superseded v1 terminology. Anything rejected is repaired once.
- **Outcomes, freezing and PDF** follow the University detailed report: AVAILABLE and final FALLBACK are stored, RETRYABLE_FALLBACK shows the deterministic report provisionally and stores nothing, the PDF is rendered on demand from the stored snapshot, and a retry reuses the frozen assessment and re-derives the same guidance so only the narrative can change.
- **Freezing at payment.** A v2 purchase records only a marker in `purchased_a_level_report_snapshot`; its report is stored once prepared.

### A-level report, payment, freezing and PDF (v1 purchases, and payment for both)

`ALevelReportAssembler` is pure deterministic composition of saved records (`a-level-report-v1`) for historical v1 assessments. FREE and PAID use the same saved status, confidence, six measures, bands and factors. FREE includes the subjects, all measures with concise definitions, main strengths, development opportunities, a short interpretation and the admissions disclaimer. PAID adds the full narrative, per-measure explanations, practical actions, deterministic guidance, degree and career-area context, saved evidence and fuller methodology.

Practical guidance uses `KEEP_DEVELOPING`, `WORTH_STRENGTHENING` and `CONSIDER_SUPPLEMENTING`. Every item is something a student can do alongside their existing subjects — building coding or data literacy, strengthening written argument, researching course requirements early — and `ALevelReportAssemblerTest` asserts across all six fixtures that no guidance ever suggests dropping, swapping or retaking a subject. Every combination is told to check real published course requirements rather than trust a guess.

A-level entitlement is entirely separate from professional and university entitlement: separate tables with foreign keys to `a_level_assessment`, a separate `A_LEVEL_REPORT_STRIPE_PRICE_ID`, a separate webhook at `POST /payments/stripe/a-level-webhook`, a separate configurable webhook secret, and `product=a_level_report` Stripe metadata so events for other products are ignored rather than errored. No price is hard-coded. Confirmation is idempotent under a row lock, and the frozen snapshot is authoritative after purchase, so `a-level-report-v2` cannot change an earlier `v1` purchase. Complimentary access and the payment-status poller work as in the other journeys; Stripe is active under `prod` and `dev-no-jev`, the simulated checkout under `dev`.

For a v1 purchase, `ALevelReportPdfService` renders only from the frozen purchased snapshot, never calling the assembler, Jev, the policy, OpenAI, evidence or Stripe. `OpenHtmlALevelReportPdfRenderer` uses built-in fonts and inline CSS with no network inputs; persisted evidence URLs are emitted as hyperlinks and never fetched. `a-level-report-pdf-v1` versions the layout separately from the purchased content, and `ON CONFLICT DO NOTHING` makes the stored artifact immutable, so repeat downloads and restarts return identical bytes.

### A-level web routes, UX and local development

| Route | Purpose |
| --- | --- |
| `GET /a-level-assessment` | Public form |
| `GET /a-level-assessment/example` | A worked example of a completed form |
| `POST /a-level-assessment` | Validate, submit once, Post/Redirect/Get |
| `GET /a-level-assessment/{UUID}` | Read-only free result |
| `POST /a-level-assessment/{UUID}/checkout` | Start checkout (CSRF-protected) |
| `POST /a-level-assessment/{UUID}/complimentary-access` | Redeem the complimentary code; 404 while disabled |
| `GET /a-level-assessment/{UUID}/payment-status` | Read-only entitlement status (JSON) for the confirmation poller |
| `GET /a-level-assessment/{UUID}/report` | Paid report, `ACTIVE` only: the stored detailed report, the preparation step, or a frozen v1 report |
| `POST /a-level-assessment/{UUID}/report/prepare` | Prepare the detailed report (CSRF-protected) |
| `GET /a-level-assessment/{UUID}/report.pdf` | PDF of the stored report, `ACTIVE` only |
| `GET`/`POST /a-level-assessment/{UUID}/checkout/local[/confirm]` | `dev` only |
| `POST /payments/stripe/a-level-webhook` | `prod` and `dev-no-jev`, signature-authenticated |

The form opens by saying there are no good or bad subjects and that the student does not need to know what they want to do yet. Degree and career areas are explicitly optional and labelled "It is completely fine to be undecided". A combination with too few subjects is a friendly field error, not a server error. The result page explains what each profile measure means before showing any number, describes a lighter capability side as the shape of the combination rather than a weakness, and tells the student to check a specific course's own published requirements. Result responses use `private, no-store` and request `noindex,nofollow`; CSRF is preserved for every browser POST.

For local startup, run `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. `app.a-level.fixture-id` (`A_LEVEL_FIXTURE_ID`, default `maths_economics_geography`) supports only `maths_economics_geography`, `mathematics_physics_computer_science`, `biology_chemistry_mathematics`, `english_history_politics`, `art_design_media` and `mixed_balanced`. There is no fuzzy matching: a missing or unsupported ID fails at startup. Each journey wires its own fixture provider independently under `dev` and `dev-no-jev`, and the fixture supplies `a-level-v2` judgments for new assessments. Fixture data is illustrative development data chosen to exercise contrasting presentation paths — it is not an empirical claim about any subject or combination.

### A-level limitations and next sub-phases

- There is no live A-level evidence adapter yet. Only the port, model, persistence and deterministic fixture exist, so no real prerequisite is shown to anyone today.
- The live calibration capture of 6 November 2026 has not yet been promoted into the repository's calibration fixture (see the decision policy section).
- A landing page and journey selector now exist; see [Product shell](#product-shell-landing-page-and-shared-ux) below.
- Predicted grades are recorded as audit context only. They never affect a measure, and the journey makes no attempt to predict an offer.
- As in the other journeys, there are no user accounts. The opaque assessment UUID remains a temporary pre-authentication access reference.

## Product shell, landing page and shared UX

Three strong vertical slices are not yet a product. This layer is the container they are presented inside. It adds navigation and a consistent voice; it adds no assessment logic, no new measure, no new band and no new access rule.

### What the shell is, and what it deliberately is not

- It **is** a landing page, a shared header and footer, one stylesheet, a consistent free/paid story, friendly error pages, and two informational pages.
- It is **not** an account system, a dashboard, a cross-journey comparison, or a place where results are aggregated. There is still no login, and the assessment UUID remains the only access reference.
- No Jev schema, rubric, policy weight, band, narrative prompt or evidence semantic was changed in this phase. The three journeys remain mutually unintelligible by design — see [Why a shared platform does not imply shared score semantics](#why-a-shared-platform-does-not-imply-shared-score-semantics).

### Routes

| Route | Purpose | Access |
| --- | --- | --- |
| `GET /` | Landing page and journey selector | public |
| `GET /methodology` | How an assessment is produced, and what it cannot tell you | public |
| `GET /privacy` | What is kept, what is not, and why links are the access mechanism | public |
| `/error` | Shared friendly error view | public |

These four, with the `/css/**` and `/js/**` static assets, are the shell's public routes. Every journey permit rule, the CSRF requirement on browser POSTs, the Stripe webhook exemptions, the profile restrictions on local payment routes and the ACTIVE-entitlement requirement for paid reports and PDFs are untouched.

### The journey selector

The landing page ("How will AI affect your career or education?") offers three journey cards, each in the visitor's own words rather than the system's vocabulary:

| Card | Journey | Question on the card | Detailed report |
| --- | --- | --- | --- |
| Professional (In work) | `/professional-assessment` | How could AI affect my current job or tasks? | £4.99 |
| University (Applying or studying) | `/university-assessment` | Is my university course preparing me for an AI-driven future? | £2.99 |
| A-level (Choosing subjects) | `/a-level-assessment` | Which opportunities could my A-level choices open up? | £1.99 |

Each card states its own question and links only to its own journey. Nothing on the page invites the visitor to compare the three results, because their numbers do not share a rubric or a meaning. The prices on the page are display text; the Stripe Price is authoritative for the charge.

Known gap: the University and A-level cards still describe the v1 results ("Five measures", "Six measures") and the page's sample result shows v1 University measures. New assessments now return the three- and four-measure v2 profiles, so this copy needs updating.

### Shared chrome

`templates/fragments/layout.html` provides `head(pageTitle)`, `siteHeader(active)`, `devBanner`, `fixtureNotice` and `siteFooter`, included with plain `th:replace` — there is no layout dialect and no new templating framework. Every page template includes the same fragments, exposes a single `<h1>`, and carries a skip link to `#main`. Under `live-smoke` a separate `LiveSmokeShellAdvice` adds the red LIVE SMOKE TEST MODE banner.

`ProductShellAdvice` is a `@ControllerAdvice` supplying `developmentMode`, `developmentFixtures` and `currentYear` to every view. It resolves both of its collaborators through `ObjectProvider` and falls back to `DevelopmentModeStatus.inactive()` and `Clock.systemUTC()`. That keeps sliced `@WebMvcTest` classes working without each one declaring the bean, and — more importantly — makes the *safe* state the default: a missing bean renders no development chrome rather than leaking it.

### Development mode is a profile fact, never a request fact

`DevelopmentModeStatus` is derived solely from the `dev` profile, through two mutually exclusive profile-scoped beans in `ProductShellConfiguration` (active under `dev`; inactive under `prod`, `dev-no-jev` and `live-smoke`). It can never be influenced by a request parameter, a header or a cookie. Under any other profile the banner, the fixture notice and the local-checkout controls are structurally absent, not merely hidden with CSS.

One subtlety worth recording, because it leaked once and was caught by a test: Thymeleaf passes plain `<!-- ... -->` comments straight through into the response. Every template comment is therefore the parser-level form `<!--/* ... */-->`, and `ProductShellConsistencyTest` fails the build if a plain comment reappears.

### Styling

`static/css/app.css` is a single hand-written stylesheet built on CSS custom properties for colour, spacing, radius and shell width. There is no UI framework, CDN asset or npm dependency. The pages are server-rendered HTML and remain usable with JavaScript disabled.

Two small first-party scripts, served from `/js/` and each included only by the pages that use it, add progressive enhancement:

- `payment-confirmation.js` polls the read-only payment-status route after a return from Stripe (see the Professional payment section).
- `long-running-submit.js` shows a loading state while a slow synchronous form post runs (below).

Each is a dependency-injected factory exported for Node's built-in test runner (`src/test/js/*.test.js`), which `PaymentConfirmationScriptTest` and `LongRunningSubmitScriptTest` run inside `./mvnw verify` (skipped when `node` is absent).

### Waiting for a long-running submission

A free assessment and a detailed-report preparation are ordinary synchronous form posts that can take from several seconds to a minute. Pages include `fragments/loading :: panel('assessment')` or `panel('report')` once and mark each such form with `data-long-running`. `LongRunningSubmitTemplatesTest` pins exactly which forms are wired: the three free-assessment forms, the University, A-level and Professional preparation forms, and the "Add the personalised explanation" retry on provisional reports. Checkout, payment, complimentary-access and live-smoke forms never are.

Once the browser accepts a valid submission, `long-running-submit.js`:

- moves the overlay to the end of `<body>` and shows it fixed over the whole viewport;
- leaves the page in view behind it, dimmed and softly blurred (`backdrop-filter`, with a darker plain tint where that is unsupported);
- makes everything else on the page `inert`, so nothing underneath can be clicked, focused or announced;
- blocks a second submission, marks the button `aria-disabled` at once and disables it just after the request starts, so no submitted field is dropped;
- marks the form `aria-busy`, moves focus once to the overlay heading, and rotates static reassurance messages in a polite live region every four seconds, stopping on the last.

The overlay shows a spinner, the heading, the rotating message, a thin indeterminate bar with no value (no percentage, because the browser cannot know how far the server has got) and supporting text. Every word is static template text. Under `prefers-reduced-motion` the spinner and bar stop. A page restored from the back/forward cache (`pageshow` with `persisted`) is reset to its usable state. The server request itself is unchanged: no polling, queue or asynchronous job.

Accessibility and responsiveness basics are structural rather than decorative: a skip link on every page, `aria-current="page"` on the active primary destination, a single `<h1>` per page, form errors announced with `role="alert"` and prefixed by a textual `Error:` through `::before` so the error is never conveyed by colour alone, and a single `max-width: 42rem` breakpoint that collapses grids and stacks definition lists. `prefers-reduced-motion` is respected.

### Free and paid, said the same way everywhere

Every result page uses the same ordering — headline, how to read the numbers, the measures, why, a short interpretation, the free-versus-full comparison, then context — and every one carries its own journey-specific caveat about what its indicator is not. The paid tier is framed consistently as **payment unlocks depth, not truth**: the deterministic assessment a visitor sees for free is the same assessment, and paying adds explanation, personalised actions and evidence rather than a different or truer answer.

Prices are only ever read from existing configuration. No monetary amount is hard-coded into domain logic, and no page fetches live Stripe data during an ordinary render.

The PDF call to action exists only inside the paid report templates (`paid-report.html` and its `-v2`, `-v3` and `-v4` versions), which render only for an ACTIVE entitlement. `ProductShellConsistencyTest` pins this as an invariant across every template, so a free result or an unavailable-report page cannot grow a download link by accident.

### Error and not-found experience

`templates/error/404.html`, `403.html` and `5xx.html` (plus a top-level `error.html`) use the same shell and the same plain voice, and expose no stack trace, exception name, provider name, database detail or configuration value. A malformed assessment identifier and an unknown one produce byte-identical responses, so nothing signals to an enumerator that one guess was closer than another.

### Routing, 404 and the absence of accounts

The authorisation rules used to end in `anyRequest().authenticated()`, which meant a URL matching no route was handed to a generated login page — for a product with no accounts to log into. The rules now end in `permitAll`, so an unmatched request reaches Spring MVC and becomes an ordinary 404 rendering the friendly not-found page.

That catch-all is permissive only because nothing here is protected by authentication in the first place. Paid reports and their PDFs are protected by an ACTIVE entitlement checked inside their controllers, and `ProductRouteSecurityTest` proves the distinction end to end: the filter chain lets the request through, and the application layer answers 403. Three outcomes are deliberately kept apart rather than collapsed into one handler:

| Situation | Response |
| --- | --- |
| Unknown route | 404, friendly page, no redirect, no `WWW-Authenticate` |
| Browser POST with a missing or stale CSRF token | 403 |
| Paid report or PDF without an ACTIVE entitlement | 403 |

Spring Boot creates an in-memory user and prints its generated password on every start whenever the context holds no authentication bean, and declaring a `SecurityFilterChain` does not stop it — the application was logging a password for a sign-in that does not exist. `WebSecurityConfiguration` therefore supplies a `UserDetailsService` that can never find a user, which backs the auto-configuration off without touching the application class. There is no form login, no HTTP Basic and no logout.

Operational endpoints under `/actuator/**` are denied. They were already unreachable, and deciding what to publish belongs to the operational hardening phase rather than to this routing change.

### Security headers

Every response carries a `Content-Security-Policy` that starts from `default-src 'self'` and allows scripts and styles from this origin only (`script-src 'self'`, `style-src 'self'`), with no inline script, `eval` or inline style allowance; the two first-party scripts are what make `'self'` necessary. `object-src 'none'`, `base-uri 'self'` and `frame-ancestors 'none'` complete it. `form-action` names Stripe's hosted checkout, because paying leaves the site through a redirect that follows a form submission. Alongside it: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and a `Permissions-Policy` that switches off camera, microphone, geolocation and the rest. The PDF response keeps its own `private, no-store` and attachment disposition.

### Request hardening

Three filters and a set of bounds sit in front of the journeys. They are documented in full in [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md); the parts that affect how the application is put together are:

- **Correlation runs first.** `RequestCorrelationFilter` is at highest precedence, so a request rejected by security still carries an `X-Request-ID` in its response and in every log line. An inbound identifier is reused only if it matches a narrow pattern, because it reaches both a log line and a response header.
- **Rate limiting runs after security.** `RateLimitFilter` is ordered behind the Spring Security chain so a missing or stale CSRF token stays a 403 and never spends an allowance. The limiter is in-process and keyed on the container-reported client address; it is deliberately not reading forwarded headers while no trusted proxy is configured.
- **Collections are bounded as well as characters.** `@BoundedTextList` caps how many entries a textarea may produce and how long any one may be, counting them exactly as the form mappers split them. A character limit alone leaves the number of judged, stored and rendered list entries unbounded.

### Production profile and configuration

The `prod` profile selects every real provider adapter and carries the production-only settings — secure cookies, forwarded-header handling, constrained Actuator exposure, quieter and structured (ECS JSON) logging, connection-pool hooks and Flyway validation — and nothing else. Everything the application needs in every environment stays in the base property file, so there is one place to read for what a setting does.

`StartupConfigurationValidator` refuses to start on a contradiction or a gap. The application supports exactly one of `dev`, `dev-no-jev`, `live-smoke` and `prod`; combining any two is a request it cannot satisfy as asked, so it stops rather than silently choosing. Missing configuration is checked per profile: `prod` needs every credential, the Stripe settings, the occupation mapping and a valid public base URL; `dev-no-jev` needs the OpenAI key and the Stripe settings; `live-smoke` needs both AI keys and `APP_LIVE_SMOKE_ENABLED=true`; `dev` needs nothing. Wherever real narratives run, each journey's narrative model must be on its allowlist. No failure message ever repeats a configured value.

`PublicBaseUrl` is the single authoritative address, and the three per-journey report base URLs now default to it. Stripe return links are built from configuration and never from a `Host` header, because a header is chosen by the caller.

### Operational surface

Health is published and nothing else is. Liveness depends on no external provider — a Stripe or OpenAI outage must not look like a dead process — while readiness includes the database, without which no request can be served usefully. Health reports a bare status with no component detail, and `/actuator/**` is denied in the filter chain in addition to being unexposed, so publishing an endpoint by accident in configuration is not enough to make it reachable.

### Observability

Three signals exist today: correlated logs, structured production logging and in-process metrics. There is no tracing; for one process and one database the request id and the timers below answer the same questions more cheaply.

**Correlated logs.** `RequestCorrelationFilter` puts the request id into the MDC, and every log line carries it. Log lines use stable `key=value` fields (`journey=… outcome=… failureType=…`) and identify work by assessment, entitlement or Stripe event id, never by user content. SQL bind parameters, Stripe and HTTP-client logging stay at `WARN` in `prod` because a bind parameter is somebody's job title.

**Structured logging in production.** Under `prod` the console log is Elastic Common Schema JSON (`logging.structured.format.console=ecs`), one object per line, with MDC entries such as `requestId` as fields. Other profiles keep the readable text pattern. Logging configuration is JVM-wide, so a test that captures output after a `prod`-profile context sees JSON; assertions on log lines must accept both shapes.

**Failure descriptions.** Application exceptions wrap the real failure, so an outer class alone says "checkout failed" for an authentication error, a bad price and a timeout alike. Error lines on the checkout, PDF and Stripe webhook paths use `FailureDescription`, which renders the cause chain as class names only — `PaymentUnavailableException<-ApiConnectionException`. Messages are never logged, because a provider or library message can echo request content.

**Metrics.** `JourneyMetrics` (in `configuration`) is the only place meters are created:

| Meter | Kind | Tags | Recorded by |
| --- | --- | --- | --- |
| `assessments.created` | counter | `journey` | assessment workflows |
| `judgment.failures` | counter | `journey` | assessment services, when the judgment provider throws |
| `narrative.failures` | counter | `journey` | assessment workflows |
| `evidence.failures` | counter | `journey` | assessment workflows |
| `checkout.failures` | counter | `journey` | checkout controllers, whenever they answer 503 |
| `payment.confirmations` | counter | `journey` | purchase services |
| `pdf.generation.failures` | counter | `journey` | PDF controllers, whenever they answer 503 |
| `rate_limit.rejections` | counter | `group` | `RateLimitFilter` |
| `external.calls` | timer | `operation`, `journey`, `outcome` | the application service that owns each outbound call |

`external.calls` wraps exactly the calls that leave the process: `judgment` (Jev), `narrative` (OpenAI), `evidence` (evidence provider) and `checkout` (Stripe). `outcome` is `success` or `failure` — returned or threw — so a narrative that returns but fails validation is a timer success and a `narrative.failures` increment. The operation names a step rather than a vendor, so fixture-backed development runs are not mislabelled as Jev or OpenAI traffic. Spring's own observations (`http.server.requests`, Hikari, JVM, Spring AI client timings) are registered automatically alongside these.

The rules that keep this safe:

- **Bounded cardinality.** Tags are a journey, a rate-limit group, a fixed operation name and an outcome. An assessment id, profession, course title or address as a tag would create unbounded series and turn a metrics backend into a second, unaudited copy of user data.
- **Never load-bearing.** Every recording method swallows its own failure, and `timed` passes the wrapped call's result or exception through unchanged. A broken registry cannot fail an assessment, a payment, a report or a PDF.
- **Optional wiring.** Services take `JourneyMetrics` through an additional constructor used by Spring configuration; the original constructors delegate with `JourneyMetrics.disabled()`. Controllers resolve it through `ObjectProvider`, so a sliced `@WebMvcTest` without a registry still loads. `JourneyMetrics` is final and is not mocked; tests that assert on it import `RecordingJourneyMetricsConfiguration`, which supplies a real in-memory registry.

### What is left

**Observability.** The metrics above are recorded but cannot yet be read in production, so they are instrumentation rather than monitoring.

- **Export.** No registry for a metrics backend is on the classpath, and `prod` exposes only `health` and `info`; meters live in memory and are lost on restart. The next step is one registry for whichever backend is chosen — Prometheus or OTLP — and nothing more.
- **Where the endpoint lives.** `/actuator/**` is denied in the security filter chain as well as unexposed. A Prometheus scrape endpoint therefore needs a deliberate decision, most simply a separate management port reachable only from inside the deployment network, rather than opening an actuator path on the public port. An OTLP push needs neither.
- **Dashboards and alerts.** Nothing yet turns the meters into a signal. The first alerts worth having are a rise in `checkout.failures` or `external.calls{operation="checkout",outcome="failure"}` (lost revenue), any `judgment.failures` (no assessment can complete), and `payment.confirmations` dropping to zero while checkouts are being started.
- **Webhook processing.** Stripe webhook failures are logged with their cause chain but not counted; a failed confirmation after a customer has paid deserves its own counter.
- **Unmeasured work.** PDF rendering is not timed, and the evidence timer measures the whole provider call rather than individual upstream requests.
- **Stripe detail.** Failure lines name the Stripe exception class but not Stripe's error code or request id, which would let a failure be matched to Stripe's dashboard. That belongs in the Stripe adapters, not in controllers.
- **Dependency health.** Readiness deliberately excludes Jev, OpenAI, Stripe and the evidence provider, so an outage cannot take the service out of rotation. Their health is visible only through `external.calls`, which is the intended design but depends on export existing.

**Live provider integration.** Real Jev judgments, real OpenAI narratives, real Stripe checkout and signed webhooks, and live evidence retrieval need real credentials and real calls, and are listed with the rest of the outstanding work at the end of [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md).

**Journey gaps.** There is no live evidence adapter for the university or A-level journeys, only ports, persistence and deterministic fixtures. The university journey has no upload input and no occupation-classification equivalent. The A-level live calibration capture still has to be promoted into the repository. See [University limitations](#university-limitations-and-next-sub-phases) and [A-level limitations](#a-level-limitations-and-next-sub-phases).

**Accounts.** There are no user accounts in any journey. The opaque assessment UUID remains a temporary pre-authentication access reference: anyone holding a saved URL can reach that assessment and an entitled report. Account-bound ownership belongs in a later phase.
