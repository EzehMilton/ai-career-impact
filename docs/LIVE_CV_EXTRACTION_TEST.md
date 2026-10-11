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

Use a real, text-based PDF or DOCX CV whose header contains an email address, a phone number and,
ideally, a LinkedIn URL and a UK postcode, and which lists more than one dated job.

1. Start `AI Career Impact - CV Extraction Test`.
2. Open `http://localhost:8080/professional-assessment`.
3. Upload the CV.
4. Select **Analyse my CV**.
5. Confirm that **We analysed your CV** contains information from that CV rather than the selected
   `senior_software_architect` fixture.
6. Check the role line above the profile. It should read "We used your role at {employer} ({start} –
   {end})." for the job that is current in the CV, or the employer-only or dates-only form when the CV
   gives less. There is no line when neither is known.
7. Compare the current role, role summary, responsibilities, technical skills and human/leadership
   strengths with the uploaded CV. Responsibilities should be recurring tasks from the current job
   only, not one-off achievements or duties from earlier jobs. Confirm the guidance under **Main
   responsibilities** ("CVs usually list achievements rather than everyday tasks…") is shown.
8. Check **Years of experience** and the "Based on: {title} at {employer} ({start} – {end})" lines
   under it. They should list exactly the current job and the earlier jobs in the same or a closely
   related profession, and the number should match those dates with overlaps counted once. When the
   dates cannot be used, the number is missing, there are no "Based on" lines, and a warning asks you
   to add your years if you know them.
9. Verify uncertain information is unknown, omitted, or called out in a warning rather than invented.
10. Confirm that no email address, phone number, link or postcode from the CV header appears anywhere
    on the review page, and that the role, employer, dates and duties were still extracted correctly.
    What is sent to OpenAI is deliberately never logged, so it cannot be inspected here; the masking of
    that text is proven by `ProfessionalCvContactMaskerTest` and by
    `OpenAiProfessionalProfileExtractorTest.contactDetailsAreRemovedFromTheTextSentToOpenAi`. Do not
    enable request logging to check it, because that would write CV content to the log.
11. Select **Edit what we understood**. Confirm **Employer**, **Started** and **Ended (or Present)**
    are editable and the duties guidance is shown. Change the years of experience, save, and confirm
    the review page shows the new number without "Based on" lines. Change another field without
    touching the years, and confirm the "Based on" lines stay.
12. Select **Continue with this profile**.
13. Confirm the resulting assessment uses the local fixture judgment behaviour. The development
    banner and fixture ID remain visible, and no TypeSafe/Jev, Stripe or Nomis call is expected.
14. Confirm the extractor version was saved with the assessment. Docker Compose runs the local
    database, so from the project directory:

    ```sh
    docker compose exec postgres psql -U myuser -d mydatabase -c \
      "select input_source, cv_parser_version, cv_extractor_version from professional_assessment order by created_at desc limit 1;"
    ```

    Expect `CV_UPLOAD`, `professional-cv-parser-v2` and `professional-cv-extractor-v2`. In plain
    `local-fixture` mode, without `live-cv-extraction`, the extractor version is
    `professional-cv-extractor-fixture-v1`.

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
- Contact masking removes email addresses, links, phone numbers and full UK postcodes written in
  capitals. Names and street addresses are still sent to OpenAI.
