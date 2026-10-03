# Release candidate engineering review — 2026-10-03

## Scope and evidence

This review covers the Professional, University and A-level journeys in the Java 21 modular monolith. The baseline `mvn clean verify` passed: 572 tests, zero failures, zero errors, zero skipped. After hardening, `mvn clean verify` passed: 582 tests, zero failures, zero errors, zero skipped. PostgreSQL ran through Testcontainers and Flyway applied schema V17. Normal verification used fixtures or mocks for external providers; no live Stripe, Jev, OpenAI or Nomis calls were made for this review.

The review is based on source inspection, existing automated tests, the baseline build, and previously recorded operator observations. Previously recorded manual evidence must not be confused with a live retest during this review.

## Architecture and journeys

Each journey has its own web forms, application workflow, typed Jev judgment schema, deterministic Java policy, narrative adapter, evidence records, assessment tables, payment records, purchased snapshot and PDF artifact. Controllers delegate decisions to application services. Jev supplies structured judgments; Java owns scores, bands and thresholds. OpenAI generates narrative from the saved decision and cannot change a measure. Factual claims belong to saved evidence with source provenance. Assessment GETs and paid-report GETs read saved state; they do not rerun providers or recalculate historical policy.

Professional takes the form and optional CV extraction into the assessment workflow. It saves judgment and policy results, then narrative, occupation classification and evidence lifecycle records. The free report reads the saved state. Checkout creates a PENDING entitlement; only a verified Stripe event can freeze a PAID report snapshot and activate the entitlement. The paid report and PDF require ACTIVE status. CV extraction is a known quality defect and is outside this review.

University and A-level use the equivalent journey-specific sequence. Their judgment schemas, policies, tables and purchase references are distinct. A-level copy and narrative validation avoid admission guarantees, invented prerequisites and instructions to drop or change a subject. The A-level report tells students to check published course requirements. There is no live University or A-level evidence adapter; unavailable sourced context stays unavailable rather than becoming a fixture in non-fixture profiles.

The same lifecycle shape is repeated in three business slices. That duplication makes payment validation drift possible: before this review the student Stripe adapters lacked the Professional checks. This review aligns those checks without merging the journeys or changing their domain policies. A future maintenance pass could centralise low-level Stripe validation behind journey-specific parameters, but that is not needed for this release candidate.

## Profile matrix and startup configuration

| Profiles | Providers and payment | Startup rule |
| --- | --- | --- |
| `local-fixture` | Fixture Jev, narrative, evidence and simulated checkout | Works without external credentials; cannot combine with `prod`, `live-payment` or `live-evidence`. |
| `prod` | Real Jev/OpenAI adapters; no fixture adapters | Requires OpenAI and TypeSafe/Jev configuration plus a valid public base URL. HTTPS is required unless HTTP behind TLS termination is explicitly allowed. |
| `live-payment` | Three real Stripe checkout adapters and signed webhooks | Requires Stripe API configuration, three explicit webhook secrets and three journey Price IDs. Cannot combine with `local-fixture`. |
| `live-evidence` | Professional Nomis adapter for one explicitly mapped occupation | Requires classified profession, canonical occupation and SOC 2020 code; cannot combine with `local-fixture`. No student live evidence is enabled. |

`StartupConfigurationValidator` reports missing property names and incompatible profiles without printing values. Spring Boot validates the datasource. The student webhook properties now have no fallback to the Professional secret. No credential-like file is tracked under the checked filename patterns, and the main source/docs scan found no embedded Stripe key or webhook-secret strings. This review did not inspect environment-variable values. Configuration remains an operator responsibility; production shape is tested with fake provider credentials and mocks.

## External integrations

- TypeSafe/Jev: separate typed adapters and schemas per journey; no normal test makes a live call.
- OpenAI: separate narrative adapters; failures are recorded without changing saved Java decisions.
- Nomis: Professional-only, exact configured occupation-to-SOC mapping, fixed endpoint, bounded HTTP timeouts and response validation. A provider failure cannot silently substitute a fixture.
- Stripe: hosted checkout and signed webhooks, active only in `live-payment`. Stripe API lookup failures during final session validation return a retryable server error.
- PostgreSQL/Flyway: sole persistence engine; no H2 path. Schema version V17.
- OpenHTMLToPDF/PDFBox: render solely from frozen purchased snapshots and persist a bounded PDF artifact with SHA-256.

## Security and payment model

Browser redirects indicate only that a browser returned from checkout. They never activate an entitlement. The three webhook routes are `/payments/stripe/webhook`, `/payments/stripe/university-webhook` and `/payments/stripe/a-level-webhook`. Browser POSTs retain CSRF protection; only those exact Stripe paths are exempt and require a valid signature. Invalid or malformed signed checkout data returns 400. Temporary Stripe lookup or processing failures remain retryable as 500.

The signed `checkout.session.completed` payload is checked for object type, `paid` status, `payment` mode, assessment and entitlement metadata, checkout reference, positive amount and currency. The session is then retrieved from Stripe and checked against the signed event; its single line item must use that journey's configured Price ID, quantity one, and matching positive amount/currency. University and A-level also require their product metadata. Each purchase service checks the provider, saved session, assessment, entitlement and PENDING state. Distinct routes, explicit secrets, Price IDs, metadata and database tables isolate the journeys. Replays are idempotent under transactional entitlement locking and processed-event records. The purchase snapshot and ACTIVE status are written together or rolled back together.

No user accounts or owner authorization exist yet. Access to saved assessments, paid reports and PDFs depends on possession of an opaque assessment UUID plus ACTIVE entitlement for paid resources. This is a real privacy and authorization limitation for a public production launch; the existing entitlement check alone does not establish who owns the purchase.

The HTTP layer keeps CSRF, content security policy, frame denial, referrer and permissions policies, safe no-store responses, scoped CSS caching, no generated login page and ordinary 404s for unknown routes. Correlation IDs are bounded and not taken as arbitrary log tags. Forms and parsed lists have limits. Uploads are PDF/DOCX only, with file/request limits, PDF page and extracted-text caps, and POI ZIP inflation/entry/text guards. Nomis uses a fixed URL, rendered evidence links allow safe schemes, and the PDF renderer resolves no remote resources. Thymeleaf templates contain no `th:utext`. Assessment, CV and checkout routes have rate limits; webhook retries and read-only routes are not rate limited.

## Persistence and PDF invariants

Assessment, judgment, narrative and evidence provenance are saved separately with version identifiers. Older assessments are not recalculated by GET. A purchased snapshot is frozen once from the saved report state. Paid-report services read only ACTIVE entitlements and the matching snapshot; PDF services do the same and do not invoke Jev, OpenAI, evidence or Stripe. A generated PDF is stored once by entitlement with version, byte count, SHA-256 and bytes; subsequent access returns the existing artifact. Renderer failure happens before artifact persistence and cannot activate or rewrite a purchase.

The offline tests cover entitlement gating, snapshot freezing, replay/idempotency, renderer failure and artifact stability. They establish code behavior under the tested conditions, not live Java 21 rendering on the development Mac.

## Observability and metrics

Assessment creation, narrative/evidence failure, checkout failure, webhook rejection, payment confirmation and PDF supply failure have safe logs or counters. Stripe logs use event/session/assessment/entitlement IDs, status, outcome and bounded reason codes; they do not print payloads, card data or secrets. Provider lifecycle records retain status and provenance. Unexpected errors are logged internally while controllers return safe messages. No CV text or report body is intentionally logged.

`JourneyMetrics` uses only journey or rate-limit group tags; it does not use UUIDs, professions or user text. Assessment creation, narrative/evidence failures, payment confirmation and rate-limit rejection are wired. `checkout.failures` and `pdf.generation.failures` are declared but remain unwired; controller error logs provide diagnosis. Wiring those counters through the six sliced controllers is deferred rather than widening this payment hardening change.

## Runtime, testing and prior manual evidence

The POM targets Java 21; this baseline ran on Java 21. No Java 25 requirement or CI workflow was found in the repository. A clean build is the local release gate. The normal suite uses PostgreSQL/Testcontainers and synthetic or mocked external calls. Live evaluation suites are opt-in and were not run here.

Previously recorded manual checks include Professional live Jev, live OpenAI narrative, Professional Nomis evidence, Stripe TEST-mode checkout, signed `checkout.session.completed` returning 200, entitlement activation and paid-report access. Local fixture manual testing covered all three free-to-paid-to-PDF flows, cross-journey entitlement isolation and restart stability. Those observations do not prove that the new University/A-level Stripe adapter changes work against live Stripe, or that Professional PDF rendering works on this Mac under Java 21.

## Known defects and release blockers

1. **CV extraction quality:** a real Professional CV placed a technical responsibility in Job title and extracted other fields poorly. No extraction change was made here.
2. **Checkout availability after runtime switch:** after IntelliJ changed from Java 25 to Java 21, a later Professional assessment displayed “Paid report checkout is not currently available.” The earlier Stripe TEST checkout and activation succeeded. The later configuration/integration cause is unknown and must be investigated, without a payment fallback.
3. **PDF runtime:** Java 25 on the development Mac failed when OpenHTMLToPDF/AWT tried to load an unavailable macOS native framework. Java 21 is the intended runtime. A real Professional paid PDF download and reopen under Java 21 is still required; no OS-specific workaround is claimed.
4. **Authorization:** opaque assessment links plus ACTIVE entitlement are insufficient to establish a purchaser's identity for a genuine public production release. Owner authorization and privacy policy decisions remain to be designed.
5. **Live coverage:** University and A-level real Stripe TEST-mode purchase, webhook and PDF paths have not been manually proven. Their offline validation is stronger after this review but is not a substitute for operator-run live tests. Live student evidence is unavailable by design.
6. **Operational gate:** no repository CI workflow was found. A repeatable Java 21 build and deployment gate, together with production monitoring and runbooks, must be established before release.

## Deferred work and release assessment

UI and visual refinement from the landing page through generated PDFs belongs to the next product-polish phase. Broader verified occupation mapping, student evidence ingestion, CV extraction improvement and metric wiring remain separate work. Do not substitute fixtures for failed live providers or a success redirect for payment confirmation.

The code has a passing offline baseline, separated journey policies, saved provenance, strict signed payment processing, frozen purchases and guarded PDFs. A genuine production release remains blocked by the checkout-unavailable observation, Java 21 live PDF re-verification, missing owner authorization, and operator validation of the student live-payment paths. This is a release candidate for controlled further testing, not a claim of production readiness.
