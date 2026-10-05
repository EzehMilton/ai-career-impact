# Production readiness

What the application bounds, throttles, refuses and requires before it will start. This covers the hardening done in phases 17A, 17B and 17C. What remains is live integration, which is deliberately out of scope here and listed at the end.

No credential appears in this document, and none should ever be added to it.

## Security model in one paragraph

There are no accounts. Nothing is protected by authentication, and nothing should be added on the assumption that it is. Access to a paid report or its PDF is granted by an **ACTIVE entitlement** checked in the application layer; access to a past free result is granted by **knowing its opaque UUID**. Stripe webhooks authenticate themselves with a signature. The security filter chain publishes routes and enforces CSRF; it is not what keeps paid content private.

## Request and input limits

### Text fields

Every free-text field is capped by Bean Validation and fails with a message next to the field. Nothing is silently truncated.

| Journey | Field | Maximum |
| --- | --- | --- |
| Professional | Job title | 120 |
| Professional | Work description | 40–4,000 |
| Professional | Responsibilities | 4,000 |
| Professional | Skills | 2,000 |
| Professional | Years of experience | 0–70 |
| University | Course title | 160 |
| University | Course description | 40–4,000 |
| University | Key modules | 4,000 |
| University | Skills developed | 2,000 |
| University | Preferred career areas | 2,000 |
| University | Institution | 160 |
| University | Country | 80 |
| A-level | Selected subjects | 500 |
| A-level | Predicted grades | 500 |
| A-level | Strengths and interests | 1,000 |
| A-level | Preferred degree areas | 1,000 |
| A-level | Preferred career areas | 1,000 |
| A-level | Country | 80 |
| A-level | Additional context | 2,000 |

### Collections

A character limit alone does not bound the *shape* of a submission: 4,000 characters can arrive as 2,000 one-character lines, each becoming a list entry that is judged, stored and rendered. `@BoundedTextList` caps both dimensions, counting entries exactly as the form mappers split them.

| Journey | Field | Maximum entries | Maximum per entry |
| --- | --- | --- | --- |
| Professional | Responsibilities | 40 | 300 |
| Professional | Skills | 40 | 120 |
| University | Key modules | 40 | 200 |
| University | Skills developed | 40 | 120 |
| University | Preferred career areas | 20 | 120 |
| A-level | Strengths and interests | 20 | 200 |
| A-level | Preferred degree areas | 20 | 120 |
| A-level | Preferred career areas | 20 | 120 |

A-level subjects keep their existing domain rule of **2 to 5** distinct subjects.

### Request sizes

| Setting | Value | Property |
| --- | --- | --- |
| Ordinary form post | 256 KB | `server.tomcat.max-http-form-post-size` |
| Upload, per file | 5 MB | `spring.servlet.multipart.max-file-size` |
| Upload, whole request | 6 MB | `spring.servlet.multipart.max-request-size` |
| Read-and-discard headroom | 8 MB | `server.tomcat.max-swallow-size` |

The swallow headroom matters: without room above the request limit, Tomcat resets the connection instead of replying, and someone uploading a large file sees a broken connection rather than the page explaining the limit.

### CV extraction

Upload size bounds the download, not the work. A DOCX is a ZIP, so a small file can inflate enormously. These bound the work:

| Setting | Default | Property |
| --- | --- | --- |
| Extracted characters | 100,000 | `app.professional.cv.maximum-extracted-characters` |
| PDF pages | 50 | `app.professional.cv.maximum-pages` |
| Largest single DOCX entry after inflation | 16 MiB | `app.professional.cv.maximum-entry-bytes` |
| Smallest allowed compression ratio | 0.01 | `app.professional.cv.minimum-inflate-ratio` |

Both parsers stop early rather than building an unbounded string and rejecting it afterwards. A PDF is read one page at a time and abandoned as soon as the text passes the cap, so peak cost is the cap plus one page. A DOCX is bounded by Apache POI's own ZIP guards, which abort during inflation.

**Known limitation:** POI's guards are JVM-wide statics, not per-document settings. They are applied once at startup and are identical for every upload. That is POI's API, not a choice made here.

PDF and DOCX parsing is deterministic and separate from structured profile extraction. Extension,
MIME and magic-signature checks, filename sanitisation, no OCR, and all existing resource limits
remain. Raw CV bytes and extracted text are never persisted or logged. OpenAI performs typed
information extraction only; confirmed data then enters the unchanged Java/Jev assessment pipeline.

## Rate limiting

In-process, fixed window, no Redis.

| Group | Routes | Default permits | Window |
| --- | --- | --- | --- |
| `assessment-submission` | POST on the three assessment start routes | 10 | 1 minute |
| `cv-upload` | POST `/professional-assessment/cv` | 5 | 1 minute |
| `checkout-start` | POST on the three checkout routes | 10 | 1 minute |

Configurable through `app.rate-limit.enabled`, `app.rate-limit.window`, and `app.rate-limit.<group>.permits`.

Never limited: reading any page, a past result, a report, a PDF, or the stylesheet. Stripe webhooks are never limited — they are signature-verified and Stripe retries, so throttling them would only delay a confirmed payment.

**Keying** is the client address as the container reports it. Forwarded headers are deliberately *not* consulted: nothing configures a trusted proxy yet, so `X-Forwarded-For` is attacker-controlled and would let one client present itself as thousands. Behind an unconfigured proxy this makes the limit coarse rather than wrong.

**Scope** is one application instance. Behind a load balancer the effective allowance is the configured number multiplied by the instance count.

**Ordering** is deliberate and tested. The limiter runs *after* Spring Security, so a POST with a missing or stale CSRF token is refused as 403 before reaching it: a broken form never spends someone's allowance and is never told it is being throttled when the real problem is its token.

## Request correlation

Every response carries an `X-Request-ID`, and every log line carries the same value in `%X{requestId}`.

- A supplied `X-Request-ID` is reused if it matches `[A-Za-z0-9._-]{8,64}`.
- Anything else — too short, too long, spaces, control characters, markup — is replaced with a fresh UUID rather than echoed. The value reaches both a log line and a response header, so an unchecked one would be a log-injection and response-splitting problem.
- The MDC entry is always cleared at the end of the request, because threads are pooled.

The filter runs first in the chain, so a request rejected by security still gets an identifier.

## Error semantics

| Status | Means | Page |
| --- | --- | --- |
| 400 | Input could not be understood | Field-level messages on the form |
| 403 | CSRF token missing or stale, or no ACTIVE entitlement | `error/403.html` |
| 404 | Unknown route, unknown or malformed assessment id | `error/404.html` or the journey's not-found page |
| 413 | Upload larger than the limit | `error/413.html` |
| 429 | Rate limit reached | `error/429.html`, with `Retry-After` |
| 500 | Unexpected failure | `error/5xx.html` |

No page exposes an exception class, a stack trace, SQL, a provider name, a configuration value or a file path. 403 and 404 are kept distinct on purpose: a refused resource and a missing one are different facts.

**Note:** an upload that exceeds the size limit is rejected by the multipart parser before a handler is chosen, so it is answered by the shared 413 page rather than by the upload form. The page says what happened and what to do instead.

## Cache control

| Response | Cache-Control |
| --- | --- |
| Stylesheet | `max-age=300, public` |
| Everything else | `no-cache, no-store, max-age=0, must-revalidate` |

Almost every page can show someone their own assessment, so no-store is the right default. The stylesheet is the single exception: shared by every visitor, containing nothing personal. Spring Security's cache-control writer is scoped to exclude `/css/**` rather than switched off, so the secure default still applies everywhere it matters.

Five minutes is short enough that a deployed change is picked up quickly without content hashing.

## Output safety

No template uses `th:utext` or any unescaped expression, and a test fails the build if one appears. Submitted HTML is reflected back as text: `<script>alert(1)</script>` renders as `&lt;script&gt;alert(1)&lt;/script&gt;`.

The Content-Security-Policy from 17A forbids scripts outright (`script-src 'none'`), which the application can afford because it serves no JavaScript at all.

## URL and SSRF review

Evidence source links are the only externally supplied URLs the product displays. They are validated at construction, in all three journeys, to be absolute `http` or `https`. `javascript:`, `data:`, `file:`, `ftp:`, `jar:`, `classpath:` and relative references are refused.

**There is no SSRF surface.** The only server-side HTTP client in the application is the Nomis evidence adapter, and its base URL is a hard-coded constant. No user-supplied URL is ever fetched by the server: evidence links are rendered as anchors and nothing more. The PDF renderer is given a null base URI and emits no `img`, stylesheet or font reference, so it resolves no external resource either.

## Redirects

There is no open-redirect surface. Every `redirect:` target in the application is a fixed internal path with a typed `UUID` path variable. Stripe success and cancel URLs are built from configured application settings, never from a query parameter or a request header.

## PDF safety

Unchanged in this phase and re-confirmed: an ACTIVE entitlement is required; the filename is derived and sanitised; `Content-Disposition: attachment`; `X-Content-Type-Options: nosniff`; `Cache-Control: private, no-store` on both the download and the refusal; output size is validated; the stored artifact is immutable; all text is escaped before HTML-to-PDF; no remote resource is fetched.

## Logging safety

Audited line by line. No log statement records CV text, form content, assessment descriptions, contact details, API keys, webhook secrets or Stripe payloads. What is logged is an assessment UUID, an exception class name, a rate-limit group name, or a fixture-mode warning. Stack traces are written for genuinely unexpected failures only, and no exception message in the codebase interpolates user-submitted text — every interpolated value is a configuration key, question id, dimension id or report version.

## Profiles

| Profile | What it turns on |
| --- | --- |
| *(none)* | Ordinary development against real AI adapters. Requires AI credentials. |
| `local-fixture` | Every external provider replaced by a deterministic fixture. Requires no secret of any kind. |
| `prod` | Production defaults: secure cookies, proxy awareness, constrained Actuator, quiet logging, fail-fast configuration. |
| `live-payment` | Real Stripe hosted checkout and webhooks. |
| `live-evidence` | Real external evidence providers. |

`StartupConfigurationValidator` refuses these combinations rather than silently picking a winner:

| Combination | Why it is refused |
| --- | --- |
| `prod` + `local-fixture` | Production must never serve a fixture result. |
| `local-fixture` + `live-payment` | The simulator overrides Stripe, so no real payment would be taken. |
| `local-fixture` + `live-evidence` | The fixture provider overrides the live one, so no real evidence would be used. |

Valid combinations include `prod`, `prod,live-payment`, `prod,live-payment,live-evidence` and `local-fixture` on its own.

**Note:** the judgment and narrative adapters are selected by `!local-fixture`, so any profile other than `local-fixture` uses the real Jev and OpenAI adapters and needs their credentials. There is deliberately no fallback from a real provider to a fixture.

`prod` alone therefore requires both `TYPESAFE_API_KEY` and `OPENAI_API_KEY`. The startup validator checks their presence and lists either missing variable by name. The live verification runbook records what has and has not been exercised against real providers: [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md).

## Fail-fast configuration

Startup stops, with every problem listed at once, when a capability is enabled but not configured. Messages name the setting and never its value, because startup failures get pasted into tickets.

- **`prod`** requires `app.public-base-url`, and requires it to be `https` unless `app.public-base-url-allow-http=true` is set deliberately.
- **`prod`** requires `TYPESAFE_API_KEY` and `OPENAI_API_KEY` because it activates real AI adapters.
- **`live-payment`** requires the Stripe secret, all three webhook secrets and all three price ids.
- **`live-evidence`** requires the professional occupation mapping.
- **`local-fixture`** requires none of the above. Keeping local development free of production secrets is a property worth protecting, and a test asserts it.

The datasource is deliberately not checked here: Spring Boot already refuses to start without one and says so more clearly than a custom check could.

## Public base URL

One setting, `app.public-base-url` (`APP_PUBLIC_BASE_URL`), is the authoritative address. Every absolute link handed to someone else — Stripe success and cancel URLs — is built from it. The three per-journey `app.<journey>.report.base-url` settings now default to it and exist only so a journey can be pointed elsewhere deliberately.

It is never inferred from a `Host` header. A header is chosen by the caller, and a payment return address chosen by the caller is a payment return address an attacker can choose.

Validated as: absolute, `http` or `https`, with a host, no query, no fragment, and normalised to a trailing slash so resolution is predictable.

## Reverse proxy and forwarded headers

Under `prod`, `server.forward-headers-strategy=framework`. Spring handles `X-Forwarded-*`; nothing in this codebase parses them. This is safe only because the documented deployment shape puts a proxy in front that overwrites those headers — exposing the application directly to the internet under this profile would let a caller set them.

**Rate-limit keying is unchanged.** The limiter still uses the container-reported client address. With `forward-headers-strategy=framework` that address becomes the real client under `prod`, which is the improvement this phase makes possible; under any other profile it stays the direct peer. The limiter was deliberately not given its own forwarded-header handling.

## Session cookies

| Setting | Production value |
| --- | --- |
| `HttpOnly` | true |
| `Secure` | true |
| `SameSite` | `Lax` |
| Timeout | 30 minutes |

`Lax` rather than `Strict` because returning from Stripe's hosted checkout is a cross-site top-level navigation back into the application, and `Strict` would withhold the cookie on arrival. `Lax` sends it on exactly that navigation while still withholding it from cross-site POSTs. `None` is not used: it would require `Secure` anyway and would broaden cookie sending for no gain here.

Local development is unaffected — these apply only under `prod`, so plain HTTP still works.

**What the session contains:** after a successful CV upload, only the structured
`ProfessionalProfile` and `ProfessionalAssessmentInputMetadata` (sanitised filename, content type,
parser version and timestamp). Both are removed on confirmation or when manual entry starts. No raw
CV bytes, extracted CV text, secret, payment payload or AI prompt is placed in the session.

## Actuator

| Endpoint | Exposed | Reachable |
| --- | --- | --- |
| `/actuator/health` | yes | yes |
| `/actuator/health/liveness` | yes (`prod`) | yes |
| `/actuator/health/readiness` | yes (`prod`) | yes |
| `/actuator/info` | yes (`prod`) | yes |
| everything else | no | denied in the filter chain |

Two independent controls: the endpoint is not exposed, *and* the security chain denies `/actuator/**` apart from the four above. Publishing one by accident in configuration is therefore not enough to make it reachable.

Health reports a bare status. `show-details=never` and `show-components=never`, so an unauthenticated caller learns that the application is up and nothing about what is inside it.

## Liveness and readiness

- **Liveness** (`livenessState`) answers "is this process working". It depends on nothing external. A Stripe, OpenAI, Jev or Nomis outage must not look like a dead process, or an orchestrator will restart healthy containers in a loop for the duration of someone else's incident.
- **Readiness** (`readinessState`, `db`) answers "can this instance serve a useful request". It includes the database, because without it nothing can be assessed, stored or retrieved. It deliberately excludes every external SaaS.

No health check calls an external provider. Prompt 18 handles real connectivity testing.

## Metrics

Micrometer is present through the Actuator starter. No Prometheus registry is on the classpath and none was added: there is no operational reason yet, and adding an exporter nobody scrapes is cost without benefit.

| Counter | Tag |
| --- | --- |
| `assessments.created` | `journey` |
| `narrative.failures` | `journey` |
| `evidence.failures` | `journey` |
| `checkout.failures` | `journey` |
| `payment.confirmations` | `journey` |
| `pdf.generation.failures` | `journey` |
| `rate_limit.rejections` | `group` |

The only tag is the journey or the rate-limit group. An assessment id, profession, course title, session or address as a tag would create unbounded time series and would turn a metrics backend into a second, unaudited copy of user data.

Recording is never load-bearing. `JourneyMetrics` swallows its own failures, and every collaborator resolves it optionally and falls back to an instance that records nothing — so a missing or broken registry cannot fail an assessment, a payment, a report or a PDF, and cannot even stop a context from starting.

`checkout.failures` and `pdf.generation.failures` are declared and tested but not yet incremented from the checkout and PDF controllers; wiring them requires threading the collaborator through six sliced controller tests and is the one piece of instrumentation left for the next phase.

The three assessment workflows also increment `narrative.failures` when a provider or validator failure is persisted as narrative status `FAILED`, as well as when narrative lifecycle persistence throws. The metric remains tagged only by journey.

## Datasource

Spring Boot defaults, with hooks so an operator can change them without a rebuild. These are deliberately close to the defaults: they exist to be adjustable, not because a number has been measured as correct.

| Setting | Default | Environment |
| --- | --- | --- |
| `spring.datasource.hikari.maximum-pool-size` | 10 | `DB_POOL_MAX_SIZE` |
| `spring.datasource.hikari.minimum-idle` | 10 | `DB_POOL_MIN_IDLE` |
| `spring.datasource.hikari.connection-timeout` | 30000 ms | `DB_CONNECTION_TIMEOUT_MS` |

Flyway runs on startup, validates what is already applied, and `clean` is disabled: a deployment must never be able to reset a production database. The schema is at **V17** and this phase added no migration. PostgreSQL only — there is no H2 anywhere, including in tests, which use Testcontainers.

## Logging

Production levels are set so the log is readable and says nothing it should not. SQL and Hibernate bind parameters stay at `WARN`: a bind parameter is somebody's job title or course description. Stripe and HTTP client logging is at `WARN` for the same reason. `ERROR` is never suppressed.

Every line carries the request correlation id through `logging.pattern.level`. Startup and background lines, which have no request, read `no-request` rather than being blank.

## Required, optional and development-only configuration

### Application

| Setting | Purpose | Required when | Secret | Example shape |
| --- | --- | --- | --- | --- |
| `app.public-base-url` / `APP_PUBLIC_BASE_URL` | Authoritative public address; builds payment return links | **Required in `prod`** | no | `https://example.com` |
| `app.public-base-url-allow-http` | Deliberate escape hatch when TLS terminates elsewhere | optional | no | `true` |
| `app.<journey>.report.base-url` | Per-journey override of the above | optional | no | `https://example.com` |
| `app.<journey>.report.display-price` | Price shown on the result page | optional; no price is shown if unset | no | `£12.00` |

### Database

| Setting | Purpose | Required when | Secret | Example shape |
| --- | --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection | **always in `prod`** | no | `jdbc:postgresql://host:5432/db` |
| `SPRING_DATASOURCE_USERNAME` | Database user | **always in `prod`** | no | `appuser` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | **always in `prod`** | **yes** | — |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` | Pool tuning | optional | no | `10`, `10`, `30000` |

### Jev / TypeSafe

| Setting | Purpose | Required when | Secret | Example shape |
| --- | --- | --- | --- | --- |
| `TYPESAFE_API_KEY` | Structured judgments | any profile other than `local-fixture` | **yes** | — |
| `spring.ai.typesafe.model` | Model name | optional | no | `jev-latest` |

### OpenAI

| Setting | Purpose | Required when | Secret | Example shape |
| --- | --- | --- | --- | --- |
| `OPENAI_API_KEY` | Narrative generation | any profile other than `local-fixture` | **yes** | — |
| `OPENAI_MODEL` | Chat model | optional | no | `gpt-4o-mini` |

### Stripe

All required when `live-payment` is active, and checked at startup.

| Setting | Purpose | Secret |
| --- | --- | --- |
| `STRIPE_SECRET_KEY` | Hosted checkout API | **yes** |
| `STRIPE_WEBHOOK_SECRET` | Professional webhook signature | **yes** |
| `UNIVERSITY_STRIPE_WEBHOOK_SECRET` | University webhook signature | **yes** |
| `A_LEVEL_STRIPE_WEBHOOK_SECRET` | A-level webhook signature | **yes** |
| `PROFESSIONAL_REPORT_STRIPE_PRICE_ID` | Professional price | no |
| `UNIVERSITY_REPORT_STRIPE_PRICE_ID` | University price | no |
| `A_LEVEL_REPORT_STRIPE_PRICE_ID` | A-level price | no |

Webhook paths: `/payments/stripe/webhook`, `/payments/stripe/university-webhook`, `/payments/stripe/a-level-webhook`. Each journey has its own endpoint and its own signing secret.

### Evidence

| Setting | Purpose | Required when | Secret |
| --- | --- | --- | --- |
| `PROFESSIONAL_EVIDENCE_PROFESSION` | Occupation mapping | `live-evidence` | no |
| `PROFESSIONAL_EVIDENCE_CANONICAL_OCCUPATION` | Occupation mapping | `live-evidence` | no |
| `PROFESSIONAL_EVIDENCE_SOC2020_CODE` | Occupation mapping | `live-evidence` | no |
| `PROFESSIONAL_EVIDENCE_GEOGRAPHY`, `UNIVERSITY_EVIDENCE_GEOGRAPHY`, `A_LEVEL_EVIDENCE_GEOGRAPHY` | Default geography | optional | no |

### Development only

| Setting | Purpose |
| --- | --- |
| `PROFESSIONAL_FIXTURE_ID`, `UNIVERSITY_FIXTURE_ID`, `A_LEVEL_FIXTURE_ID` | Which fixture each journey serves under `local-fixture` |

Rate-limit, CV and PDF settings are listed in their own sections above; all are optional and all have working defaults.

## Running it

### Local development

No secret of any kind is needed:

```
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local-fixture \
  -Dspring-boot.run.arguments="--app.professional.fixture-id=senior_software_architect --app.university.fixture-id=computer_science --app.a-level.fixture-id=maths_economics_geography"
```

Docker must be running; `spring-boot-docker-compose` starts PostgreSQL from `compose.yaml`.

### Production

Not every live capability has to be enabled at once — `prod` on its own is a valid deployment that takes no payments.

```
export SPRING_PROFILES_ACTIVE=prod,live-payment
export APP_PUBLIC_BASE_URL=https://example.com

export SPRING_DATASOURCE_URL=jdbc:postgresql://db.internal:5432/aicareerimpact
export SPRING_DATASOURCE_USERNAME=appuser
export SPRING_DATASOURCE_PASSWORD=...        # from your secret store

export TYPESAFE_API_KEY=...                   # from your secret store
export OPENAI_API_KEY=...                     # from your secret store

export STRIPE_SECRET_KEY=...                  # from your secret store
export STRIPE_WEBHOOK_SECRET=...
export UNIVERSITY_STRIPE_WEBHOOK_SECRET=...
export A_LEVEL_STRIPE_WEBHOOK_SECRET=...
export PROFESSIONAL_REPORT_STRIPE_PRICE_ID=price_...
export UNIVERSITY_REPORT_STRIPE_PRICE_ID=price_...
export A_LEVEL_REPORT_STRIPE_PRICE_ID=price_...

java -jar target/ai-career-impact-0.0.1-SNAPSHOT.jar
```

If anything required is missing, the application names it and refuses to start. The `live-evidence` profile currently supplies only Professional UK Nomis data for an exactly configured occupation; it does not activate University or A-level live evidence. Those report sections must be read as unavailable where no sourced item exists. The profile cannot be combined with `local-fixture`. See [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md) for the tested source path and limits.

## Build-time analysis

`mvn dependency:tree` shows no artifact resolved at two versions. PDFBox is 3.0.8, POI 5.5.1 across all three of its artifacts, Stripe 33.4.2, Spring AI 2.0.1 and TypeSafe 0.2.0 all aligned with their BOMs. One observation, not a conflict: `xmpbox` arrives at 3.0.7 through OpenHTMLToPDF while the rest of PDFBox is 3.0.8. Different artifacts, no duplication, and pinning it would mean adding a dependency to solve a cosmetic skew, so it was left alone and recorded here.

No static-analysis or dependency-scanning plugin is configured — no SpotBugs, Checkstyle, PMD, JaCoCo, Enforcer or OWASP Dependency-Check. None was added: each is a standing build cost, and adding several at the end of a hardening phase would produce a backlog of findings nobody has budget to triage. Choosing one deliberately is a better next step than inheriting four.

## Reserved for live integration

The remaining external-provider work is:

- a real Stripe checkout, a real signed webhook, and the full purchase-to-entitlement path against Stripe's test mode;
- broader official occupation mapping and additional Professional evidence categories beyond the verified UK Nomis employment snapshot;
- University and A-level live evidence adapters only after exact course/provider identifiers and defensible source ingestion are available;
- wiring `checkout.failures` and `pdf.generation.failures` into their controllers;
- deciding whether a Prometheus registry is wanted, once there is somewhere to scrape it from.

Real Jev and OpenAI verification is recorded in [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md). Prompt 18B's independent Nomis request and saved-report flow are recorded in [LIVE_EVIDENCE_VERIFICATION.md](LIVE_EVIDENCE_VERIFICATION.md). Normal `mvn clean verify` runs neither live suite.
