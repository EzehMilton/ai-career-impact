# OpenAI Decisions migration — Phase 1 verification

Date: 12 November 2026

Branch: `feat/change-decision-model-jev-to-openai`
Starting commit: `c83f110`

## Scope

This phase proves Spring AI and OpenAI Decisions integration only. Jev remains the production judgment provider for
all five capabilities. No scoring policy, threshold, report, narrative prompt, persistence format or provider source
was changed.

## Baseline

The untouched project used Spring Boot 4.0.8, Java 21 and Spring AI 2.0.1.

The initial `./mvnw clean verify` compiled 753 main and 297 test source files, then reported 1,560 tests, 149 errors
and one skipped test. The common cause was environmental: Docker Desktop was not running, so PostgreSQL
Testcontainers could not find a Docker environment. This was recorded before dependency or source changes.

After Docker Desktop was started, the post-change verification command completed successfully:

```text
./mvnw clean verify
Tests run: 1567, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The seven added offline tests account for the difference in test count.

## Compatibility findings

- Spring Boot remains at 4.0.8; no Boot upgrade was required.
- Spring AI 2.1.0-RC2 compiles and runs on this application with Java 21.
- TypeSafe 0.2.0 remains present and its existing tests pass with the Spring AI 2.1.0-RC2 dependency graph.
- `spring-ai-starter-model-openai` 2.1.0-RC2 supplies `OpenAiDecisionModel` and Decision Model observation support.
- RC2 enables the OpenAI decision auto-configuration by default. Phase 1 therefore sets
  `spring.ai.model.decision=none` in normal application and test configuration, and enables it only in the
  `openai-decision-poc` profile.
- The POC profile reads the existing `OPENAI_API_KEY` environment variable and pins the documented
  `gpt-6-luna` decision model.
- The repository's `src/test/resources/application.properties` replaces the main file during tests. It must repeat
  `spring.ai.model.decision=none`; without it, ordinary test contexts try to construct a credentialed decision client.

## Proof of concept

The isolated POC sends one fields-based decision request containing three existing questions:

1. Professional `information_sufficient`, translated from Jev `Noul` to `YesNoQuestion`.
2. Professional `task_repeatability`, translated from Jev `Score` to `ScoreQuestion`.
3. Responsibility `r1__outcome_accountability`, representing a dynamic complex capability.

Question IDs, instructions, yes/no criteria and all five score-level descriptions are copied from the existing Jev
schemas. Score level labels are the existing zero-based indices (`0` through `4`). The result keeps OpenAI's
probability-of-yes, thresholded boolean, expected score, nearest level, confidence and probability distribution as
distinct values. It does not map them into a production judgment or claim equivalence with Jev.

The POC rejects missing, unexpected, refused, wrong-type and malformed results. It never supplies a fixture or
default value after a provider failure.

## Offline verification

The added tests verify:

- Spring Boot 4.0.8 / Spring AI 2.1.0-RC2 decision auto-configuration and opt-out behavior.
- Exact preservation of existing question text, criteria, IDs and score levels.
- Separate mapping of OpenAI's predicate probability, expected score, nearest level and confidence.
- Missing, unexpected, refused and malformed response rejection.
- The real Spring AI/OpenAI Java client request against a loopback HTTP stub: `POST /v1/decisions`, model
  `gpt-6-luna`, three correctly typed questions, response mapping and `x-request-id` propagation.

The loopback contract test constructs and calls `OpenAiDecisionModel` without a TypeSafe client or Jev service,
confirming that OpenAI Decisions has no runtime dependency on those services.

## Live probe

The live test is excluded from ordinary Surefire and `verify` runs. Run exactly one real Decisions request with:

```text
OPENAI_API_KEY=... ./mvnw verify -Plive-openai-decision-poc
```

It also requires the Maven profile's explicit `openai.decision.poc.live.enabled=true` switch, which the profile sets.
The opt-in live test subsequently passed: one test, zero failures/errors/skips, in 4.146 seconds. It constructed the
real `OpenAiDecisionModel` with `OPENAI_API_KEY`, the default OpenAI endpoint and `gpt-6-luna`; no stub or mock was
injected. The response carried provider request ID `req_172ebd7b787e469ab6cb8ddafaa79d0f` and all three answers
passed strict validation:

- `information_sufficient`: probability of yes `0.96`.
- `task_repeatability`: expected level `1.68`, nearest level `2`, confidence `0.72`.
- `r1__outcome_accountability`: expected level `4.0`, nearest level `4`, confidence `1.0`.

## Limitations discovered

- Current official OpenAI documentation supports only `gpt-6-luna` for the Decisions endpoint.
- A predicate produces probability-of-yes; its boolean uses a `0.5` default threshold. A score separately exposes a
  probability-weighted expected level, nearest level, probabilities and confidence. None should be treated as a Jev
  equivalent without calibration.
- OpenAI can refuse individual questions, so every future adapter must reject partial response sets explicitly.
- The Phase 1 question translator intentionally depends on Jev schema types to prove exact preservation. Phase 2
  should first extract provider-neutral question definitions rather than copy this transitional coupling into five
  production adapters.
- The live probe verifies connectivity and response shape, not production calibration or outcome equivalence.
