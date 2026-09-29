# Professional Jev live evaluation

This harness runs fictional professional profiles through the real `professional-v1` Jev judgment schema and the deterministic `professional-decision-v1` Java policy. It exists to help reviewers inspect atomic judgments, confidence, decision measures, and whether detailed responsibilities affect results beyond a job title.

The harness is not a scientific validation study. The cases do not have independently validated ground-truth labels, so the report uses descriptive statistics and warnings rather than an accuracy percentage. Exact model outputs can vary and should not become brittle unit-test assertions.

## Safety and cost

Normal verification remains offline for external AI providers:

```shell
./mvnw clean verify
```

Live evaluation requires both an explicitly selected Maven profile and a non-empty `TYPESAFE_API_KEY` environment variable. Merely exporting the key does not enable live tests. A live run incurs TypeSafe/Jev API usage; each selected case makes one System One request containing all nine professional-v1 questions. The harness does not call OpenAI and does not add retries around the client.

## Run one case

Use one case while developing to keep cost and review scope small:

```shell
./mvnw clean verify \
  -Plive-jev-evaluation \
  -Djev.eval.case=routine_software_developer
```

An unknown case ID fails clearly before an API call is made.

## Run all cases

```shell
./mvnw clean verify -Plive-jev-evaluation
```

Cases run sequentially. The full dataset currently makes 16 System One requests.

## Report

The generated report is written to:

```text
target/jev-evaluation/professional-v1-report.md
```

It includes each case's atomic values, nearest Score labels, confidences and probability distributions; the four professional decision measures; overall status and confidence; structured exposure/protective factors; same-domain comparisons; informational warnings; and descriptive summary statistics.

Hard failures are reserved for technical or structural problems such as API exceptions, missing answers, invalid ranges, malformed distributions, or a wrong journey/schema. Unexpected professional results remain review findings in the report. They do not automatically fail the build or alter the schema and policy.
