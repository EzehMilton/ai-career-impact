# Railway public beta deployment

This deployment uses the ordinary `prod` profile. Stripe and Professional live evidence are independent capabilities;
both are deliberately disabled for the first public beta. Detailed-report access uses the existing complimentary
entitlement and therefore exercises the same stored report and PDF path as a paid entitlement.

## Railway service

Deploy the application service and a Railway PostgreSQL service in the same project. Configure the application health
check as:

```text
/actuator/health/readiness
```

Railway injects `PORT`; `application-prod.properties` binds `server.port` to `${PORT:8080}`. Do not set a fixed port.
If a deployment template insists on an explicit Spring variable, map it to Railway's injected value as
`SERVER_PORT=<Railway PORT mapping>`. The application keeps `server.forward-headers-strategy=framework`, secure session
cookies and HTTPS public URLs.

Spring Boot's local Docker Compose support must be disabled on Railway. It remains available to local development.

## Required beta variables

Use Railway variable references for the PostgreSQL service where available. The JDBC URL must begin with
`jdbc:postgresql://`; Railway's generic `DATABASE_URL` is not a direct replacement if it uses a `postgresql://` URI.

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DOCKER_COMPOSE_ENABLED=false
# PORT is injected by Railway; the application reads it automatically.

SPRING_DATASOURCE_URL=jdbc:postgresql://<Railway PostgreSQL host>:<port>/<database>
SPRING_DATASOURCE_USERNAME=<Railway PostgreSQL user>
SPRING_DATASOURCE_PASSWORD=<Railway PostgreSQL password>

APP_PUBLIC_BASE_URL=https://<final Railway or custom domain>

OPENAI_API_KEY=<secret>
JUDGMENT_PROVIDER=openai

STRIPE_ENABLED=false

COMPLIMENTARY_REPORT_ENABLED=true
COMPLIMENTARY_REPORT_CODE=<secret beta access code>

PROFESSIONAL_LIVE_EVIDENCE_ENABLED=false

ASSESSMENT_TRACE_ENABLED=false
RATE_LIMIT_ENABLED=true

A_LEVEL_V3_PRESENTATION_CUTOVER=<permanent UTC instant set after cutover>
```

Do not configure `TYPESAFE_API_KEY`, Stripe keys, webhook secrets, Price IDs, or Professional occupation mapping values
for this beta. They are not required while their capabilities are disabled and OpenAI is selected. Never store real
credentials in source control or documentation.

## A-Level v3 release boundary

Initially leave `A_LEVEL_V3_PRESENTATION_CUTOVER` at the application's safe far-future default, or explicitly use that
same safe value. After the new Railway deployment is healthy everywhere and the old version has stopped accepting new
submissions, choose a UTC instant after that point and set it permanently. Assessments created before it retain v2 free
report presentation; assessments created at or after it use v3. Once activated, do not move the timestamp between
deployments.

## Expected beta behaviour

- OpenAI Decisions is the selected atomic-judgment provider; Java policies remain authoritative.
- OpenAI supplies narratives. There is no automatic fallback to Jev.
- Checkout buttons and Stripe webhook controllers are absent.
- A beta access-code form unlocks the normal detailed report and PDF through a `COMPLIMENTARY` entitlement.
- Professional occupation classification and Nomis enrichment are skipped. No fixture evidence is used in `prod`.
- Flyway validates and migrates the Railway PostgreSQL database; production clean remains disabled.
- Assessment trace logging stays off and rate limiting stays on.

Before enabling payments or broader Professional evidence, follow
[COMMERCIAL_LAUNCH_CHECKLIST.md](COMMERCIAL_LAUNCH_CHECKLIST.md).
