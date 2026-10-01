# Manual product test

A customer-style walkthrough of the whole product, run entirely offline. Nothing here contacts Jev, OpenAI, an evidence provider or Stripe, and no real credential is needed or should ever be added to this file.

Re-run this after any change to the shell, the templates, the stylesheet or the security configuration.

Sections 0 to 8 were executed against the running application on 2026-10-01 over HTTP and all passed, including the full free → simulated payment → paid report → PDF chain, cross-journey entitlement isolation, and a restart with historical reload. Sections 9 and 10 need a real browser: no screenshot or rendering tool was available, so those were reviewed by reading the HTML structure and stylesheet rather than by looking at the pages, and a human should still confirm them visually. Section 11 cannot be run offline at all, for the reason given there.

## 0. Start the application

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-fixture
```

Docker must be running: `spring-boot-docker-compose` starts the PostgreSQL service from `compose.yaml` automatically, and Flyway applies V1–V17 on first start.

Confirm in the startup log, once per journey (three lines in total):

```
LOCAL FIXTURE MODE ACTIVE - PROFESSIONAL RESULTS ARE NOT LIVE JEV RESULTS
LOCAL FIXTURE MODE ACTIVE - UNIVERSITY RESULTS ARE NOT LIVE JEV RESULTS
LOCAL FIXTURE MODE ACTIVE - A-LEVEL RESULTS ARE NOT LIVE JEV RESULTS
```

Optional, only to see the price line: set `PROFESSIONAL_REPORT_DISPLAY_PRICE`, `UNIVERSITY_REPORT_DISPLAY_PRICE` and `A_LEVEL_REPORT_DISPLAY_PRICE` to any display string (for example `£12.00`) before starting. Leave them unset and no price is shown anywhere, which is the intended behaviour — the product never invents an amount.

If you start on a port other than 8080, note that the checkout return URL comes from `app.base-url` in configuration. The local checkout link will therefore point at the configured port, not the one you ran on.

## 1. Landing page

Open `http://localhost:8080/`.

- [ ] The page explains what this is without jargon, and asks "What are you thinking about?"
- [ ] Three options are offered in the visitor's own words: **My current job**, **A university course**, **My A-level subjects**
- [ ] Each card states the question it answers and links only to its own journey
- [ ] Nothing invites you to compare the three results against one another
- [ ] A free-versus-full comparison is present, framed as depth rather than a different answer
- [ ] A yellow **Development mode** banner is visible, naming the three fixtures in use
- [ ] A **Development shortcuts** section is visible

## 2. Shared chrome, on every page

Visit `/`, `/methodology`, `/privacy` and all three journey start pages.

- [ ] The same header and footer appear on all six
- [ ] The header links to all three journeys
- [ ] On `/` and the three journey pages, exactly one header item is marked as the current page
- [ ] On `/methodology` and `/privacy`, no header item claims to be current (they are reached from the footer)
- [ ] The footer links to Home, How this works and Your information, and carries the "does not predict anyone's future" note
- [ ] Pressing Tab as the first action reveals a **Skip to main content** link

## 3. Professional journey

From the landing page, follow **My current job**.

- [ ] The form explains who it is for and what you will need, and says that uploading a CV is optional
- [ ] Submit the form manually. You are redirected to a result page
- [ ] The result shows **AI Exposure** and the other professional measures
- [ ] The result reads in this order: headline, how to read the numbers, measures, why, short interpretation, free-versus-full, context
- [ ] A caveat states what the exposure figure is **not** (it is not a probability of losing your job)
- [ ] There is **no** Download PDF link and no `report.pdf` anywhere on the free result
- [ ] A price line appears only if a display price is configured

**CV upload.** Return to `/professional-assessment` and upload a PDF or DOCX.

- [ ] The form reappears with a **Review your extracted details** notice
- [ ] Extracted values are editable, and you are asked to check them rather than told they are correct
- [ ] Uploading a file that is not really a CV produces empty or sparse fields and the same review prompt, not an error page

## 4. University journey

- [ ] The form states that this assesses **the course, not you**
- [ ] After submitting, the result shows **Future Readiness**
- [ ] Its caveat makes clear that readiness is not a guarantee of employability
- [ ] The wording never implies this number is comparable with the professional or A-level ones

## 5. A-level journey

- [ ] The form states that no subject is universally good or bad, and that being undecided is fine
- [ ] After submitting, the result shows **Future Optionality**
- [ ] Nothing reads as a ranking of subjects, and nothing predicts an offer
- [ ] Predicted grades are accepted but visibly do not drive the result

## 6. Free to paid, and the PDF

On any free result, use **Unlock my full report**.

- [ ] The local checkout page is unmistakably development-only and says **No real Stripe checkout or payment has occurred**
- [ ] It offers **Simulate payment confirmation**
- [ ] Before confirming, requesting `/{journey}-assessment/{id}/report` directly returns 403 and a page offering **Back to your free result** — not a stack trace, and not the report
- [ ] Before confirming, `/{journey}-assessment/{id}/report.pdf` also returns 403
- [ ] Confirm the simulated payment. You are redirected to the paid report
- [ ] The paid report adds explanation, personalised actions and evidence — the deterministic measures are the same ones you already saw
- [ ] **Download PDF** appears in the header area and again at the foot of the report
- [ ] The PDF downloads, opens, and begins with `%PDF`
- [ ] The paid report links back to the free result, to how this works, and to home

## 7. Access, isolation and restart

- [ ] Open the paid report URL in a private window with no cookies. It still renders: the link itself is the access mechanism
- [ ] Buying one journey's report does not unlock another journey's. Take a second journey's assessment id and request its `/report` — it must still be refused
- [ ] Stop the application and start it again. Reload the same free result, the same paid report and the same PDF. All three still work, and the PDF is byte-identical, because the purchased snapshot is frozen
- [ ] Nothing is regenerated on a reload

## 8. Errors and wrong links

- [ ] `/{journey}-assessment/00000000-0000-0000-0000-000000000000` gives a friendly "We couldn't find that assessment." page with the shared chrome, a link to start that journey again, and a link home
- [ ] `/{journey}-assessment/not-a-uuid` gives a byte-identical page — a mistyped id and an unknown id are indistinguishable
- [ ] No page anywhere shows a stack trace, an exception name, a provider name, a database detail, a configuration value or a system prompt
- [ ] Known gap: a URL matching no route at all redirects to the login form rather than showing the friendly 404. This is recorded in `ARCHITECTURE.md` and left to the security-hardening phase

## 9. Mobile width

Narrow the window to about 375px, or use a device emulator.

- [ ] The page never scrolls sideways
- [ ] The header navigation stays usable
- [ ] Measure grids collapse to one column and definition lists stack
- [ ] Tap targets remain comfortably large
- [ ] Long links inside evidence cards wrap instead of widening the page

## 10. Accessibility basics

- [ ] Every page has exactly one `<h1>`
- [ ] The whole of each form can be reached and completed with the keyboard alone
- [ ] Submit a form with a deliberately invalid field: the message is announced, sits next to its field, and is prefixed with "Error:" so colour is not the only signal
- [ ] Focus is clearly visible on links, buttons and inputs
- [ ] Zoom to 200%. Nothing is clipped or overlapped

## 11. Production shape

The non-fixture profiles deliberately require real provider credentials — starting without `local-fixture` fails with `Could not resolve placeholder 'TYPESAFE_API_KEY'`, because there is no fallback from a real provider to a fixture. That is correct behaviour, so this step cannot be checked by starting the application offline.

It is instead guaranteed by test, and those tests are the thing to trust here:

- `ProductShellConfigurationTest` — under `local-fixture` the status is active and names the fixtures; under an empty, `live-evidence` or `live-payment` profile it is inactive, and no property can switch it on.
- `HomeControllerTest` — with no development bean present, the landing page contains no banner, no development section and not the string `local-fixture`.
- `DevelopmentModeShellTest` — with one present, it does.
- `ProductShellConsistencyTest` — no template mentions development wording without also branching on a development guard, and no template uses a plain HTML comment, which would otherwise be passed straight through into the response.

If you do have real credentials and run a non-fixture profile, confirm:

- [ ] No **Development mode** banner on any page
- [ ] No **Development shortcuts** section on the landing page
- [ ] No local payment simulation route is reachable — those controllers do not exist outside `local-fixture`
- [ ] No page contains the string `local-fixture`
- [ ] The landing page, `/methodology` and `/privacy` still render

## 12. Offline guarantee

- [ ] The application log contains no outbound call to any external host
- [ ] No Stripe, OpenAI or evidence-provider request is made at any point in this walkthrough
- [ ] Disconnecting the network changes nothing about the behaviour above
