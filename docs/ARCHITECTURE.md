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
Persisted assessment snapshot
    ↓
OpenAI or local-fixture explanation
    ↓
Persisted narrative
    ↓
Nomis/ONS or local-fixture evidence
    ↓
Persisted evidence
    ↓
Deterministic free/paid report composition
```

Jev supplies small, explicit judgments rather than an overall business decision. Deterministic Java policies combine those judgments using journey-specific weights, thresholds, and rules. OpenAI turns the resulting decision into accessible narrative, but does not determine the authoritative score. External evidence is gathered and displayed separately; it is not sent to OpenAI. Free and paid reports compose the same saved package and do not make another assessment call.

External facts, including salaries and labour-market statistics, must come from an evidence layer with traceable sources rather than from model memory.

## Journeys and decision semantics

```text
                              SHARED PLATFORM
                                     |
          +--------------------------+--------------------------+
          |                          |                          |
          v                          v                          v
   PROFESSIONAL                 UNIVERSITY                   A-LEVEL
          |                          |                          |
   AI Exposure               Future Readiness          Future Optionality
   Human Advantage           Human Differentiation     Degree Pathway Breadth
   AI Complementarity        AI Complementarity        Analytical Foundation
   Role Adaptability         Skill Transferability     Human/Creative Strength
                             Career Adaptability       Skill Transferability
                                                       AI-Era Adaptability
```

- **Professional** assesses how AI may affect a person's current work: AI Exposure, Human Advantage, AI Complementarity, and Role Adaptability. Its indicators describe characteristics of the current role.
- **University** assesses how well a course positions a student for an AI-shaped labour market: Future Readiness, AI Complementarity, Human Differentiation, Skill Transferability, and Career Adaptability. Its measures describe characteristics of a course of study. It deliberately does not answer "will AI take this job?".
- **A-level** assesses whether a subject combination preserves useful future options: Future Optionality, Degree Pathway Breadth, Analytical Foundation, Human and Creative Strength, Skill Transferability, and AI-Era Adaptability. Its measures describe a combination of subjects. Its central idea is optionality, not present-day job exposure.

The journeys therefore share the `AssessmentDecision` result shape, but not a universal meaning for its indicator. Each Java decision policy defines its own dimensions, aggregation, labels, and thresholds.

### Why a shared platform does not imply shared score semantics

Professional AI Exposure, University Future Readiness and A-level Future Optionality are **three different constructs**. They share a 0–10 presentation scale and a persistence, delivery and payment pattern, and nothing else:

- They answer different questions. AI Exposure asks how readily work a person already performs may be performed, automated or materially changed by AI. Future Readiness asks how well a course appears to develop capabilities that stay valuable as AI tools become more capable. Future Optionality asks how wide a range of plausible next steps a subject combination keeps open.
- They describe different subjects of assessment: a person's current work, a course of study, and a combination of school subjects.
- They are computed from different atomic judgments. `professional-v1` and `university-v1` share only `information_sufficient`, `ai_complementarity` and `contextual_complexity`. `a-level-v1` shares only `information_sufficient` with professional, and `information_sufficient`, `analytical_reasoning`, `original_judgment_creativity` and `skill_transferability` with university — and even those have different rubrics, because they describe a subject combination rather than a course.
- They use different weights, versioned separately as `professional-decision-v1`, `university-decision-v1` and `a-level-decision-v1`.
- They use three distinct band vocabularies with no overlapping value: professional indicators band as `LOW` / `MODERATE` / `HIGH`, university measures as `LIMITED` / `DEVELOPING` / `STRONG`, and A-level measures as `FOCUSED` / `BALANCED` / `BROAD`. The names are deliberately disjoint so a reader cannot carry an interpretation across.
- Their direction of merit differs. A high AI Exposure is a neutral-to-cautionary observation about work; a high Future Readiness and a high Future Optionality are positive observations about a course and a subject combination. Neither is the inverse of AI Exposure.

A professional score of 7.2, a university score of 7.2 and an A-level score of 7.2 therefore mean unrelated things and must never be compared, averaged, ranked together or combined into one universal AI career score. `ProfessionalMeasure`, `UniversityMeasure` and `ALevelMeasure` are separate enums precisely so no shared score object can exist; each decision policy refuses another journey's judgment set outright; and `CrossJourneySeparationTest` pins all of this. Each paid report states the separation explicitly in its methodology section.

Genuinely shared, journey-neutral types are reused: `JourneyType`, `AssessmentStatus`, `AssessmentConfidence`, `AssessmentDecision`, `AssessmentJudgmentSet`, `DimensionJudgment`, the security configuration and the persistence/payment/PDF patterns. Narrative, evidence, payment, report and PDF status enums are deliberately duplicated per journey rather than hoisted into a generic framework: the small duplication keeps each journey's semantics legible and avoids coupling one journey's release to another's. There is deliberately no `Journey<T>` abstraction; three clear vertical slices are easier to reason about than one parameterised framework that hides which semantics apply.

## Intended modules

- `assessment`: journey inputs, assessment application services, and assessment lifecycle.
- `judgment`: atomic judgment contracts and later Jev adapters.
- `decision`: deterministic, journey-specific Java policies.
- `narrative`: explanation generation from authoritative decisions and evidence.
- `evidence`: sourced external facts and provenance.
- `career`: career and occupation concepts shared where their meaning is genuinely common.
- `report`: deterministic tiered report assembly and presentation models.
- `payment`: payment and entitlement workflows.
- `product`: the shell the three journeys are presented inside — landing page, shared chrome, informational pages and development-mode signalling. It owns no assessment logic.
- `analytics`: product and assessment analytics.
- `security`: identity, access, and data-protection concerns.
- `configuration`: application wiring and external-client configuration.

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
       ProfessionalDecisionPolicyV1
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

The `prod` profile also selects the real Jev and OpenAI adapters because both are guarded by `!local-fixture`. Production startup requires `TYPESAFE_API_KEY` and `OPENAI_API_KEY`; `StartupConfigurationValidator` names missing variables without logging values. Live AI verification status and the explicit Jev command are recorded in [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md).

### Saved professional assessments

Write flow: browser POST → form validation → form mapper → application service → Jev or explicit local fixture → `AssessmentJudgmentSet` → `ProfessionalDecisionPolicyV1` → `ProfessionalAssessmentResult` → `ProfessionalAssessmentStore` → JPA adapter → PostgreSQL → redirect to `/professional-assessment/{UUID}`.

Read flow: GET `/professional-assessment/{UUID}` → `ProfessionalReportService` → saved package stores → `ProfessionalReportAssembler` → free view mapper → Thymeleaf. The GET does not invoke the judgment provider or policy. Post/Redirect/Get means a browser refresh repeats only the GET, avoiding a second paid Jev request.

`ProfessionalAssessmentStore` is a vendor-neutral application port. Its JPA adapter maps the immutable application snapshot to dedicated persistence entities. Domain and decision records remain free of JPA annotations. Flyway V1 creates an assessment row with input and provenance, judgment rows for all nine dimensions, measure rows, and ordered factor rows. JSONB holds only the ordered responsibility/skill and decision ID lists, plus each judgment's probability distribution. Nullable columns preserve absent Noul confidence rather than inventing a value. The saved decision includes its original scores, bands, confidence, status, and structured factors; reading an old assessment never recalculates it under today's policy. Schema and policy versions identify the methodology that produced it.

The external Jev call and Java policy execute before `JpaProfessionalAssessmentStore.save` starts its short database transaction. `findById` also maps inside a read-only transaction. A database transaction cannot make a Jev network call atomic with PostgreSQL: if saving fails after a successful call, the request fails safely and no redirect is issued. No automatic retry or fixture fallback occurs.

The UUID is an opaque temporary access token. There is no public list endpoint; result responses use `private, no-store`, and the page requests `noindex,nofollow`. Anyone who has a saved URL can currently read that assessment, so this pre-authentication access model requires proper account-based authorization before production use. The configured Jev model name (currently `jev-latest`) is saved when known; this alias is not an immutable underlying model version and the adapter does not claim it is.

`LocalFixtureProfessionalJudgmentProvider` is an adapter for explicit local development only. Activate it with the `local-fixture` Spring profile and select an exact fixture ID through `app.professional.fixture-id`: `routine_software_developer`, `senior_software_architect`, `electrician`, or `registered_nurse`. The fixture ID is technical configuration; `ProfessionalAssessmentState.profession` remains the user's real job title. Missing or unsupported IDs fail at startup. Fixture data is illustrative development data, not product intelligence. The profile disables external AI models and selects only the fixture provider; normal mode selects only the Jev provider. Fixtures never activate automatically, and a Jev failure never falls back to fixtures. `ProfessionalAssessmentResult.source` carries `JEV` or `LOCAL_FIXTURE` explicitly for future callers, along with the judgment schema and decision policy versions. The results view prominently marks local fixture output as development data.

For local startup, run:

```shell
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local-fixture \
  -Dspring-boot.run.arguments="--app.professional.fixture-id=senior_software_architect"
```

This does not require Jev or OpenAI keys; the existing PostgreSQL development configuration still applies. Visit `/professional-assessment` and enter a real job title and work details. Spring Security permits this public form and its stylesheet while keeping CSRF protection for the POST and requiring authentication for unrelated routes.

The Maven `spring-boot:run` development path has the optional Spring Boot Docker Compose dependency on its runtime classpath, so it discovers the running PostgreSQL service and supplies the datasource connection. The repackaged executable JAR excludes that development dependency. `application.properties` intentionally has no production datasource URL or credentials. A packaged deployment must provide its own PostgreSQL connection configuration, for example `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; running the JAR locally without them fails at datasource startup. This is deployment configuration to supply when a deployment environment is introduced, not a reason to add H2 or embed development credentials.

`ProfessionalDecisionPolicyV1` is deterministic Java policy. It validates the atomic judgments, normalises their 0–4 values, applies explicit weights, assigns indicator bands, derives confidence and status, and selects structured explanation factors. It does not call Jev or OpenAI.

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

`OpenAiProfessionalNarrativeGenerator` is the normal adapter. It uses the existing Spring AI 2.x `ChatClient` and one typed `entity(ProfessionalNarrative.class, ...)` request with provider-native structured output. It does not request free-form prose and scrape headings. The response record requires every prose section and between three and five non-blank practical actions. `ProfessionalNarrativeValidator` additionally rejects score-like or percentage claims; the prompt instructs the model not to repeat numeric assessment values at all. Invalid or absent output fails the narrative attempt and can never change assessment state.

The `professional-narrative-v1` prompt and output contract state that saved scores, bands, confidence, status, drivers and protective factors are authoritative. The model must not recalculate or invent scores, introduce decision factors, contradict the decision, predict job loss, claim certainty or disappearance, fabricate labour-market or salary facts, cite invented studies or statistics, recommend abandoning a career, or recommend courses and providers. It must distinguish exposure from complementarity, treat Human Advantage as independent rather than the inverse of exposure, and explain Role Adaptability as a property of role structure rather than the person's motivation, learning ability, willingness, finances, geography or circumstances. Actions are limited to modest steps grounded in the assessment. User-supplied text is explicitly data rather than instructions.

`LocalFixtureProfessionalNarrativeGenerator` is selected only by the explicit `local-fixture` Spring profile. It uses fixed templates plus saved bands and structured factors; it has no alternate scoring method and no OpenAI dependency. Its persisted source and provider are `LOCAL_FIXTURE` and `local-fixture`, and the existing DEVELOPMENT MODE warning remains visible. Normal OpenAI failures never select this bean or call it: profile wiring permits exactly one generator, and `ProfessionalNarrativeService` stores `FAILED` instead of looking up another implementation.

### Narrative lifecycle and history

Flyway V2 adds `professional_assessment_narrative`, a separate one-to-one table keyed by the immutable assessment ID. No row means `NOT_REQUESTED`; an attempt writes `PENDING` before calling the generator and then updates that row to `AVAILABLE` with all structured content or `FAILED` without content. The table records source, provider identifier, configured model identifier when known, prompt/schema version, attempt timestamp and successful generation timestamp. It never stores API keys, prompts, exceptions, hidden reasoning or chain-of-thought. A database check constraint ensures only an available row can contain narrative content and a generation timestamp.

Keeping narrative lifecycle in a separate table lets the assessment snapshot remain immutable. A generator or narrative-persistence failure occurs after the assessment commit, so it cannot delete or invalidate the deterministic result. The submission still redirects to the saved result. The result GET always renders the measures and factors; when narrative is missing, pending or failed it shows a safe temporary-unavailability message without provider details.

Result GETs are read-only. They load a saved narrative when one exists and never invoke `ProfessionalNarrativeGenerator`, Jev or `ProfessionalDecisionPolicyV1`. `generateFor` also returns any existing lifecycle row without replacing it, including older prompt versions and failed attempts. Consequently an `AVAILABLE` historical narrative remains exactly the narrative originally saved: there is no implicit regeneration or upgrade from one prompt/schema version to another. A future regeneration feature would need to be an explicit operation with its own audit semantics.

The configured OpenAI model identifier (`spring.ai.openai.chat.model`, configurable through `OPENAI_MODEL`) is persisted. It may be a provider alias rather than an immutable underlying model revision, so it is audit metadata with that limitation rather than a claim that the exact provider-side build is known. The prompt/schema version remains the authoritative identifier for this application's narrative contract.

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

The live adapter uses the official ONS Annual Population Survey occupation dataset exposed by the Nomis API (`APS218`, API dataset `NM_218_1`). It makes two bounded HTTP requests: the official SOC 2020 codelist resolves the configured four-digit code to Nomis's dataset-specific value, then one observation request obtains the latest UK employment estimate. The adapter validates dataset, occupation, geography, measure and numeric value before accepting the response. It has a 5-second connection timeout and a 10-second read timeout. It supports United Kingdom only; another configured geography fails safely instead of receiving a misleading UK label. The reference period and retrieval date are retained, while publication date is absent when the response does not supply one. A single historical employment count is not a trend or forecast. It does not scrape web pages and does not infer or fuzzily match an occupation code from the submitted title. For now the `live-evidence` profile uses an exact operator-configured mapping requiring `PROFESSIONAL_EVIDENCE_PROFESSION`, `PROFESSIONAL_EVIDENCE_CANONICAL_OCCUPATION` and `PROFESSIONAL_EVIDENCE_SOC2020_CODE`; the submitted title must match exactly. This is intentionally limited: V1 proves the replaceable classifier and evidence-adapter boundaries but does not yet provide broad official occupation lookup, trend series, salary, skills or automation research for every role. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md) for endpoints, live results and source limits.

Live evidence is opt-in through the `live-evidence` Spring profile. With no evidence profile there is no provider and evidence remains `NOT_REQUESTED` (represented by no row). Ordinary tests and normal development therefore make no live evidence request. No API key is required for the bounded anonymous Nomis request; if a future adapter needs credentials, they must come from environment configuration and must never be persisted or logged.

The `local-fixture` profile selects `LocalFixtureProfessionalEvidenceProvider` alongside the existing judgment and narrative fixtures. It supports only the exact configured IDs `routine_software_developer`, `senior_software_architect`, `electrician` and `registered_nurse`. The representative item is deterministic, carries `LOCAL_FIXTURE` / `DEVELOPMENT_FIXTURE` provenance, has no fake government URL, and is shown under both the main development warning and an evidence-specific “not live official data” label. Unsupported or approximate fixture IDs fail clearly.

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

`ProfessionalReportAssembler` is a pure, deterministic application component. It accepts a `ProfessionalReportSource` containing only saved historical records and creates the immutable, delivery-neutral `ProfessionalReport`. It cannot call Jev, the Java decision policy, OpenAI, an evidence provider or any external API. `ProfessionalReportService` only reads the four persistence ports, while the web layer maps the already tiered report to presentation data. Templates contain no entitlement rules. The same stored input, tier and `professional-report-v1` composition logic therefore produce the same report.

Payment never affects assessment. Both tiers use exactly the same saved status, confidence, four scores, bands, factors, narrative and evidence. `FREE` includes the profession, all authoritative measures and concise definitions, top exposure and protective factors, a short saved interpretation, the explicit job-loss disclaimer and a concise methodology caveat. It intentionally remains useful. `PAID` includes all of that plus the complete saved narrative, actions, per-measure explanations, deterministic skill-development priorities, conservative work-focus guidance, saved external evidence with attribution, saved occupation classification and fuller methodology/version information. The product boundary is expressed by report composition rather than scattered `if (paid)` checks in Thymeleaf.

Skill priorities are modest labels (`HIGH_PRIORITY` or `USEFUL`), not scores. They follow only from saved role-level factor directions and the saved AI Complementarity band. Work-focus guidance likewise does not classify individual tasks: general assistance, oversight and human-value areas follow from saved role factors, while every submitted responsibility is placed in `USER_REVIEW_REQUIRED` with an explicit statement that no task-level judgement occurred. This avoids inventing a second assessment engine.

### Occupation classification

`ProfessionalOccupationClassifier` is a vendor-neutral port receiving only profession, responsibilities, skills and explicit geography. Its output records canonical occupation, classification system/code, provenance, mapping quality and `professional-occupation-classifier-v1`. It cannot alter assessment scores. The `local-fixture` implementation maps only the four exact configured fixture IDs to deterministic SOC 2020 occupations and never fuzzily matches a title. The opt-in `live-evidence` implementation is currently an exact operator-configured mapping; a broader official lookup can replace it behind the same port later without changing assessment or evidence orchestration.

Classification runs after the assessment and narrative have been persisted and before evidence is gathered. Flyway V4 adds one immutable `professional_occupation_classification` row per assessment, keyed by the assessment UUID. The evidence request factory reads that saved classification rather than environment values. An existing row is never overwritten or automatically upgraded, GET requests never classify again, and absent classification degrades cleanly. V4 is necessary because the code and timestamp used for historical evidence selection are audit data; V1–V3 remain unchanged.

### Report history, access and price

Ordinary assembled reports are not separately persisted because they would duplicate the assessment, narrative and evidence snapshots. `professional-report-v1` is defined once and exposed in report methodology. A confirmed purchase is the exception: its exact paid report is frozen as an immutable purchased snapshot, as described below.

The paid route, `GET /professional-assessment/{UUID}/report`, requires an `ACTIVE` entitlement and renders only its frozen purchased snapshot. URL knowledge, a redirect query parameter and a pending Checkout Session do not grant access. No price is hard-coded: the expected low-cost consumer range is £2.99–£9.99, while Stripe Price configuration is authoritative for charging and optional display text comes from configuration.

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
Stripe / local fixture
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

The Stripe adapter is selected only by the explicit `live-payment` profile and fails startup if `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` or `PROFESSIONAL_REPORT_STRIPE_PRICE_ID` is missing. It uses the official Stripe Java SDK and hosted Checkout Sessions in one-time payment mode. Optional `PROFESSIONAL_REPORT_DISPLAY_PRICE` controls CTA display only; Stripe's configured Price remains authoritative for the charge and currency. Normal mode has no payment gateway. The `local-fixture` profile instead selects a deterministic, zero-network gateway and an explicit local confirmation screen with prominent development warnings. It never represents its session or entitlement as Stripe provenance.

### Confirmation, idempotency and freezing

The success redirect is navigation, not proof of payment. It only returns the browser to the free result, which reads the persisted entitlement state. Production confirmation comes through `POST /payments/stripe/webhook`. That exact path alone is exempt from browser CSRF because it is authenticated by Stripe's signature; checkout and local-confirmation POSTs retain CSRF protection. The adapter verifies the signature with the configured webhook secret, accepts only `checkout.session.completed`, requires the session's payment status to be `paid`, and validates its minimal metadata against the saved provider, session, assessment and entitlement identifiers.

Stripe can redeliver events. Flyway V5 records each provider event ID under a composite provider/event primary key. Processing locks the entitlement row, rechecks state and identity, and atomically writes the purchased snapshot, changes `PENDING` to `ACTIVE`, and records the event. A duplicate event returns an idempotent already-processed result and neither rebuilds nor duplicates the report. If report assembly, JSON conversion or persistence fails, the transaction rolls back: no `ACTIVE` entitlement can exist without its purchased snapshot, and Stripe may retry the event.

The purchased snapshot is JSONB because `ProfessionalReport` is an immutable presentation document with nested, versioned sections; normalising it would duplicate the report model into many purchase-only tables. Entitlement status, provider/session/payment references, timestamps and `purchased_report_version` remain relational for constraints and queries. The JSON contains only user-facing paid report content—scores, explanations, factors, narrative, actions, skill and work-focus guidance, displayed evidence and classification, methodology and limitations. It excludes secrets, prompts, exceptions, SDK data and hidden reasoning.

The frozen snapshot, not today's assembler, is authoritative after purchase. A `professional-report-v1` bought in 2026 therefore remains V1 after a future V2 deploy. `GET /professional-assessment/{UUID}/report` denies absent, `PENDING`, `FAILED`, `REFUNDED` and `REVOKED` states and renders the snapshot only for `ACTIVE`. The free assessment remains available throughout checkout and all payment failures.

There are no accounts yet. Entitlement is associated with the assessment UUID, so anyone who obtains that opaque URL may potentially reach both the assessment and an entitled report. There is no public listing route, but this is explicitly temporary pre-authentication access rather than final production-grade authorisation. Account-bound ownership belongs in a later phase.

## Purchased professional report PDF

```text
Purchased professional-report-v1 snapshot
        |
        v
ProfessionalReportPdfService
        |
        v
ProfessionalReportPdfRenderer
        |
        v
professional-report-pdf-v1
        |
        v
immutable persisted PDF artifact
        |
        v
authorised download
```

The PDF is a delivery artifact of the exact report already bought. `ProfessionalReportPdfService` reads an `ACTIVE` entitlement and its frozen purchased snapshot directly through persistence ports. It never calls `ProfessionalReportAssembler`, Jev, the decision policy, OpenAI, narrative generation, evidence retrieval, occupation classification or Stripe. This makes payment-confirmed `professional-report-v1` content—not current application state—the sole source of PDF text.

`ProfessionalReportPdfRenderer` is a vendor-neutral application port receiving `ProfessionalReportPdfModel`, which contains the purchased content version, purchase timestamp, PDF template version and immutable purchased report. `OpenHtmlProfessionalReportPdfRenderer` is the local pure-Java implementation. It converts internally generated XHTML and conservative print CSS through OpenHTMLToPDF/PDFBox. The template uses built-in font families, inline packaged CSS and no images, remote stylesheets, CDN resources, tracking elements or other network inputs. Persisted safe HTTP(S) evidence URLs are emitted only as PDF hyperlinks; they are never fetched.

Content and presentation versions are deliberately separate. `professional-report-v1` identifies what the customer bought. `professional-report-pdf-v1` identifies how those purchased fields are laid out. The purchased-snapshot codec now switches explicitly on `professional-report-v1`, giving future report versions a small version-aware reader seam rather than assuming that every historical JSON document matches the latest Java model. Future incompatible versions must add a reader/mapper and must not reinterpret V1.

### Artifact lifecycle and persistence

On the first authorised `GET /professional-assessment/{UUID}/report.pdf`, the service first checks for an existing artifact. If present it returns those exact bytes without loading the snapshot or invoking the renderer. Otherwise it loads the matching frozen snapshot, renders once, requires a `%PDF-` signature, rejects empty or over-limit output, calculates SHA-256, sanitises a profession-based filename and calls `ProfessionalReportPdfStore.saveIfAbsent`.

Flyway V6 adds `purchased_professional_report_pdf`, with one row per entitlement and a unique assessment reference. It stores purchased report version, PDF template version, generation timestamp, safe filename, byte count, SHA-256 and PDF bytes in PostgreSQL `BYTEA`. The database checks positive and matching byte length plus hash format. BYTEA is appropriate for these small bounded consumer documents and avoids premature object-storage infrastructure. The storage port permits a later S3/object-store adapter without changing the service or controller.

The insert uses PostgreSQL `ON CONFLICT DO NOTHING`, followed by reading the single stored winner. If simultaneous first requests both render, the entitlement primary key ensures one immutable artifact wins and both callers return that same persisted artifact. No distributed lock or queue is needed. Existing rows are never updated or regenerated; subsequent downloads and application restarts return identical bytes and SHA-256.

Only an `ACTIVE` entitlement reaches generation or download. Missing, pending, failed, refunded and revoked states receive HTTP 403. Successful responses use `application/pdf`, an attachment disposition with a sanitised filename, an exact content length, `nosniff`, and private/no-store caching. Rendering or persistence failure leaves the entitlement, purchased snapshot and paid HTML report unchanged and returns a generic temporary PDF-unavailability message without provider or renderer details.
## Professional CV input

CV upload is an alternative input path, not an assessment engine:

```text
Manual input -> ProfessionalAssessmentForm -> ProfessionalAssessmentState --\
                                                                             -> existing judgment/policy pipeline
CV upload -> ProfessionalCvParser -> ProfessionalProfileExtractor            /
                    |                         |
                    v                         v
          transient clean text       ProfessionalProfile
                                              |
                                      review / edit / confirm
                                              |
                              ProfessionalProfileAssessmentMapper
```

`ProfessionalCvParser` accepts a Spring-independent `ProfessionalCvDocument` and does one job:
PDFBox or Apache POI converts it to bounded, cleaned text. It never decides a role, skill,
responsibility or years of experience. `ProfessionalProfileExtractor` is the separate information-
extraction boundary. The normal adapter uses Spring AI/OpenAI provider structured output to create
the small immutable `ProfessionalProfile`; under `local-fixture`, a deterministic fixture adapter
supports the complete browser flow without OpenAI or Jev.

For local extraction-quality testing, `local-fixture,live-cv-extraction` replaces only that fixture
extractor with the OpenAI adapter. The fixture judgment, narrative, occupation, evidence and payment
providers remain active, so the mode cannot silently become a production or mixed-provider setup.
It requires only `OPENAI_API_KEY` and never falls back to fixture profile values after extraction or
validation failure. Exact setup and acceptance steps are in
[`LIVE_CV_EXTRACTION_TEST.md`](LIVE_CV_EXTRACTION_TEST.md).

The text-only parser contract is versioned `professional-cv-parser-v2`; historical V1 assessment
metadata is retained unchanged.

The extraction prompt requires support from the document, null/empty values under uncertainty,
separation of responsibilities and skills, and warnings for ambiguity. It explicitly forbids
assessment measures, job-loss predictions and labour-market claims. `ProfessionalProfileValidator`
then applies only structural safeguards: title shape and length, required responsibilities, concise
skills, duplicate role/responsibility text and plausible experience bounds. It does not attempt
semantic classification.

Before the CV text reaches OpenAI, the adapter, in this order, neutralises any `<cv-data>`/`</cv-data>`
markers inside it, so the document cannot close its own data block; removes contact details with
`ProfessionalCvContactMasker`; and sends at most `app.professional.cv.maximum-extraction-characters`
(default 30,000, at least 1,000) characters. The masker replaces email addresses with `[email]`, web
links (http/https, `www.`, and linkedin.com/github.com paths) with `[link]`, phone numbers of 10 to 20
digits with `[phone]`, and full UK postcodes in capitals with `[postcode]`. It never masks a digit run
that the experience calculator would read as a date or date range, and it does not try to remove names
or street addresses, so those may still be sent. Its patterns are length-bounded and anchored to token
boundaries, so masking stays linear on any input the parser allows. The prompt tells the model to
ignore the placeholders. Duty evidence and employment dates are checked against this exact masked,
truncated text, and neither the raw nor the masked text is logged or persisted. When the text is cut, the profile carries a review warning. Null or blank list items in
the structured response are dropped rather than failing extraction.

The model returns current-role `dayToDayDuties`, each with word-for-word `evidence`, and lists
one-off `achievements` separately. Java keeps a duty only when its evidence (at least 12 characters
after normalising case, whitespace, quote and dash style, and a leading bullet) is an exact substring
of the text actually sent to OpenAI. Unmatched duties are dropped with one generic warning. The first
12 verified duty texts become `responsibilities`. Achievements never reach Jev. If no duty survives, the existing
validator rejects the profile and the user describes their work manually.

Years of experience is calculated in Java, not estimated by the model. The model only lists
`employmentHistory`: every dated job with its title, employer, start and end copied exactly as
written, and whether it is the same as or closely related to the current role.
`ProfessionalExperienceCalculator` (pure Java, driven by the injected clock) then:

- counts a job when the model marks it relevant, and always counts the current job, recognised by
  matching the selected entry's start and end dates;
- requires each counted date to appear in the text actually sent to OpenAI, using the same
  normalisation as duty evidence (Present-style end words are exempt);
- parses `MM/YYYY`, `M/YYYY`, `MM.YYYY`, `MM-YYYY`, English month names and abbreviations, and a
  year alone (January as a start, December as an end); Present, Current, Now, To date and Ongoing
  mean the current month and are accepted only as an end date;
- merges overlapping or touching periods, counts months inclusively, does not count future months
  of a contract that has not ended yet, and rounds to the nearest year (six months rounds up).

Any doubt gives no number rather than a guess: an unparseable or uncopied relevant date, an end
before its start, a start after the current month, no relevant job, or more than 70 years. The
profile then carries one fixed warning asking the user to add their years if they know them. A
current job that started at least 12 months ago can never produce zero.

The extractor also returns the selected entry's employer and start/end dates as written in the CV.
They exist only so the review page can say which job was used ("We used your role at {employer}
({start} – {end}).", or the employer-only or dates-only form, or nothing when neither is known), and
so the user can correct them on the edit page. `ProfessionalProfileValidator` strips them and silently
clears an employer over 120 characters or a date over 40; they never reject a profile.
`ProfessionalProfileAssessmentMapper` ignores them, so they never reach Jev or the narrative and are
never persisted. The review and edit pages also tell the user that CVs usually list achievements,
and ask them to add their typical weekly tasks and remove one-offs or tasks from earlier jobs.

Under "Years of experience" the review page shows one "Based on: {title} at {employer} ({start} –
{end})" line per counted job, carried as `ProfessionalProfile.experienceBasis` (at most ten lines).
Like the employer and dates it is display-only: the mapper ignores it, so it never reaches Jev and
is never persisted. The edit page does not edit it; the user edits the years number directly, and
changing or clearing that number removes the basis so the page no longer explains a value the CV
did not produce. The validator also drops the basis when it omits an implausible years value. The
local-fixture extractor calculates its years from its own dated history in the same way.

The reviewed profile lives only in the in-memory servlet session (Tomcat session persistence is off
and there is no Spring Session store), so it is never serialised and does not survive a restart; a
missing or unrecognised session profile redirects to the professional assessment start page.

Only PDF and DOCX are accepted. Filename, declared media type, file signature, configured size,
and actual parseability are checked. Scanned/image-only PDFs fail safely; there is no OCR fallback.
Encrypted/unreadable files, sparse career information, unavailable extraction, malformed structured
output and timeouts return to manual entry rather than populating guessed values.

### Known limitations

`ProfessionalProfile` holds an `OptionalInt` and `ProfessionalAssessmentInputMetadata` holds
`Optional` values. Both records declare `Serializable`, but neither `OptionalInt` nor `Optional` is
serialisable, so these session attributes cannot be written out. This is harmless while sessions
stay in memory, but it must be fixed before enabling Tomcat session persistence or Spring Session.

Raw CV bytes and extracted text exist only during the upload call and are never persisted or logged.
A short-lived server session carries only the structured profile and trusted parse provenance across
review/edit/confirm. `ProfessionalProfileAssessmentMapper` combines the confirmed role, summary,
responsibilities, technical/human skills, context and optional experience into the unchanged
`ProfessionalAssessmentState`. Alongside the assessment snapshot, V7 stores `MANUAL`/`CV_UPLOAD`,
the sanitised filename, canonical media type, parser version and parse timestamp, and V18 adds
`cv_extractor_version`. Every `ProfessionalProfileExtractor` reports a `version()`, which
`ProfessionalCvUploadService` records next to the parser version: `professional-cv-extractor-v2` for
the OpenAI extractor and `professional-cv-extractor-fixture-v1` for the local fixture. V18 backfills
`professional-cv-extractor-v1` on every earlier CV upload, since all of them used that extractor, and
re-creates `professional_assessment_cv_metadata_consistent` so a manual row must have no extractor
version and a CV upload must have one. These fields do not affect scoring; judgments, policies,
reports and PDFs are unchanged.

## University journey

The university journey answers a different product question from the professional journey: *how well does this course position me for an AI-shaped labour market?* It is a distinct vertical slice that reuses the proven delivery pattern without reusing professional scoring semantics.

```text
University input
    ↓
UniversityAssessmentState
    ↓
UniversityJudgmentProvider
    ↓
university-v1 judgments
    ↓
UniversityDecisionPolicyV1
    ↓
saved assessment
    ↓
narrative + evidence
    ↓
UniversityReportAssembler
    ↓
FREE / PAID
    ↓
university entitlement
    ↓
frozen purchase
    ↓
university-report-pdf-v1
```

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
        UniversityDecisionPolicyV1
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

`UniversityJudgmentProvider` is the vendor-neutral application port. `UniversityJevJudgmentService` is the normal adapter; TypeSafe/Jev SDK types never leave `judgment.typesafe.university`. It returns the shared `AssessmentJudgmentSet` / `DimensionJudgment` types. `UniversityJevAutoConfiguration` is registered in `AutoConfiguration.imports`, is active only outside `local-fixture`, and requires a `TypeSafeClient` bean.

### University decision policy

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

### University explainability

Explainability uses signed contribution logic, following the established professional pattern. A separate `EXPLANATION_WEIGHTS` map covers **all nine** Score dimensions and sums to 1; it is used only to select and order factors and never contributes to an authoritative measure. This lets human and practical dimensions be explained even though they do not enter Future Readiness.

For each dimension the signed effect is `weight × (normalised − 0.5)`. Positive effects become `STRENGTH` factors ("key strengths", "readiness drivers"); negative effects become `DEVELOPMENT_OPPORTUNITY` factors ("areas you may want to strengthen"). Because a dimension has exactly one signed effect, the same dimension can never appear on both sides. The top three of each side are kept. `UniversityFactorDirection` records which side of the neutral midpoint was observed, and the report maps each direction to course-specific language rather than professional "exposure driver" wording.

### University persistence

Flyway V8 adds `university_assessment` with input, provenance and headline decision, plus ordered `university_assessment_judgment`, `university_assessment_measure` and `university_assessment_factor` rows. JSONB holds only the ordered module/skill/career lists, the factor ID lists and each judgment's probability distribution. Nullable columns preserve absent Noul confidence rather than inventing a value. A check constraint pins both journey columns to `UNIVERSITY`.

V9 adds `university_assessment_narrative`, V10 adds `university_assessment_evidence_bundle` and `university_assessment_evidence_item`, V11 adds `university_report_entitlement`, `purchased_university_report_snapshot` and `processed_university_payment_event`, and V12 adds `purchased_university_report_pdf`. University tables are entirely separate from professional tables; no university record is ever forced into a professional table. V1–V7 are unchanged.

`UniversityAssessmentStore` is a vendor-neutral application port whose JPA adapter inserts only — a completed assessment is never merged over an old snapshot. The saved decision keeps its original scores, bands, confidence, status and structured factors, so reading an old assessment never recalculates it under today's policy. `GET /university-assessment/{UUID}` loads saved records only: it never invokes the judgment provider, the decision policy, the narrative generator or an evidence provider.

### University narrative

`UniversityNarrativeService` runs after the assessment transaction has committed. `UniversityNarrativeContextFactory` builds `UniversityNarrativeContext` from the persisted snapshot: course details, saved status and confidence, all five saved measures with values, bands and confidences, the structured strengths and development opportunities, and the source and version metadata. It contains no JPA entity, database access, credential or provider SDK type.

`UniversityNarrativeGenerator` is the vendor-neutral port returning the immutable, HTML-free `UniversityNarrative`. `OpenAiUniversityNarrativeGenerator` is the normal adapter, using one typed `entity(UniversityNarrative.class, ...)` request with provider-native structured output. `LocalFixtureUniversityNarrativeGenerator` is selected only by the `local-fixture` profile and uses fixed templates plus saved bands and factors. Profile wiring permits exactly one generator; an OpenAI failure stores `FAILED` rather than looking up another implementation.

The `university-narrative-v1` prompt states that saved measures, bands, confidence, status, strengths and development opportunities are authoritative. The model must not recalculate or invent them, repeat numeric values, add new assessment factors, or contradict the decision. It must not invent module content, teaching arrangements or institutions; state salary figures, graduate employment rates, league table positions, entry requirements or study costs; cite studies or sources; guarantee employment; call a course future-proof, safe, risky, a bad degree or a waste; or tell the student to abandon the degree. It must explain Future Readiness as a property of the course rather than a probability that a job will be automated, treat AI Complementarity as independent, and treat Skill Transferability and Career Adaptability as properties of the capabilities and pathways rather than the student's motivation, ability, finances or circumstances. Career areas are context only, phrased as "a relevant area to explore"; "you should become X" is forbidden. User-supplied text is explicitly data rather than instructions.

`UniversityNarrativeValidator` is a deterministic backstop applied after typed mapping. It rejects repeated scores and percentages, currency and salary figures, and unsupported employment or quality claims (`future-proof`, employment rates, job guarantees, "bad degree", "you will struggle to find work"). Anything it rejects fails the narrative attempt and can never change assessment state.

Narrative lifecycle is a separate one-to-one table keyed by the immutable assessment ID: no row means `NOT_REQUESTED`; an attempt writes `PENDING`, then `AVAILABLE` with content or `FAILED` without. `generateFor` returns any existing row without replacing it, including older prompt versions and failed attempts, so a historical narrative remains exactly what was originally saved. Regeneration would need to be an explicit future operation with its own audit semantics.

### University evidence

`UniversityEvidenceProvider` is the vendor-neutral port. It receives a narrow `UniversityEvidenceRequest` containing the assessment UUID, course title, modules, skills, preferred career areas, optional institution, explicit geography and journey — never a JPA entity, credential or SDK type. Providers return the immutable `UniversityEvidenceBundle` and `UniversityEvidenceItem` model. Categories are `COURSE_CONTENT`, `GRADUATE_OUTCOMES`, `OCCUPATIONAL_DESTINATIONS`, `SKILL_DEMAND` and `LABOUR_MARKET_CONTEXT`. The version is `university-evidence-v1`.

Evidence is external factual context, not an input to `UniversityDecisionPolicyV1`. It cannot change any measure, status or confidence. It is deliberately downstream of the persisted assessment: assessment and narrative remain valid if an evidence provider or its persistence fails. Geography comes from the submitted country when supplied, otherwise from the explicit `app.university.evidence.geography` configuration, defaulting to `United Kingdom`. The application does not infer geography from IP address.

`LocalFixtureUniversityEvidenceProvider` supports the six exact configured IDs, carries `LOCAL_FIXTURE` / `DEVELOPMENT_FIXTURE` provenance, emits no fake government URL, and is labelled as development data in the UI. No live university evidence adapter exists. Prompt 18B examined official Discover Uni/HESA data but deferred a live adapter: the request has a course title and optional institution, without stable course/provider identifiers or entry year. Course content requires an identified provider course; Graduate Outcomes aggregates must not be presented as outcomes for an arbitrary course; occupational and labour-market data require an independently justified mapping. All five live categories therefore remain unavailable until a defensible source mapping is implemented. With no evidence profile there is no provider and evidence remains `NOT_REQUESTED`. A provider exception is recorded as `FAILED` and never causes fixture lookup. Historical evidence is never refreshed by a GET. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md).

### University report composition

`UniversityReportAssembler` is a pure, deterministic application component. It accepts a `UniversityReportSource` containing only saved historical records and produces the immutable, delivery-neutral `UniversityReport` (`university-report-v1`). It cannot call Jev, the decision policy, OpenAI, an evidence provider or any external API. `UniversityReportService` only reads the three persistence ports; the web layer maps the already tiered report to presentation data, so templates contain no entitlement rules.

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
Stripe / local fixture
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

University entitlement is **entirely separate** from professional entitlement. The two live in different tables with foreign keys to their own assessment tables, so a professional purchase grants no university access and a university entitlement cannot even be created for a professional assessment ID. There is a separate Stripe Price (`UNIVERSITY_REPORT_STRIPE_PRICE_ID`), a separate webhook endpoint (`POST /payments/stripe/university-webhook`), a separate configurable webhook secret, and separate local-fixture provenance. No price is hard-coded.

`UniversityReportPurchaseService.startCheckout` first verifies that a complete saved PAID report can be composed, then calls the gateway without a database transaction, then persists the returned session as a `PENDING` entitlement in a short transaction. A Stripe failure never persists an entitlement and never falls back to the fixture gateway.

The Stripe adapter is selected only by the explicit `live-payment` profile and fails startup if its secret key, webhook secret or Price ID is missing. Its Checkout Session carries `product=university_report` metadata alongside the assessment and entitlement UUIDs — never course descriptions or other submitted content. The webhook verifier requires that product tag, so an event for another product is ignored rather than treated as an error. The success redirect is navigation, not proof of payment.

Confirmation is idempotent: each provider event ID is recorded under a composite provider/event primary key, processing locks the entitlement row, rechecks state and identity, and atomically writes the purchased snapshot, flips `PENDING` to `ACTIVE` and records the event. A duplicate event returns an already-processed result and neither rebuilds nor duplicates the report. If anything fails the transaction rolls back, so no `ACTIVE` entitlement can exist without its purchased snapshot.

The frozen snapshot, not today's assembler, is authoritative after purchase. A `university-report-v1` bought today remains V1 after a future V2 deploy; `PurchasedUniversityReportSnapshotCodec` switches explicitly on the stored version and gives future versions a reader seam rather than reinterpreting V1. Only `ACTIVE` reaches `GET /university-assessment/{UUID}/report`; absent, `PENDING`, `FAILED`, `REFUNDED` and `REVOKED` are denied. The free assessment remains available throughout checkout and all payment failures.

### Purchased university report PDF

`UniversityReportPdfService` reads an `ACTIVE` entitlement and its frozen purchased snapshot directly through persistence ports. It never calls `UniversityReportAssembler`, Jev, the decision policy, OpenAI, narrative generation, evidence retrieval or Stripe, so payment-confirmed `university-report-v1` content — not current application state — is the sole source of PDF text.

Content and presentation versions are separate: `university-report-v1` identifies what the customer bought; `university-report-pdf-v1` identifies how those purchased fields are laid out. `OpenHtmlUniversityReportPdfRenderer` is a local pure-Java implementation using OpenHTMLToPDF/PDFBox with built-in fonts, inline CSS and no images, remote stylesheets, CDN resources or other network inputs. Persisted safe HTTP(S) evidence URLs are emitted only as hyperlinks and are never fetched.

On the first authorised `GET /university-assessment/{UUID}/report.pdf` the service returns an existing artifact's exact bytes without loading the snapshot or invoking the renderer; otherwise it renders once, requires a `%PDF-` signature, rejects empty or over-limit output, calculates SHA-256, sanitises a course-based filename and calls `saveIfAbsent`. The insert uses PostgreSQL `ON CONFLICT DO NOTHING`, so simultaneous first requests converge on one immutable artifact without a distributed lock. Existing rows are never updated; subsequent downloads and application restarts return identical bytes and SHA-256. Rendering or persistence failure leaves the entitlement, purchased snapshot and paid HTML report unchanged and returns a generic message without provider detail.

The university PDF is a deliberately parallel implementation rather than a generic abstraction over the professional PDF. Forcing shared generic rendering would have required changing working professional code for no current benefit; the two renderers can be unified later behind a small port if a third journey justifies it.

### University web routes, UX and local development

| Route | Purpose |
| --- | --- |
| `GET /university-assessment` | Public form |
| `POST /university-assessment` | Validate, submit once, Post/Redirect/Get |
| `GET /university-assessment/{UUID}` | Read-only free result |
| `POST /university-assessment/{UUID}/checkout` | Start checkout (CSRF-protected) |
| `GET /university-assessment/{UUID}/report` | Frozen paid report, `ACTIVE` only |
| `GET /university-assessment/{UUID}/report.pdf` | Immutable purchased PDF, `ACTIVE` only |
| `GET`/`POST /university-assessment/{UUID}/checkout/local[/confirm]` | `local-fixture` only |
| `POST /payments/stripe/university-webhook` | `live-payment` only, signature-authenticated |

The result page is written for students, not software professionals. It explains what Future Readiness means before showing numbers, presents strengths and development areas side by side, uses "This assessment suggests…", "This course appears to develop…" and "One area you may want to strengthen…", and states plainly that no assessment can tell you whether a degree is right for you. Alarmist and verdict language is absent by construction and asserted against in tests. Result responses use `private, no-store` and request `noindex,nofollow`. CSRF is preserved for every browser POST; only the two Stripe webhook paths are exempt because Stripe's signature authenticates them.

For local startup, run:

```shell
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local-fixture \
  -Dspring-boot.run.arguments="--app.professional.fixture-id=senior_software_architect --app.university.fixture-id=computer_science"
```

`app.university.fixture-id` supports only the exact IDs `computer_science`, `economics`, `graphic_design`, `history`, `mechanical_engineering` and `nursing`. There is no fuzzy matching: a missing or unsupported ID fails at startup. The fixture ID is technical configuration; `UniversityAssessmentState.courseTitle` remains the user's real course. Fixture data is illustrative development data chosen to exercise contrasting presentation paths — it is not empirical intelligence about any subject, course or institution, and the UI marks it prominently as development data. Both journeys' fixture IDs are required under `local-fixture` because each journey wires its own provider independently.

### University limitations and next sub-phases

- There is no live university evidence adapter yet. Only the port, model, persistence and deterministic fixture exist.
- There is no CV- or prospectus-upload equivalent for the university journey; input is manual only.
- There is no occupation-classification equivalent, because the university evidence request is course-shaped rather than occupation-shaped.
- As in the professional journey, there are no user accounts. The opaque assessment UUID is a temporary pre-authentication access reference, not production-grade authorisation. Anyone holding a saved URL may reach that assessment and an entitled report. There is no public listing route, but account-bound ownership belongs in a later phase.

## A-level journey

The A-level journey answers a third product question: *do these A-level choices preserve strong future options in an AI-shaped economy?* It is aimed at UK sixth-form students, who are often 16 to 18 and reading with a parent, so its wording is deliberately the most careful of the three.

```text
A-level input
    ↓
ALevelAssessmentState
    ↓
ALevelJudgmentProvider
    ↓
a-level-v1 judgments
    ↓
ALevelDecisionPolicyV1
    ↓
saved assessment
    ↓
narrative + evidence
    ↓
ALevelReportAssembler
    ↓
FREE / PAID
    ↓
A-level entitlement
    ↓
frozen purchase
    ↓
a-level-report-pdf-v1
```

### A-level input and subject canonicalisation

`ALevelAssessmentState` is a dedicated immutable domain record with no JPA annotations: selected subjects, optional predicted grades, strengths and interests, preferred degree areas, preferred career areas, and optional country and free-text context. Only the subjects are required, and between two and five distinct subjects are accepted (UK students usually take three or four). A student is never required to know a degree or a career: every other field may be empty.

`ALevelSubjects.canonicalise` maps common abbreviations and spellings onto consistent display names — `maths` and `Maths` become `Mathematics`, `comp sci` and `computing` become `Computer Science` — and is applied inside the record's constructor so the same combination is stored, judged and displayed identically however it was typed. It is idempotent, and an unrecognised subject is accepted and title-cased rather than rejected, so a student is never blocked because their school offers something unusual.

The alias table **maps text to text only**. There is no subject-points table anywhere in the journey: `ALevelDecisionPolicyV1` never learns that Mathematics exists, and the local fixture is keyed by a configured identifier rather than by the subjects submitted. `ALevelSubjectsTest` asserts by reflection that the alias table holds no numeric value, and `ALevelDecisionPolicyV1Test` asserts that two completely different subject lists produce an identical result for the same fixture. Together these pin the rule that the journey scores a judged combination, never a ranked list of subjects.

### A-level judgment schema

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

1. The `degree_pathway_breadth` question explicitly instructs the model to judge only the general capability profile and **not** to apply any specific university's published entry requirements. No other question mentions requirements at all.
2. `ALevelDecisionPolicyV1` has no concept of a prerequisite; it combines capability judgments only.
3. `ALevelNarrativeValidator` rejects any narrative that states a requirement, names a required subject, or quotes grade requirements such as `AAA`. The safe phrasing — telling the student to check a course's own published requirements — is explicitly permitted and tested.
4. The evidence layer has a dedicated `DEGREE_PREREQUISITES` category, and every item carries a source, publisher, geography and date.

The report keeps fact and assessment visually and structurally separate. Evidence carries a standing note: *"These are factual notes with their own sources. They are shown separately because subject requirements are facts to check, not part of your assessment, and they do not change any measure above."* A prerequisite note is therefore presented as something to look up, never translated into "your subject choices are bad".

### A-level decision policy

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

### A-level explainability

Explainability uses signed contribution logic over a separate `EXPLANATION_WEIGHTS` map covering all ten Score dimensions (summing to 1). It is deliberately flatter than the Future Optionality weights so a capability carried mainly by a supporting measure can still be named as a strength, and it never contributes to an authoritative measure. For each dimension the signed effect is `weight × (normalised − 0.5)`; positive effects become `STRENGTH` factors and negative effects `DEVELOPMENT_OPPORTUNITY` factors, so a dimension can never appear on both sides.

Factor wording explains capability contribution rather than judging a subject: "Your combination gives you a strong quantitative foundation", or "Your combination develops less technical and digital depth than some alternatives". Nothing says "Maths is good" or "Geography is weak", and no factor names a subject at all.

### A-level persistence and migrations

Flyway **V13** adds `a_level_assessment` with input, provenance and headline decision, plus ordered judgment, measure and factor tables; a check constraint pins both journey columns to `A_LEVEL`. **V14** adds `a_level_assessment_narrative`, **V15** the evidence bundle and items, **V16** the entitlement, purchased snapshot and processed payment event, and **V17** the purchased PDF. A-level tables are entirely separate from professional and university tables. V1–V12 are unchanged. **V18** later added the professional `cv_extractor_version` column (see
Professional CV input). The repository is now at Flyway v18.

Selected subjects, strengths, degree areas and career areas are JSONB arrays (order preserved); predicted grades are a JSONB object keyed by canonical subject (PostgreSQL does not preserve jsonb object key order, so grade display order follows the subject array). `ALevelAssessmentStore` inserts only, and `GET /a-level-assessment/{UUID}` loads saved records without invoking any provider or policy.

### A-level narrative

`ALevelNarrativeService` runs after the assessment transaction commits, building `ALevelNarrativeContext` from the persisted snapshot. `OpenAiALevelNarrativeGenerator` is the normal adapter with one typed structured-output request; `LocalFixtureALevelNarrativeGenerator` is selected only by `local-fixture`. Profile wiring permits exactly one generator, and a failure stores `FAILED` rather than looking up another implementation. Historical narratives are never regenerated or upgraded.

The `a-level-narrative-v1` prompt states that saved measures, bands, confidence, status, strengths and development opportunities are authoritative and must never be recalculated or repeated numerically. It forbids stating university entry requirements or subject prerequisites, quoting grade requirements, guaranteeing or predicting admission, inventing salary figures or employment rates, citing studies, calling a combination future-proof/safe/risky/wrong/bad, ranking subjects, telling the student to drop or change a subject, telling them which career to pursue, and saying their choices will limit their future. It requires explaining `FOCUSED` as a legitimate choice rather than a limitation, and restricts practical actions to things a student can do *alongside* existing subjects.

`ALevelNarrativeValidator` is the deterministic backstop: it rejects numeric and percentage claims, currency figures, invented prerequisites and grade requirements, and admission promises, subject verdicts or drop instructions. Rejection fails the narrative attempt and can never change the saved assessment.

### A-level evidence

`ALevelEvidenceProvider` is the vendor-neutral port receiving a narrow request (assessment UUID, subjects, preferred degree and career areas, explicit geography, journey). Categories are `DEGREE_PREREQUISITES`, `COURSE_ENTRY_CONTEXT`, `SUBJECT_PATHWAY_CONTEXT`, `LABOUR_MARKET_CONTEXT` and `SKILL_DEMAND_CONTEXT`; the version is `a-level-evidence-v1`. Evidence cannot change any measure, status or confidence, and a provider failure is recorded as `FAILED` with no fallback. Geography comes from the submitted country when supplied, otherwise the configured default.

No live A-level evidence adapter exists. The request contains subjects and preferred areas, without a specific university, course and entry year. Individual course providers set their own entry requirements, so general knowledge cannot establish a prerequisite or admission guarantee. Prompt 18B chose verified ingestion of official course/admissions pages with provider, course, year, source URL and review date as the safer future strategy; generic search scraping and cross-site CSS crawlers are excluded. Degree prerequisites, course entry context, subject pathway context, labour-market context and skill-demand context remain unavailable as live evidence. Fixture items are framed as neutral factual context, carry no fake university or government URL, and state explicitly that they do not change the assessment. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md).

### A-level report, payment, freezing and PDF

`ALevelReportAssembler` is pure deterministic composition of saved records (`a-level-report-v1`). FREE and PAID use the same saved status, confidence, six measures, bands and factors. FREE includes the subjects, all measures with concise definitions, main strengths, development opportunities, a short interpretation and the admissions disclaimer. PAID adds the full narrative, per-measure explanations, practical actions, deterministic guidance, degree and career-area context, saved evidence and fuller methodology.

Practical guidance uses `KEEP_DEVELOPING`, `WORTH_STRENGTHENING` and `CONSIDER_SUPPLEMENTING`. Every item is something a student can do alongside their existing subjects — building coding or data literacy, strengthening written argument, researching course requirements early — and `ALevelReportAssemblerTest` asserts across all six fixtures that no guidance ever suggests dropping, swapping or retaking a subject. Every combination is told to check real published course requirements rather than trust a guess.

A-level entitlement is entirely separate from professional and university entitlement: separate tables with foreign keys to `a_level_assessment`, a separate `A_LEVEL_REPORT_STRIPE_PRICE_ID`, a separate webhook at `POST /payments/stripe/a-level-webhook`, a separate configurable webhook secret, and `product=a_level_report` Stripe metadata so events for other products are ignored rather than errored. No price is hard-coded. Confirmation is idempotent under a row lock, and the frozen snapshot is authoritative after purchase so a future `a-level-report-v2` cannot change an earlier `v1` purchase.

`ALevelReportPdfService` renders only from the frozen purchased snapshot, never calling the assembler, Jev, the policy, OpenAI, evidence or Stripe. `OpenHtmlALevelReportPdfRenderer` uses built-in fonts and inline CSS with no network inputs; persisted evidence URLs are emitted as hyperlinks and never fetched. `a-level-report-pdf-v1` versions the layout separately from the purchased content, and `ON CONFLICT DO NOTHING` makes the stored artifact immutable, so repeat downloads and restarts return identical bytes.

### A-level web routes, UX and local development

| Route | Purpose |
| --- | --- |
| `GET /a-level-assessment` | Public form |
| `POST /a-level-assessment` | Validate, submit once, Post/Redirect/Get |
| `GET /a-level-assessment/{UUID}` | Read-only free result |
| `POST /a-level-assessment/{UUID}/checkout` | Start checkout (CSRF-protected) |
| `GET /a-level-assessment/{UUID}/report` | Frozen paid report, `ACTIVE` only |
| `GET /a-level-assessment/{UUID}/report.pdf` | Immutable purchased PDF, `ACTIVE` only |
| `GET`/`POST /a-level-assessment/{UUID}/checkout/local[/confirm]` | `local-fixture` only |
| `POST /payments/stripe/a-level-webhook` | `live-payment` only, signature-authenticated |

The form opens by saying there are no good or bad subjects and that the student does not need to know what they want to do yet. Degree and career areas are explicitly optional and labelled "It is completely fine to be undecided". A combination with too few subjects is a friendly field error, not a server error. The result page explains what Future Optionality means before showing any number, states that a focused combination is a reasonable choice, and tells the student to check a specific course's own published requirements. Result responses use `private, no-store` and request `noindex,nofollow`; CSRF is preserved for every browser POST.

For local startup, run:

```shell
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local-fixture \
  -Dspring-boot.run.arguments="--app.professional.fixture-id=senior_software_architect --app.university.fixture-id=computer_science --app.a-level.fixture-id=maths_economics_geography"
```

`app.a-level.fixture-id` supports only `maths_economics_geography`, `mathematics_physics_computer_science`, `biology_chemistry_mathematics`, `english_history_politics`, `art_design_media` and `mixed_balanced`. There is no fuzzy matching: a missing or unsupported ID fails at startup. All three journeys' fixture IDs are required under `local-fixture` because each wires its own provider independently. Fixture data is illustrative development data chosen to exercise contrasting presentation paths — it is not an empirical claim about any subject or combination.

### A-level limitations and next sub-phases

- There is no live A-level evidence adapter yet. Only the port, model, persistence and deterministic fixture exist, so no real prerequisite is shown to anyone today.
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

These four are the only routes this phase made public. Every pre-existing permit rule, the CSRF requirement on browser POSTs, the Stripe webhook exemptions, the profile restrictions on local payment routes and the ACTIVE-entitlement requirement for paid reports and PDFs are untouched.

### The journey selector

The landing page asks "What are you thinking about?" and offers three options in the visitor's own words rather than in the system's vocabulary:

| Framing on the page | Journey | Question it answers | Principal concept |
| --- | --- | --- | --- |
| My current job | `/professional-assessment` | How exposed is my role, and what still depends on me? | AI exposure and human advantage |
| A university course | `/university-assessment` | How well does this course position me for an AI-shaped labour market? | Future readiness |
| My A-level subjects | `/a-level-assessment` | Do these choices preserve strong future options? | Future optionality |

Each card states its own question and links only to its own journey. Nothing on the page invites the visitor to compare the three numbers, because those numbers do not share a scale, a rubric or a meaning.

### Shared chrome

`templates/fragments/layout.html` provides `head(pageTitle)`, `siteHeader(active)`, `devBanner`, `fixtureNotice` and `siteFooter`, included with plain `th:replace` — there is no layout dialect and no new templating framework. Every one of the 28 page templates includes the same four fragments, exposes a single `<h1>`, and carries a skip link to `#main`.

`ProductShellAdvice` is a `@ControllerAdvice` supplying `developmentMode`, `developmentFixtures` and `currentYear` to every view. It resolves both of its collaborators through `ObjectProvider` and falls back to `DevelopmentModeStatus.inactive()` and `Clock.systemUTC()`. That keeps sliced `@WebMvcTest` classes working without each one declaring the bean, and — more importantly — makes the *safe* state the default: a missing bean renders no development chrome rather than leaking it.

### Development mode is a profile fact, never a request fact

`DevelopmentModeStatus` is derived solely from the `local-fixture` profile, through two mutually exclusive profile-scoped beans in `ProductShellConfiguration`. It can never be influenced by a request parameter, a header or a cookie. Under any other profile the banner, the fixture notice and the local-checkout controls are structurally absent, not merely hidden with CSS.

One subtlety worth recording, because it leaked once and was caught by a test: Thymeleaf passes plain `<!-- ... -->` comments straight through into the response. Every template comment is therefore the parser-level form `<!--/* ... */-->`, and `ProductShellConsistencyTest` fails the build if a plain comment reappears.

### Styling

`static/css/app.css` is a single hand-written stylesheet built on CSS custom properties for colour, spacing, radius and shell width. No UI framework, CDN asset or client-side script was introduced; the pages are server-rendered HTML and remain usable with JavaScript disabled. The previous `professional-assessment.css` was removed once all templates pointed at the shared stylesheet.

Accessibility and responsiveness basics are structural rather than decorative: a skip link on every page, `aria-current="page"` on the active primary destination, a single `<h1>` per page, form errors announced with `role="alert"` and prefixed by a textual `Error:` through `::before` so the error is never conveyed by colour alone, and a single `max-width: 42rem` breakpoint that collapses grids and stacks definition lists. `prefers-reduced-motion` is respected.

### Free and paid, said the same way everywhere

Every result page uses the same ordering — headline, how to read the numbers, the measures, why, a short interpretation, the free-versus-full comparison, then context — and every one carries its own journey-specific caveat about what its indicator is not. The paid tier is framed consistently as **payment unlocks depth, not truth**: the deterministic assessment a visitor sees for free is the same assessment, and paying adds explanation, personalised actions and evidence rather than a different or truer answer.

Prices are only ever read from existing configuration. No monetary amount is hard-coded into domain logic, and no page fetches live Stripe data during an ordinary render.

The PDF call to action exists only inside the three `paid-report.html` templates, which render only for an ACTIVE entitlement. `ProductShellConsistencyTest` pins this as an invariant across every template, so a free result or an unavailable-report page cannot grow a download link by accident.

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

Every response carries a `Content-Security-Policy` that starts from `default-src 'self'` and forbids scripts outright, which the application can afford because it serves no JavaScript and no inline styles. `form-action` names Stripe's hosted checkout, because paying leaves the site through a redirect that follows a form submission. Alongside it: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and a `Permissions-Policy` that switches off camera, microphone, geolocation and the rest. The PDF response keeps its own `private, no-store` and attachment disposition.

### Request hardening

Three filters and a set of bounds sit in front of the journeys. They are documented in full in [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md); the parts that affect how the application is put together are:

- **Correlation runs first.** `RequestCorrelationFilter` is at highest precedence, so a request rejected by security still carries an `X-Request-ID` in its response and in every log line. An inbound identifier is reused only if it matches a narrow pattern, because it reaches both a log line and a response header.
- **Rate limiting runs after security.** `RateLimitFilter` is ordered behind the Spring Security chain so a missing or stale CSRF token stays a 403 and never spends an allowance. The limiter is in-process and keyed on the container-reported client address; it is deliberately not reading forwarded headers while no trusted proxy is configured.
- **Collections are bounded as well as characters.** `@BoundedTextList` caps how many entries a textarea may produce and how long any one may be, counting them exactly as the form mappers split them. A character limit alone leaves the number of judged, stored and rendered list entries unbounded.
- **Extraction stops early.** PDF text is read a page at a time and abandoned once it passes the cap; DOCX inflation is bounded by Apache POI's own ZIP guards, which abort before the XML is materialised. Those guards are JVM-wide statics, which is POI's API rather than a choice made here.

### Production profile and configuration

A `prod` profile carries the production-only differences — secure cookies, forwarded-header handling, constrained Actuator exposure, quieter logging and connection-pool hooks — and nothing else. Everything the application needs in every environment stays in the base property file, so there is one place to read for what a setting does.

`StartupConfigurationValidator` refuses to start on a contradiction or a gap. Asking for `prod` and `local-fixture` together, or for `live-payment` alongside the simulator that overrides it, is a request the application cannot satisfy as asked, so it stops rather than silently choosing. Missing configuration is checked per capability, which is what keeps `local-fixture` free of every production secret. No failure message ever repeats a configured value.

`PublicBaseUrl` is the single authoritative address, and the three per-journey report base URLs now default to it. Stripe return links are built from configuration and never from a `Host` header, because a header is chosen by the caller.

### Operational surface

Health is published and nothing else is. Liveness depends on no external provider — a Stripe or OpenAI outage must not look like a dead process — while readiness includes the database, without which no request can be served usefully. Health reports a bare status with no component detail, and `/actuator/**` is denied in the filter chain in addition to being unexposed, so publishing an endpoint by accident in configuration is not enough to make it reachable.

`JourneyMetrics` counts assessments, enrichment failures, payment confirmations and rate-limit rejections, tagged only by journey or group. It is observational by construction: it swallows its own failures, and every collaborator resolves it optionally and falls back to an instance that records nothing, so missing instrumentation can neither fail a request nor prevent a context from starting.

### What is left

Live provider integration: real Jev judgments, real OpenAI narratives, real Stripe checkout and signed webhooks, and live evidence retrieval. Those need real credentials and real calls, and are listed with the rest of the outstanding work at the end of [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md).
