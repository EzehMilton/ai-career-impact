# AI Career Impact contributor guide

- Use Java 21. Prefer records for immutable value objects where they fit.
- Organise code by business capability, not one global controller/service/repository/model layout. Keep this application a modular monolith.
- Keep controllers thin; put business rules in domain or application services.
- Jev makes atomic judgments. Java owns deterministic decisions, weights, and thresholds. OpenAI provides explanations and narrative, never authoritative scores.
- Source salaries, labour-market statistics, and other external facts from an evidence layer rather than model memory.
- Normal unit and integration tests must never make real OpenAI or TypeSafe/Jev calls.
- Do not add infrastructure or dependencies unless the current task requires them. Avoid premature microservices, messaging, and vector databases.
- Run `./mvnw clean verify` after changes.
