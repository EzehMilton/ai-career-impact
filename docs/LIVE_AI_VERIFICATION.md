# Prompt 18A live AI verification

## Status on this development machine

The clean baseline completed with 549 tests, zero failures, zero errors and zero skipped. It used PostgreSQL via Testcontainers and Flyway V17. No live AI call was made by the normal build.

After the focused configuration and metric changes, the final `mvn clean verify` completed with 553 tests, zero failures, zero errors and zero skipped. An intermediate run hit an intermittent one-byte PDF length assertion; that existing test passed in isolation and in the final clean run. No PDF code or test was changed.

Two later Professional Jev runs succeeded on this machine. The senior software architect returned AI Exposure 4.790, Human Advantage 6.667, AI Complementarity 7.975 and Role Adaptability 8.374 in about 5.5 seconds. The routine software developer returned 9.446, 0.577, 7.525 and 3.029 respectively; its Failsafe test took 62.43 seconds. Both were READY_WITH_CAUTION. The latter report is saved under `target/jev-evaluation/routine-software-developer-report.md`; the contrast is semantically consistent with the previously documented evaluation, without any policy change.

The subsequent student Jev runs also succeeded, one selected case per Failsafe run. `computer_science` used `university-v1` and `university-decision-v1`: Future Readiness 8.538, AI Complementarity 9.496, Human Differentiation 5.510, Skill Transferability 8.765 and Career Adaptability 8.379. Jev elapsed time was 5.068 seconds. `maths_economics_geography` used `a-level-v1` and `a-level-decision-v1`: Future Optionality 7.226, Degree Pathway Breadth 6.344, Analytical Foundation 7.200, Human and Creative Strength 6.839, Skill Transferability 7.405 and AI Era Adaptability 7.001. Jev elapsed time was 1.234 seconds. Both had MODERATE assessment confidence and READY_WITH_CAUTION status. All schema, distribution, confidence, provenance and Java policy assertions passed. Their reports are `target/jev-evaluation/university-v1-report.md` and `target/jev-evaluation/a-level-v1-report.md`.

The three live OpenAI narrative outputs below used the configured `gpt-4o-mini` model. These are actual typed responses from the production adapters, not fixture text. No response repeated numeric scores or supplied external evidence. Provider-side resolved model revisions, request IDs, token usage and attempt counts are not exposed by the narrative adapter or harness. No error or retry event was visible in the successful request logs; this does not prove that the underlying client made only one HTTP attempt.

After the narrative wiring fix and focused offline regression tests, the final `mvn clean verify` completed with **555 tests, zero failures, zero errors and zero skipped**. It selected no live Failsafe harness and made no Jev or OpenAI call.

| Journey and case | Narrative latency | Structured output | Validator | Persisted status |
|---|---:|---|---|---|
| Professional `senior_software_architect` | 14.029 s | Success | Passed, including a second check of saved output | `AVAILABLE` |
| University `computer_science` | 9.485 s | Success | Passed | `AVAILABLE` |
| A-level `maths_economics_geography` | 9.279 s | Success | Passed | `AVAILABLE` |

The University and A-level harness replayed the saved live Jev judgments through the corresponding Java policies. The replay used the reports' three-decimal values, and the resulting headline measures matched the saved decisions within 0.005. Each case saved an assessment and a `PENDING` narrative row in PostgreSQL, used its existing OpenAI generator and validator, then reloaded an `AVAILABLE` row. Full narrative outputs are under `target/openai-evaluation/`.

Manual content review found no invented salary, statistics, studies, university ranking, admission guarantee or admission prerequisites. The Professional text uses British spelling, explains moderate AI Exposure and Human Advantage and high AI Complementarity and Role Adaptability, does not call exposure a job-loss probability, and does not advise abandoning the career. It does say human skills provide value AI “cannot replicate at present”; that is stronger certainty than the prompt supports. Classify this as model non-compliance with the cautious wording instruction, not a validator bug. The University text stays aligned with its Java bands and makes no specific claims about admissions or graduate outcomes. It uses broad phrases such as “versatile candidates in the job market”, which should be edited for a more cautious product voice; it does not present them as sourced evidence. The A-level text is readable for the intended age group, keeps the selected subjects, and directs readers to published course requirements. It calls the human and creative aspect “strong” although the Java measure's band is BALANCED. This is model wording drift against the prompt's authoritative-band instruction. The existing validator is a narrow structural and prohibited-claim guardrail; it passed all three outputs and does not establish full semantic agreement. No narrative was regenerated to polish wording.

The Professional case used the one real Jev request required for the end-to-end check because the earlier senior report had been overwritten by the routine report and its complete atomic judgments and confidences were unavailable. Its flow saved the Jev judgments and Java decision, generated and validated the OpenAI narrative, and persisted both. Jev and policy took about 1.199 seconds; narrative generation took about 14.029 seconds. Two HTTP GETs of the saved result returned 200 and rendered the stored narrative summary. Test-only counters remained at Jev 1 and OpenAI 1 after both GETs, confirming that historical reload made no further provider call. The live Failsafe test's final assertion looked for the stored headline on the free page, which renders the summary; the assertion was corrected without repeating the paid flow. The persisted status, two successful renders and provider counters were recorded before that assertion failure. The test database was the normal transient Testcontainers PostgreSQL instance.

## Profile and provider contract

`prod` alone activates the real Jev and OpenAI adapters through `!local-fixture`. It requires `TYPESAFE_API_KEY` and `OPENAI_API_KEY`. Missing keys are named by the startup validator, without printing their values. `local-fixture` selects deterministic adapters and cannot be combined with `prod`. There is no provider to fixture fallback. `live-payment` and `live-evidence` are separate opt-in capabilities and are outside 18A.

The configured TypeSafe model alias is `jev-latest`; the configured OpenAI chat model defaults to `gpt-4o-mini` and may be set with `OPENAI_MODEL`. These are configured names, not verified provider-side model revisions.

The installed TypeSafe Java SDK 0.2.0 uses a 10-second request read timeout and a retry policy with up to two retries and a 30-second total timeout by default. It retries 408, 429, server errors and connection errors. The application does not currently override these defaults. Whether retries are appropriate for charged System One requests needs review with real-provider behaviour before release. No application-level retry or fixture fallback was found. OpenAI timeout and retry behaviour has not yet been verified against a real call.

The 62.43-second Failsafe duration is not evidence that a retry occurred. The saved Failsafe XML and report contain no retry/timeout event. Inspection of SDK bytecode shows that the 30-second budget is checked only after a failed attempt, before sleeping and starting another; it is not a hard deadline around a successful request or the whole test. The read timeout applies to the SDK's JDK request factory, but the available log does not isolate connection, response, mapping or test setup time. Attempt count and provider billing for that run remain unknown. No extra paid call was made to investigate it.

## Existing explicit live command

After exporting `TYPESAFE_API_KEY`, run one professional Jev case through the existing Failsafe harness:

```shell
./mvnw -Plive-jev-evaluation -Djev.eval.case=senior_software_architect test-compile failsafe:integration-test failsafe:verify
```

Use `routine_software_developer` as the case ID for the historical comparison. Each selected case makes one System One request containing all nine professional judgments and writes `target/jev-evaluation/professional-v1-report.md`. The run does not invoke OpenAI. Normal `mvn clean verify` must continue making zero Jev, OpenAI, Stripe or live evidence calls.

The same Failsafe profile now includes a student-journey live harness. Select the single test class and journey explicitly, so no Professional case is repeated:

```shell
./mvnw -Plive-jev-evaluation -Dit.test=StudentJevLiveEvaluationIT -Djev.eval.journey=university test-compile failsafe:integration-test failsafe:verify
./mvnw -Plive-jev-evaluation -Dit.test=StudentJevLiveEvaluationIT -Djev.eval.journey=a-level test-compile failsafe:integration-test failsafe:verify
```

The commands write separate reports under `target/jev-evaluation/`. The class is gated by the Failsafe system property, a non-empty `TYPESAFE_API_KEY`, and the exact journey selector. Each selected run submits one assessment to Jev and applies that journey's Java decision policy. It makes no OpenAI or database call. `-Dit.test` is required for these commands so the existing Professional harness is not selected.

## Explicit live OpenAI command

The live narrative harness is gated by `-Plive-openai-evaluation`, a non-empty `OPENAI_API_KEY`, and an exact case selector. Because it starts the full `prod` application context, `TYPESAFE_API_KEY` must also be available. Normal `mvn clean verify` does not select this Failsafe class and remains offline. The student cases replay saved Jev reports from `target/jev-evaluation/`; do not delete those reports before the live run.

```shell
./mvnw -Plive-openai-evaluation -Dit.test=LiveOpenAiNarrativeIT -Dopenai.eval.case=university test-compile failsafe:integration-test failsafe:verify
./mvnw -Plive-openai-evaluation -Dit.test=LiveOpenAiNarrativeIT -Dopenai.eval.case=a-level test-compile failsafe:integration-test failsafe:verify
./mvnw -Plive-openai-evaluation -Dit.test=LiveOpenAiNarrativeIT -Dopenai.eval.case=professional test-compile failsafe:integration-test failsafe:verify
```

Each invocation creates a fresh PostgreSQL Testcontainer. The Professional selector makes a new Jev and OpenAI call; do not rerun it merely to improve wording or recheck the corrected page assertion. The three requested live narrative calls and the real-AI Professional flow are complete. The remaining wording findings are review findings, not permission to silently change authoritative decisions or weaken validators.

Prompt 18B covers live evidence providers and Nomis. Prompt 18C covers Stripe integration. Prompt 18D retains release work and any remaining operational checks; none are part of this verification record.

The offline review also found that narrative provider failures were persisted as `FAILED` but did not increment `narrative.failures`; the metric previously covered only thrown lifecycle persistence errors. Each journey workflow now counts a returned `FAILED` state. Focused offline tests cover the three paths.

## Prompt 18A narrative calibration (2 October 2026)

The three prompts now state that Java's journey-specific measure names and bands are final: Professional LOW/MODERATE/HIGH, University LIMITED/DEVELOPING/STRONG, and A-level FOCUSED/BALANCED/BROAD. They prohibit rhetorical upgrades or downgrades of the same measure, categorical AI capability or future claims, and turning assessment inference into unsupported employment, salary, admission, employer or labour-market facts. The Professional prompt also distinguishes exposure from job-loss probability, complementarity from replacement, Human Advantage from proof of AI inability, and Role Adaptability from job security. The University prompt states that Future Readiness does not guarantee employment and Career Adaptability does not establish demand. The A-level prompt states that Future Optionality does not guarantee admission and Degree Pathway Breadth does not establish university prerequisites; it explicitly forbids calling BALANCED human and creative strength strong. Prompt/schema version identifiers are now `*-narrative-v2`; saved v1 snapshots retain their original version.

Deterministic additions are deliberately narrow. The Professional validator rejects explicit categorical AI capability/replacement or role disappearance/safety phrases. The University validator rejects explicit graduate employment and employer hiring promises. The A-level validator compares the saved Human and Creative Strength band with its corresponding explanation and rejects the observed “strong human and creative” wording when Java says BALANCED. This check is scoped to that field so ordinary use of “strong” about a different skill remains valid. General semantic agreement, unsupported qualitative employment implications, and equivalent band descriptions outside this pattern remain prompt-level and manual-review concerns; there is no general semantic NLP validator.

Focused offline regression tests passed before the live recheck. One new OpenAI generation per journey then ran through the production structured-output adapter and existing narrative validators. No Jev request was made. The University and A-level cases replayed saved Jev judgments through Java policy and persisted the new narrative. The Professional calibration used local fixture judgments through Java policy and did not persist; it is a wording recheck, not a repeat of the earlier real-Jev end-to-end test. The configured model was `gpt-4o-mini` in all three cases. Provider token usage and retry counts were not exposed; no failure or retry was observed in the successful requests.

| Journey | OpenAI latency | Structured output | Validator | Narrative state |
|---|---:|---|---|---|
| Professional `senior_software_architect` | 7.310 s | Success | Passed | Calibration only, not persisted |
| University `computer_science` | 18.492 s | Success | Passed | `AVAILABLE` |
| A-level `maths_economics_geography` | 6.091 s | Success | Passed, including saved-band check | `AVAILABLE` |

The Professional recheck avoids “AI cannot replicate”, replacement certainty, job-loss probability and invented statistics. It uses moderate AI Exposure and Human Advantage and high AI Complementarity and Role Adaptability. It still says the architect “can adjust to changing project requirements and learning new technologies” when explaining Role Adaptability, which could be read as a claim about the individual's ability despite the prompt defining a role-level measure. This is residual model non-compliance, not evidence that Java's decision changed. The University recheck uses cautious employment language and makes no employment promise, but calls an input development factor “rated as Developing” although the factor has no Java band; that is an unsupported narrative label and remains a prompt-level semantic risk. The A-level recheck uses BALANCED for the human and creative measure and aligns the other measure bands, with no admission guarantee, prerequisite claim or subject-change advice. Its “solidly positions you” wording is more confident than the desired cautious voice and should not be treated as evidence of career outcomes. No output was regenerated after review. Full outputs are `target/openai-evaluation/*-calibration-report.md`.

Final `mvn clean verify` succeeded offline: **560 tests, zero failures, zero errors, zero skipped**. The live Jev and OpenAI reports were restored under `target/` after Maven clean.
