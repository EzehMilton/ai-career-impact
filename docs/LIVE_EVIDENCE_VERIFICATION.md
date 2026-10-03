# Prompt 18B live evidence verification

Verified 3 October 2026. Prompt 18A's Jev/OpenAI verification is in [LIVE_AI_VERIFICATION.md](LIVE_AI_VERIFICATION.md). This phase made no Jev calls and did not change scoring, bands, narrative prompts or Stripe.

## Boundary and normal build

Jev supplies structured judgments; Java owns decisions; OpenAI explains the saved assessment. Factual employment, salary, course and admissions claims belong to evidence providers. Evidence is gathered downstream of the assessment and narrative, is saved with source metadata, and cannot change a Java measure. The OpenAI narrative receives no evidence item and therefore cannot rewrite a Nomis observation as a growth forecast. The paid report displays sourced items separately. The free report says that verified context appears where available; missing paid evidence says unavailable rather than implying completeness. A Professional paid item now displays source quality alongside source, geography, date, category and metric.

`live-evidence` is explicit and mutually exclusive with `local-fixture`. Its exact Professional title-to-SOC mapping must be configured. No provider or fixture fallback exists for the student journeys. The standard `mvn clean verify` does not select the live Failsafe profile, makes zero live evidence, Jev or OpenAI calls, and keeps historical evidence frozen. Saved result GET and paid report assembly only load existing evidence; they never refetch it. HTTP(S) evidence URLs are validated, escaped as links, and never fetched by the PDF renderer.

## Professional source and interpretation

Source: [ONS Annual Population Survey occupation dataset, Nomis APS218](https://www.nomisweb.co.uk/datasets/aps218), API dataset `NM_218_1`, SOC 2020, measure `20100` (Count). The adapter uses two requests per report:

1. `GET https://www.nomisweb.co.uk/api/v01/dataset/NM_218_1/SOC2020_FULL.def.sdmx.json` to resolve the configured exact four-digit SOC code to its Nomis codelist value.
2. `GET https://www.nomisweb.co.uk/api/v01/dataset/NM_218_1.data.json?geography=2092957697&time=latest&soc2020_full=<resolved-value>&measures=20100&setallwherenotspecified=false` for one UK observation.

The submitted profession must equal the operator-configured profession. There is no fuzzy title/SOC guessing, scraping or user-controlled URL. The adapter checks the response's dataset, SOC code and label, UK geography/GSS code, Count measure, unique numeric observation and reference period. It rejects unsupported geography before a request. Connection/read timeouts are 5/10 seconds. The item retains the response period, retrieval date, source URL, SOC reference, occupation and `OFFICIAL` quality. Nomis did not supply an observation publication date, so that field remains absent. The UK count describes the occupation for that reference period; it is neither a regional estimate, trend, causal effect nor forecast.

The [Nomis API help](https://www.nomisweb.co.uk/api/v01/help) describes anonymous use and its 25,000-cell limit and concurrent-request controls. These calls use no credential and return one observation, but the full codelist is fetched for each new report. There is no added cache or paid Nomis API charge in this implementation; future volume needs monitoring against Nomis usage terms and latency. The data source is ONS, served by Nomis.

## Live commands and results

The live tests require explicit Failsafe selection and `evidence.live.evaluation.enabled=true` (set by the Maven profile). The independent test needs network access; the paid flow also needs `OPENAI_API_KEY`, `TYPESAFE_API_KEY` for production-shape startup, and a usable Testcontainers database. No key value is logged.

```sh
./mvnw -Plive-evidence-evaluation -Dit.test=NomisLiveEvidenceIT test-compile failsafe:integration-test failsafe:verify
./mvnw -Plive-evidence-evaluation -Dit.test=ProfessionalLiveEvidenceFlowIT test-compile failsafe:integration-test failsafe:verify
```

Both final explicit runs succeeded. Independent request: `senior_software_architect`'s configured SOC `2133`, “IT business analysts, architects and systems designers”; `AVAILABLE`, UK, APS218 / SOC 2020 2133, **212,200 people**, Apr 2025–Mar 2026, retrieved 2026-10-03 09:41 UTC, `OFFICIAL`, about **3.4 s**, two HTTP requests. Full output: `target/live-evidence/nomis-2133-report.md`.

Controlled paid flow: replayed the verified Prompt 18A routine software developer Jev judgments (**zero new Jev calls**), ran Java policy, made one real OpenAI narrative generation, mapped the exact configured `Software Developer` to SOC `2134`, gathered real Nomis evidence, persisted all records, assembled and rendered the paid Professional report. OpenAI narrative `AVAILABLE` in about **10.4 s**. Nomis evidence `AVAILABLE` in about **0.7 s** for two HTTP requests: **543,400 people**, SOC 2134 “Programmers and software development professionals”, UK, Apr 2025–Mar 2026, retrieved 2026-10-03 09:59 UTC, `OFFICIAL`. The rendered item showed source, date, quality, occupation-outlook category, geography and metric. Loading the saved evidence and reassembling the paid report returned the same snapshot without Nomis, Jev or OpenAI calls. Full output: `target/live-evidence/professional-paid-flow-report.md`. The harness initially exposed a missing test context setting and then a Thymeleaf web-context setup issue; these were corrected in the harness, and the successful run is the reported result. The intermediate harness runs made provider calls before rendering completed; no claim is made that only one paid provider call occurred across all development attempts.

The original Nomis adapter was defective: the `APS218.data.json` path returned HTTP 200 API help HTML and its parser assumed a flat JSON layout. The current adapter uses the actual dataset identifier and nested observation shape. Offline stubs cover a valid response, unknown code, unexpected geography/malformed value and HTTP 500. The service stores safe `FAILED` with no items for provider failures and never substitutes a fixture. Existing saved `AVAILABLE`, `PARTIAL` and `FAILED` bundles are returned unchanged. Timeout is bounded by the configured HTTP client; no destructive live outage was induced.

## Student source decisions

**University: defer a live adapter.** `UniversityEvidenceRequest` has course title, modules, skills and optional institution but no stable course/provider ID. Categories are `COURSE_CONTENT`, `GRADUATE_OUTCOMES`, `OCCUPATIONAL_DESTINATIONS`, `SKILL_DEMAND`, `LABOUR_MARKET_CONTEXT`. [Discover Uni's methodology](https://www.discoveruni.gov.uk/about-our-data/) and [HESA's official dataset download](https://www.hesa.ac.uk/support/tools-and-downloads/unistats) are credible for identified courses; Graduate Outcomes uses a later survey and may be grouped or suppressed. [HESA's Graduate Outcomes releases](https://www.hesa.ac.uk/data-and-analysis/graduates/releases) contain official aggregates, which must not be presented as outcomes for an arbitrary course. Course content needs an exact provider course page; occupation and skill-demand data need a justified course-to-occupation link. Without that identity/mapping, all five categories remain unavailable. A future adapter can ingest official bulk data with stable IDs and attribution; it must label aggregation and reference year.

**A-level: defer a live adapter.** `ALevelEvidenceRequest` has subjects and preferred degree/career areas, without an exact university, course and entry year. Categories are `DEGREE_PREREQUISITES`, `COURSE_ENTRY_CONTEXT`, `SUBJECT_PATHWAY_CONTEXT`, `LABOUR_MARKET_CONTEXT`, `SKILL_DEMAND_CONTEXT`. [UCAS provider data guidance](https://www.ucas.com/media/179356/download) and [Discover Uni provider information](https://discoveruni.gov.uk/information-providers/) place course information with providers. No general subject combination proves an individual admission requirement. Verified ingestion of official course/admissions pages, retaining provider, course, entry year, URL, retrieval date and review, is safer than live generic scraping or search snippets. Until implemented, all five categories remain unavailable; OpenAI memory does not fill them.

There are no student live evidence calls in this phase. The fixture providers remain development-only and are visibly labelled as such. No salary, ranking, graduate statistic, admission requirement or labour-market prediction was added from model memory.

## Reserved work

Prompt 18C: Stripe live checkout, signed webhook and entitlement verification. Prompt 18D: release and operational hardening. Broader Professional occupation coverage, other evidence categories and verified student source ingestion remain evidence backlog items; this phase implements none of them.
