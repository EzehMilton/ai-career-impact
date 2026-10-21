# Professional Jev live evaluation

This harness runs fictional professional profiles through the real `professional-v1` Jev judgment schema and the deterministic `professional-decision-v1` Java policy. It exists to help reviewers inspect atomic judgments, confidence, decision measures, and whether detailed responsibilities affect results beyond a job title.

The harness is not a scientific validation study. The cases do not have independently validated ground-truth labels, so the report uses descriptive statistics and warnings rather than an accuracy percentage. Exact model outputs can vary and should not become brittle unit-test assertions.

## Development workflows and safety

### Normal full offline verification

Run after changes. This runs the normal unit and integration suite, including database/Testcontainers tests, without real Jev or OpenAI calls:

```shell
./mvnw clean verify
```

Live evaluation requires both the explicit `live-jev-evaluation` Maven profile and a non-empty `TYPESAFE_API_KEY` environment variable. The live test also checks a system property supplied only by that profile. Merely exporting the key does not enable live tests. A live run incurs TypeSafe/Jev API usage; each selected case makes one System One request containing all nine professional-v1 questions. The harness does not call OpenAI and does not add retries around the client.

### Fast live Jev case

Use this command to inspect one case quickly while developing:

```shell
./mvnw \
  -Plive-jev-evaluation \
  -Djev.eval.case=senior_software_architect \
  test-compile failsafe:integration-test failsafe:verify
```

`test-compile` compiles application and test code. The two Failsafe goals run and check only `ProfessionalJevLiveEvaluationIT`. This command never reaches Maven's `test` phase, so Surefire and unrelated PostgreSQL/Testcontainers tests do not run. It writes `target/jev-evaluation/professional-v1-report.md`. An unknown case ID fails before an API call is made. Omit `-Djev.eval.case` only when deliberately evaluating the full dataset.

### Full live evaluation

Use the full lifecycle for deliberate broader validation. With a case selector it runs one live case alongside the normal verification suite; without one it evaluates all 16 cases sequentially:

```shell
./mvnw clean verify -Plive-jev-evaluation
```

The full dataset currently makes 16 System One requests. The normal suite may start PostgreSQL/Testcontainers.

### Capturing what the running application sends to Jev

The harness above uses fictional cases. To see what a real assessment in the running application
sent to Jev and what came back, start the application with the
development-only `jev-capture` profile added to the profiles you already use:

```shell
SPRING_PROFILES_ACTIVE=jev-capture TYPESAFE_API_KEY=<key> OPENAI_API_KEY=<key> ./mvnw spring-boot:run
```

`jev-capture` wraps whichever professional judgment provider is active. After each successful
judgment it writes one pretty-printed JSON file to `target/jev-capture/`, named
`{yyyyMMdd'T'HHmmss.SSS'Z'}-{8 hex characters}.json`. Set
`app.professional.jev-capture.directory` to write elsewhere; `./mvnw clean` deletes the default
directory. Each file contains:

- `source`, first: `JEV` for real Jev answers or `LOCAL_FIXTURE` for fixture answers;
- `capturedAt`, `schemaVersion` and `modelIdentifier` (the configured model alias, or `null`);
- `state`: exactly the map `ProfessionalJevStateMapper` produces, which is the state Jev is given;
- `questions`: for each question id, its `type`, `instructions` and either the five score `levels`
  (lowest first) or, for the `information_sufficient` Noul, its `whenTrue` and `whenFalse` wording,
  all taken from `ProfessionalJudgmentSchema`;
- `answers`: for each dimension, `value`, `nearestLabel`, `confidence` and `probabilities`.

The files are what the application gave the TypeSafe client and what it kept from the answer, not
the raw HTTP exchange. The client also adds the configured model name (`spring.ai.typesafe.model`)
to the request body, and the bearer key and JSON content headers to the request.

Capture never changes a result. A failed judgment writes nothing; a failed write is ignored apart
from one WARN log line naming only the exception type, never the path or content. With `local-fixture` the fixture provider is
wrapped, so the files show the state and questions Jev would have been given alongside the fixture's
answers.

The files contain the assessment input, which can include personal details. Treat them as
personal data: keep them local, do not commit them (`target/` is ignored), and delete them when done.
The startup validator refuses to run `jev-capture` together with `prod`.

## What the first live comparison showed

The routine software developer and senior software architect returned substantially different judgments despite related job titles. The recorded AI Exposure indicators were 9.471 and 4.810 respectively. The architect's higher contextual complexity, original judgment, human trust and accountability help explain the difference. These observations show expected sensitivity to described responsibilities; two fictional cases do not establish scientific or predictive accuracy.

The original explainability code could put the same dimension in both exposure drivers and protective factors because it ranked its exposure-oriented value and its complement separately. That made the labels confusing even though the numeric AI Exposure formula was sound. Each exposure dimension now has one signed explanatory effect: `policyWeight × (exposureComponent − 0.5)`, using the same exposure-oriented normalised component as the numeric formula. Positive effects are drivers, negative effects are protective, and zero is neutral. Each list contains at most three factors, ranked by magnitude on its side. Structured directions such as `LOW_CONTEXTUAL_COMPLEXITY` and `HIGH_CONTEXTUAL_COMPLEXITY` preserve what the underlying judgment means. Signed effects select and describe factors only; they do not change AI Exposure, other measures, weights, thresholds or bands.

## Report

The generated report is written to:

```text
target/jev-evaluation/professional-v1-report.md
```

It includes each case's atomic values, nearest Score labels, confidences and probability distributions; the four professional decision measures; overall status and confidence; structured exposure/protective factors; same-domain comparisons; informational warnings; and descriptive summary statistics.

Hard failures are reserved for technical or structural problems such as API exceptions, missing answers, invalid ranges, malformed distributions, or a wrong journey/schema. Unexpected professional results remain review findings in the report. They do not automatically fail the build or alter the schema and policy.
