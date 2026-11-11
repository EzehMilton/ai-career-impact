# Decision model providers

This application separates probabilistic judgments from deterministic decisions:

```text
selected judgment provider
        |
        v
atomic judgments
        |
        v
Java decision policies
        |
        v
authoritative scores, bands and classifications
        |
        v
narrative generation
```

The provider answers the versioned assessment questions. It does not own weights, thresholds, bands, final
classifications, persistence rules or report rendering. Those remain in Java regardless of which provider is
selected.

## Selecting OpenAI or Jev

The provider is selected application-wide with `app.judgment.provider`. The base configuration exposes it as the
`JUDGMENT_PROVIDER` environment variable and defaults to OpenAI:

```properties
app.judgment.provider=${JUDGMENT_PROVIDER:openai}
```

To use OpenAI Decisions:

```bash
JUDGMENT_PROVIDER=openai
OPENAI_API_KEY=your-openai-key
```

To select Jev deliberately:

```bash
JUDGMENT_PROVIDER=jev
TYPESAFE_API_KEY=your-typesafe-key
```

Restart the application after changing the setting. For example, during local live-smoke testing:

```bash
APP_LIVE_SMOKE_ENABLED=true \
JUDGMENT_PROVIDER=jev \
TYPESAFE_API_KEY=your-typesafe-key \
OPENAI_API_KEY=your-openai-key \
SPRING_PROFILES_ACTIVE=live-smoke \
./mvnw spring-boot:run
```

`OPENAI_API_KEY` can still be required when Jev supplies judgments because report narratives use OpenAI separately.
`JUDGMENT_PROVIDER` does not select the narrative provider or model.

Provider selection is explicit. There is no automatic failover from OpenAI to Jev, from Jev to OpenAI, or to local
fixtures. A failure from the selected provider remains visible. An operator must change the configuration and
restart the application to use a different provider.

The current values are:

| Value | Role | Required judgment credential |
| --- | --- | --- |
| `openai` | Default and primary provider | `OPENAI_API_KEY` |
| `jev` | Explicit contingency provider | `TYPESAFE_API_KEY` |

The provider auto-configurations are active for the real-provider profiles (`prod` and `live-smoke`). Development
fixture profiles have their own deliberate wiring and are not provider-failure fallbacks.

## How provider selection is wired

Each assessment capability exposes a provider-independent application port:

- `ProfessionalJudgmentProvider`
- `UniversityJudgmentProvider`
- `ALevelJudgmentProvider`
- `ProfessionalResponsibilityJudgmentProvider`
- `UniversityModuleJudgmentProvider`

OpenAI and Jev adapters implement those ports. Their Spring auto-configurations use mutually exclusive conditions,
for example:

```java
@ConditionalOnProperty(
        name = "app.judgment.provider",
        havingValue = "openai",
        matchIfMissing = true)
```

or:

```java
@ConditionalOnProperty(
        name = "app.judgment.provider",
        havingValue = "jev")
```

The application services depend only on the ports. Consequently, changing the property changes the adapter without
changing the decision policies. Provider provenance is stored on results, so old Jev assessments remain Jev
assessments after the default changes.

## Adding another provider

If a provider such as Anthropic later offers an appropriate decision-model API, add it as another adapter. Do not
add provider types to the application services or deterministic decision policies.

### 1. Confirm the provider contract

Before implementation, verify that the API can represent every canonical question without changing its meaning:

- yes/no (`Noul`) judgments and their probability semantics;
- ordered score levels and the exact level descriptions;
- stable question identifiers;
- explicit refusals and provider errors;
- usage, model and request metadata needed for observability and cost reporting.

Do not assume its probabilities, confidence, expected scores or nearest levels mean the same thing as OpenAI or Jev.
Document the provider's semantics and map only values with a defensible interpretation.

### 2. Keep the canonical schemas unchanged

Reuse the existing schema version, question IDs, wording, instructions and ordered score definitions. If provider
limitations force a semantic change, create and document a new schema version instead of silently changing the old
one.

Provider-specific request objects belong inside the new adapter. They must not leak into application ports, domain
judgment records or Java decision policies.

### 3. Implement all five application ports

Create provider adapters in provider-specific packages, for example:

```text
judgment/anthropic/professional/
judgment/anthropic/university/
judgment/anthropic/alevel/
responsibility/professional/anthropic/
coursemodule/university/anthropic/
```

Each adapter should:

1. Map the existing assessment state to the provider request.
2. Send the complete atomic-judgment request.
3. Validate the response before domain mapping.
4. Return the existing provider-independent result contract.
5. Report its provider source/provenance.
6. Record provider observations, latency, model metadata, token usage and estimated cost where available.
7. Propagate provider failure; it must not call another provider or manufacture default judgments.

If Spring AI supplies a compatible `DecisionModel`, use that abstraction and its observation support. If it does
not, encapsulate the provider's official client in the adapter and still return the existing domain contracts. The
application layer must not depend on the provider SDK.

### 4. Validate responses strictly

Add reusable provider-specific validation that rejects:

- a null or missing response;
- missing answers;
- unexpected answer IDs;
- duplicate answers where the API permits them;
- the wrong answer type;
- malformed, non-finite or out-of-range values;
- an invalid probability distribution;
- invalid score levels;
- refusals and safety blocks;
- missing model/provenance metadata required by the application.

Never silently substitute fixture values, zeroes, neutral scores or answers from another provider.

### 5. Add explicit Spring configuration

Add a new, unambiguous value such as:

```properties
app.judgment.provider=anthropic
```

Create one provider auto-configuration per capability, guarded with:

```java
@ConditionalOnProperty(
        name = "app.judgment.provider",
        havingValue = "anthropic")
```

Also guard on the required client bean and relevant class, and retain the same real-provider profile restrictions.
Register each auto-configuration in:

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Add provider credentials and model settings through environment-backed properties. Do not hard-code credentials,
and fail startup with a clear message when the selected provider lacks required configuration. Update
`StartupConfigurationValidator` and its profile-matrix tests accordingly.

Do not use `matchIfMissing = true` for the new provider: OpenAI remains the single declared default. Ensure exactly
one adapter exists for each port under every supported profile/provider combination.

### 6. Preserve provenance

Add the new source value (for example, `ANTHROPIC`) to every relevant source enum and map it through existing result
and persistence contracts. Treat stored enum values and report JSON as compatibility-sensitive. Add a database
migration only if the actual storage schema requires one; never rewrite historical OpenAI or Jev records.

### 7. Add tests before activation

Normal tests must never call a real provider. Add:

- adapter unit tests using a scripted or fake provider client;
- contract tests for every question and answer type;
- malformed, missing, extra, refused and failed-response tests;
- Spring context tests proving `anthropic` selects only Anthropic adapters;
- context tests proving `openai` and `jev` still select only their own adapters;
- startup validation tests for missing credentials and unsupported provider values;
- provenance and persistence compatibility tests;
- deterministic policy regression tests proving the same mapped judgments yield the same Java outcome;
- an explicitly opt-in live integration test.

Run the full offline suite:

```bash
./mvnw clean verify
```

### 8. Calibrate before making it the default

Evaluate the provider on clear low, moderate and high cases, classification boundaries, missing information,
ambiguity, contradictory evidence, repeated identical input and semantically equivalent wording. Review atomic
judgments and final Java classifications, plus refusal rate, repeatability, latency and cost.

The acceptance criterion is a stable and defensible customer outcome under the existing Java policies. Matching
OpenAI or Jev probabilities is not an acceptance criterion.

Only after this evaluation should the default in `application.properties` be changed. Changing the default is a
separate, deliberate release decision; adding an adapter must not change production provider selection by itself.

## Provider implementation checklist

- Existing application ports and domain response contracts are unchanged.
- Java still owns scoring, weights, thresholds, bands and classifications.
- Question IDs, semantics and schema versions are preserved or deliberately versioned.
- Responses and refusals fail strict validation.
- No automatic provider failover exists.
- Source provenance is persisted and historical records remain unchanged.
- Configuration selects exactly one judgment adapter per capability.
- Offline tests make no real provider calls.
- The live test is opt-in.
- Calibration covers boundary and difficult-information cases.
- Observability, latency and cost are recorded.
- `./mvnw clean verify` passes.
