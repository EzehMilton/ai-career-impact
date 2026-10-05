# Live CV extraction development mode

## Purpose and boundaries

`live-cv-extraction` is a local-development overlay for `local-fixture`. It changes only the
professional CV profile extractor from the deterministic fixture to the existing OpenAI structured-
output extractor. Professional judgment, Java policy, narrative, occupation classification,
evidence and payment remain on their local fixture paths.

The startup validator refuses to run `live-cv-extraction` without `local-fixture`. It requires
`OPENAI_API_KEY` and does not require `TYPESAFE_API_KEY`, Stripe secrets or live Nomis/evidence
configuration. There is no provider-to-fixture fallback: an OpenAI timeout, provider failure,
malformed structured response or invalid extracted profile returns a safe failure page with a
**Describe my work manually** link.

## IntelliJ run configuration

Create a **Spring Boot** run configuration with these exact settings:

- Name: `AI Career Impact - CV Extraction Test`
- Main class: `com.chikere.futureai.AiCareerImpactApplication`
- Module: `ai-career-impact`
- JRE: `21`
- Active profiles: `local-fixture,live-cv-extraction`
- Environment variables: `OPENAI_API_KEY=<real key>`
- Program arguments: `--app.professional.fixture-id=senior_software_architect --app.university.fixture-id=computer_science --app.a-level.fixture-id=maths_economics_geography`

Keep the profile order as shown: the narrowly scoped live profile deliberately overrides the
`spring.ai.model.chat=none` setting from `local-fixture`. Do not add `prod`, `live-payment` or
`live-evidence`. Do not add `TYPESAFE_API_KEY`, Stripe keys, or Nomis configuration for this mode.

## Manual acceptance test

1. Start `AI Career Impact - CV Extraction Test`.
2. Open `http://localhost:8080/professional-assessment`.
3. Upload a real, text-based PDF or DOCX CV.
4. Select **Analyse my CV**.
5. Confirm that **We analysed your CV** contains information from that CV rather than the selected
   `senior_software_architect` fixture.
6. Compare the current role, role summary, responsibilities, technical skills, human/leadership
   strengths, and years of experience with the uploaded CV.
7. Verify uncertain information is unknown, omitted, or called out in a warning rather than invented.
8. Select **Edit what we understood**, change one field, save it, and verify the review page shows
   the edit.
9. Select **Continue with this profile**.
10. Confirm the resulting assessment uses the local fixture judgment behaviour. The development
    banner and fixture ID remain visible, and no TypeSafe/Jev, Stripe or Nomis call is expected.

For a failure-path check, temporarily use an invalid OpenAI key or interrupt connectivity, upload a
CV, and confirm the application shows a safe extraction failure and offers **Describe my work
manually**. It must not display fixture profile fields, raw CV text, provider JSON, prompts, or the
provider payload.

## Known limitations

- PDF and DOCX text extraction is deterministic; scanned/image-only PDFs have no OCR fallback.
- This mode assesses extraction quality manually. It is not a provider evaluation harness and does
  not capture raw provider responses or token usage.
- The configured OpenAI model defaults to `gpt-4o-mini` and can be changed with `OPENAI_MODEL`.
- Raw CV bytes and extracted text are transient and must never be copied into logs or test fixtures.
