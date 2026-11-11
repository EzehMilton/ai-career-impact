# Manual product test

A customer-style walkthrough of the whole product, run offline under the `dev` profile. Nothing here contacts Jev, OpenAI, an evidence provider or Stripe, and no real credential is needed or should ever be added to this file.

Re-run this after any change to the shell, the templates, the stylesheet, the scripts or the security configuration.

**History.** An earlier version of this checklist (sections 0 to 8) was executed against the running application on 2026-10-01 over HTTP and all passed, including the full free → simulated payment → paid report → PDF chain, cross-journey entitlement isolation, and a restart with historical reload. The checklist was brought up to date with the code on 2026-11-08: the `dev` profile, the v2 University and A-level profiles, the detailed-report preparation step, payment confirmation, complimentary access and the loading overlay. This version has not yet been executed end to end by hand. The loading overlay (section 7) was verified in headless Chrome on 2026-11-08 for all three free assessments and an A-level preparation, at desktop and mobile widths. Sections 10 and 11 still need a person to confirm them visually.

## 0. Start the application

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Docker must be running: `spring-boot-docker-compose` starts the PostgreSQL service from `compose.yaml` automatically, and Flyway applies V1–V25 on first start. Make sure nothing else is already listening on port 8080, or the application will fail to start and you may end up testing another instance.

Confirm in the startup log, once per journey (three lines in total):

```
LOCAL FIXTURE MODE ACTIVE - PROFESSIONAL RESULTS ARE NOT LIVE JEV RESULTS
LOCAL FIXTURE MODE ACTIVE - UNIVERSITY RESULTS ARE NOT LIVE JEV RESULTS
LOCAL FIXTURE MODE ACTIVE - A-LEVEL RESULTS ARE NOT LIVE JEV RESULTS
```

Optional, only to see the price line: set `PROFESSIONAL_REPORT_DISPLAY_PRICE`, `UNIVERSITY_REPORT_DISPLAY_PRICE` and `A_LEVEL_REPORT_DISPLAY_PRICE` to any display string (for example `£12.00`) before starting. Leave them unset and no price is shown on the result pages, which is the intended behaviour — the product never invents an amount.

Optional, to test complimentary access: set `COMPLIMENTARY_REPORT_ENABLED=true` and `COMPLIMENTARY_REPORT_CODE` to a value of your choice.

If you start on a port other than 8080, note that the checkout return URL comes from `app.public-base-url` (`APP_PUBLIC_BASE_URL`, default `http://localhost:8080`). The local checkout link will therefore point at the configured address, not the port you ran on.

What `dev` can and cannot show: every free result, the simulated checkout and the paid pages work. Preparing a **Professional or University** detailed report needs a paid Jev analysis, which has no provider under `dev`, so preparation ends with a safe "unavailable" message. The **A-level** detailed report needs no Jev call; with no paid narrative generator under `dev` it is shown provisionally from deterministic copy, with an **Add the personalised explanation** button. To see a fully prepared detailed report, use `live-smoke` ([LIVE_SMOKE_TESTING.md](LIVE_SMOKE_TESTING.md)); `dev-no-jev` also completes the A-level one with a real OpenAI narrative.

## 1. Landing page

Open `http://localhost:8080/`.

- [ ] The page explains what this is without jargon ("How will AI affect your career or education?")
- [ ] Three journey cards are offered — **Professional**, **University**, **A-level** — each with its own question and a link only to its own journey
- [ ] Nothing invites you to compare the three results against one another
- [ ] A free-versus-detailed comparison is present, framed as depth rather than a different answer
- [ ] A yellow **Development mode** banner is visible, naming the three fixtures in use
- [ ] A **Development shortcuts** section is visible
- [ ] Known copy gap: the University and A-level cards still say "Five measures" and "Six measures"; new results show three and four. Record it, do not fail the run on it

## 2. Shared chrome, on every page

Visit `/`, `/methodology`, `/privacy` and all three journey start pages.

- [ ] The same header and footer appear on all six
- [ ] The header links to all three journeys
- [ ] On `/` and the three journey pages, exactly one header item is marked as the current page
- [ ] On `/methodology` and `/privacy`, no header item claims to be current (they are reached from the footer)
- [ ] The footer links to Home, How this works and Your information, and carries the "does not predict anyone's future" note
- [ ] Pressing Tab as the first action reveals a **Skip to main content** link

## 3. Professional journey

From the landing page, follow the **Professional** card.

- [ ] The form explains who it is for and what you will need, and links to a worked example (`/professional-assessment/example`)
- [ ] Submit the form. The loading overlay appears (section 7), then you are redirected to a result page
- [ ] The result shows **AI Exposure** and the other three professional measures
- [ ] Factor lists show only material factors; an empty side is explained rather than filled
- [ ] A caveat states what the exposure figure is **not** (it is not a probability of losing your job)
- [ ] There is **no** Download PDF link and no `report.pdf` anywhere on the free result
- [ ] A price line appears only if a display price is configured
- [ ] The form offers no file upload, and `/professional-assessment/cv` shows the not-found page

## 4. University journey

- [ ] The form states that this assesses **the course, not you**
- [ ] Entering the same module twice (in any letter case) is refused with a field error
- [ ] After submitting, the result is a course profile of three measures — **Working Alongside AI**, **Human and Practical Capability**, **Transferable Capability** — with no overall score
- [ ] Each measure shows its band and what drives and limits it; nothing is ranked or added up
- [ ] The wording never implies these numbers are comparable with the professional or A-level ones
- [ ] The result has no personalised narrative (it is deterministic), and makes no employment promise

## 5. A-level journey

- [ ] The form states that no subject is universally good or bad, and that being undecided is fine
- [ ] Fewer than two subjects is a friendly field error
- [ ] After submitting, the result is a subject profile — **Options Kept Open**, the two Capability Profile sides (**Analytical & Quantitative**, **Communication, Interpretation & Context**) shown together, and **Learning Alongside AI** — with no overall score
- [ ] A lighter capability side is described as the shape of the combination, never as a weakness
- [ ] Nothing reads as a ranking of subjects, and nothing predicts an offer or states an entry requirement
- [ ] Predicted grades are accepted but visibly do not drive the result

## 6. Free to paid, and the PDF

On any free result, use **Get my detailed report**.

- [ ] The local checkout page is unmistakably development-only and says **No real Stripe checkout or payment has occurred**
- [ ] It offers **Simulate payment confirmation**
- [ ] Before confirming, requesting `/{journey}-assessment/{id}/report` directly returns 403 and a page offering **Back to your free result** — not a stack trace, and not the report
- [ ] Before confirming, `/{journey}-assessment/{id}/report.pdf` also returns 403
- [ ] Confirm the simulated payment. You reach the paid report page
- [ ] The paid page offers **Prepare my detailed report** (University: "Choose up to 8 modules" first when the course lists more than eight, nothing pre-selected; Professional: the same for more than eight responsibilities)
- [ ] Preparing shows the loading overlay with the heading "Preparing your detailed report"
- [ ] A-level: the detailed report renders provisionally from deterministic copy with **Add the personalised explanation**; it tells you to check published course requirements and never states one
- [ ] Professional and University: preparation ends with a calm "unavailable" message under `dev` (no paid Jev analysis); the purchased report remains viewable, and nothing technical is shown
- [ ] **Download PDF** appears only on paid report pages; where it appears, the PDF downloads, opens, and begins with `%PDF`. A provisional detailed report offers no PDF
- [ ] The paid report links back to the free result, to how this works, and to home
- [ ] With complimentary access enabled: a fresh free result shows the access-code form; a wrong code is refused without echoing it; the right code unlocks the same paid page without any checkout. With it disabled, the form is absent and posting to `/{journey}-assessment/{id}/complimentary-access` gives the not-found page

## 7. Waiting for a long-running submission

Use the browser's developer tools to throttle the network (for example "Slow 3G") so the wait is visible.

- [ ] After a valid submission, a full-screen overlay appears: the page stays recognisable behind it, dimmed and softly blurred
- [ ] It shows a spinner, the heading ("We're analysing your information" for an assessment, "Preparing your detailed report" for a report), a rotating message that changes about every four seconds and stops on "Almost there…", a thin bar with no percentage, and "Please keep this page open"
- [ ] Nothing underneath can be clicked or reached with Tab, and a second click or Enter does not send a second request
- [ ] With "prefers reduced motion" emulated, the spinner and bar are still and the messages still change
- [ ] Press Back after the result loads: the form is usable again, not stuck behind the overlay
- [ ] Checkout, simulated payment and complimentary-access forms never show the overlay

## 8. Access, isolation and restart

- [ ] Open the paid report URL in a private window with no cookies. It still renders: the link itself is the access mechanism
- [ ] Buying one journey's report does not unlock another journey's. Take a second journey's assessment id and request its `/report` — it must still be refused
- [ ] Stop the application and start it again. Reload the same free result, the same paid report and any downloaded PDF. All still work, and a stored PDF is byte-identical
- [ ] Nothing is regenerated on a reload

## 9. Errors and wrong links

- [ ] `/{journey}-assessment/00000000-0000-0000-0000-000000000000` gives a friendly "We couldn't find that assessment." page with the shared chrome, a link to start that journey again, and a link home
- [ ] `/{journey}-assessment/not-a-uuid` gives a byte-identical page — a mistyped id and an unknown id are indistinguishable
- [ ] A URL matching no route at all (for example `/no-such-page`) gives the friendly 404 page, with no login redirect
- [ ] No page anywhere shows a stack trace, an exception name, a provider name, a database detail, a configuration value or a system prompt

## 10. Mobile width

Narrow the window to about 375px, or use a device emulator.

- [ ] The page never scrolls sideways, including while the loading overlay is shown
- [ ] The header navigation stays usable
- [ ] Measure grids collapse to one column and definition lists stack
- [ ] Tap targets remain comfortably large
- [ ] Long links inside evidence cards wrap instead of widening the page

## 11. Accessibility basics

- [ ] Every page has exactly one `<h1>`
- [ ] The whole of each form can be reached and completed with the keyboard alone
- [ ] Submit a form with a deliberately invalid field: the message is announced, sits next to its field, and is prefixed with "Error:" so colour is not the only signal
- [ ] Focus is clearly visible on links, buttons and inputs
- [ ] Zoom to 200%. Nothing is clipped or overlapped
- [ ] With a screen reader, the loading overlay's heading is read once and the rotating message is announced politely without moving focus

## 12. Production shape

`prod` needs real provider credentials, Stripe settings and the occupation mapping, so it cannot be started offline: `StartupConfigurationValidator` lists every missing setting by name and refuses to start. That is correct behaviour. Development chrome is instead guaranteed by test, and those tests are the thing to trust here:

- `ProductShellConfigurationTest` — under `dev` the status is active and names the fixtures; under `prod` and `dev-no-jev` it is inactive, and no property can switch it on. (`live-smoke` is wired inactive by the same configuration and shows its own LIVE SMOKE banner instead.)
- `HomeControllerTest` — with no development bean present, the landing page contains no banner, no development section and no fixture wording.
- `DevelopmentModeShellTest` — with one present, it does.
- `ProductShellConsistencyTest` — no template mentions development wording without also branching on a development guard, only paid report templates offer a PDF download, and no template uses a plain HTML comment, which would otherwise be passed straight through into the response.

If you do have real credentials and run `prod` (or `dev-no-jev` with Stripe test keys), confirm:

- [ ] No **Development mode** banner on any page
- [ ] No **Development shortcuts** section on the landing page
- [ ] No local payment simulation route is reachable — those controllers exist only under `dev`
- [ ] No page contains fixture wording
- [ ] The landing page, `/methodology` and `/privacy` still render

## 13. Offline guarantee

- [ ] The application log contains no outbound call to any external host
- [ ] No Stripe, OpenAI or evidence-provider request is made at any point in this walkthrough
- [ ] Disconnecting the network changes nothing about the behaviour above
