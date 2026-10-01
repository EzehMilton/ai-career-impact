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

- **Professional** assesses how AI may affect a person's current work: AI Exposure, Human Advantage, AI Complementarity, and Role Adaptability. Its indicators describe characteristics of the current role.
- **University** assesses how a course supports future readiness and how its possible career paths may be exposed to AI. A high or low value here cannot be interpreted as the same kind of risk as a professional assessment.
- **A-level** assesses whether a subject combination preserves useful future options, given the student's interests and strengths. Its central idea is optionality, not present-day job exposure.

The journeys therefore share the `AssessmentDecision` result shape, but not a universal meaning for its indicator. Each Java decision policy will define its own dimensions, aggregation, labels, and thresholds.

## Intended modules

- `assessment`: journey inputs, assessment application services, and assessment lifecycle.
- `judgment`: atomic judgment contracts and later Jev adapters.
- `decision`: deterministic, journey-specific Java policies.
- `narrative`: explanation generation from authoritative decisions and evidence.
- `evidence`: sourced external facts and provenance.
- `career`: career and occupation concepts shared where their meaning is genuinely common.
- `report`: deterministic tiered report assembly and presentation models.
- `payment`: payment and entitlement workflows.
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

The first live adapter uses the official ONS Annual Population Survey regional occupation dataset exposed by the Nomis API (`APS218`). It performs one bounded request for the latest UK occupational employment estimate, using a saved SOC 2020 classification. It does not scrape web pages and does not infer or fuzzily match an occupation code from the submitted title. For now the `live-evidence` profile uses an exact operator-configured mapping requiring `PROFESSIONAL_EVIDENCE_PROFESSION`, `PROFESSIONAL_EVIDENCE_CANONICAL_OCCUPATION` and `PROFESSIONAL_EVIDENCE_SOC2020_CODE`; the submitted title must match exactly. This is intentionally limited: V1 proves the replaceable classifier and evidence-adapter boundaries but does not yet provide broad official occupation lookup, trend series, salary, skills or automation research for every role.

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
