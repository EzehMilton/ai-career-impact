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
- `report`: report assembly, premium verification, and presentation models.
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

Read flow: GET `/professional-assessment/{UUID}` → application service → store → PostgreSQL → saved `StoredProfessionalAssessment` → existing view mapper → Thymeleaf. The GET does not invoke the judgment provider or policy. Post/Redirect/Get means a browser refresh repeats only the GET, avoiding a second paid Jev request.

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

The weights and thresholds in `professional-decision-v1` are transparent V1 product policy. They are not claimed to be scientifically validated universal truths. Changing them requires a new policy version so an existing version never silently changes meaning.

AI Exposure describes how readily the current work may be performed, automated, or materially changed by AI. It is not a probability of job loss. Human Advantage is separately calculated because a role can combine meaningful AI exposure with strong reasons for continued human involvement. AI Complementarity describes potential augmentation rather than automation exposure.

Role Adaptability describes characteristics of the role that provide scope for continued human value and AI-enabled evolution. It does not measure the individual's personal motivation, willingness to retrain, learning ability, financial flexibility, geographic mobility, or personal circumstances.
