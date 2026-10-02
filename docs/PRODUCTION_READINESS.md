# Production readiness

What the application currently bounds, throttles and refuses, and what is still missing. This covers the hardening done in phases 17A and 17B. Operational concerns — a production profile, external configuration validation, a single public base URL, cookie and proxy settings, Actuator exposure, health groups and metrics — are not done yet and are listed at the end.

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

Unchanged from before: PDF and DOCX only, extension and MIME and magic-signature checks, filename sanitisation, no OCR, and raw CV bytes are never persisted.

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

## Still outstanding

Reserved for the operational phase:

- a production profile;
- fail-fast validation of required external configuration, including Stripe keys, webhook secrets and price ids;
- a single public base URL instead of three per-journey settings that silently default to `http://localhost:8080`;
- forwarded-header handling and a trusted proxy, which would also let rate limiting key on the real client address;
- secure, HttpOnly and SameSite cookie settings;
- Actuator exposure and health groups, with liveness that does not depend on an AI provider;
- metrics;
- datasource and connection-pool tuning;
- a dependency and static-analysis review.
