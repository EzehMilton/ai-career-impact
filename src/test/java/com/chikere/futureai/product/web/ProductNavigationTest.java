package com.chikere.futureai.product.web;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The journey a first-time visitor actually takes: arrive at the landing page, understand the three
 * options in plain language, and reach the one that matches their situation. Exercised over real
 * HTTP so routing, security and view resolution all participate.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductNavigationTest {

    private record Journey(String path, String framing, String navLabel) { }

    private static final List<Journey> JOURNEYS = List.of(
            new Journey("/professional-assessment", "My current job", "Professional"),
            new Journey("/university-assessment", "A university course", "University"),
            new Journey("/a-level-assessment", "My A-level subjects", "A-levels"));

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;

    @Test
    void theLandingPageOffersAllThreeJourneysInPlainLanguage() throws Exception {
        String home = get("/").body();

        for (Journey journey : JOURNEYS) {
            assertThat(home).as("home offers %s", journey.path())
                    .contains(journey.framing(), "href=\"" + journey.path() + "\"");
        }
        // Nothing on the landing page should imply the three results are comparable with one another.
        assertThat(home).doesNotContain("score out of", "ranking", "compare your results");
    }

    @Test
    void everyJourneyAdvertisedOnTheLandingPageIsActuallyReachableAndStartsAForm() throws Exception {
        for (Journey journey : JOURNEYS) {
            HttpResponse<String> response = get(journey.path());

            assertThat(response.statusCode()).as("%s status", journey.path()).isEqualTo(200);
            assertThat(response.body()).as("%s is a form", journey.path())
                    .contains("<form", "method=\"post\"", "_csrf");
        }
    }

    @Test
    void theHeaderMarksTheCurrentJourneyAndTheFooterIsTheSameEverywhere() throws Exception {
        // The header carries the four primary destinations; the informational pages are reached from
        // the footer instead, so on those nothing in the header may claim to be the current page.
        var primary = List.of("/", "/professional-assessment", "/university-assessment",
                "/a-level-assessment");
        for (String path : List.of("/", "/methodology", "/privacy",
                "/professional-assessment", "/university-assessment", "/a-level-assessment")) {
            String html = get(path).body();

            assertThat(html).as("%s header", path).contains("class=\"site-header\"");
            for (Journey journey : JOURNEYS) {
                assertThat(html).as("%s header links to %s", path, journey.path())
                        .contains("href=\"" + journey.path() + "\"");
            }
            assertThat(html).as("%s footer", path)
                    .contains("href=\"/methodology\"", "href=\"/privacy\"", "AI Career Impact");
            int current = html.split("aria-current=\"page\"", -1).length - 1;
            assertThat(current).as("%s current-page markers", path)
                    .isEqualTo(primary.contains(path) ? 1 : 0);
        }
    }

    @Test
    void theProfessionalJourneyStillOffersBothManualEntryAndCvUpload() throws Exception {
        String form = get("/professional-assessment").body();

        assertThat(form).contains("Upload my CV", "/professional-assessment/cv",
                "multipart/form-data", "Uploading a CV is optional");
    }

    @Test
    void theShellDidNotOpenAnythingBeyondTheInformationalPages() throws Exception {
        // The only routes this phase made public. A POST without a CSRF token must still be rejected,
        // and the informational pages must not have become a way in to anything else.
        assertThat(get("/").statusCode()).isEqualTo(200);
        assertThat(get("/methodology").statusCode()).isEqualTo(200);
        assertThat(get("/privacy").statusCode()).isEqualTo(200);

        for (Journey journey : JOURNEYS) {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER).build()
                    .send(HttpRequest.newBuilder(URI.create(base() + journey.path()))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString("profession=x")).build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s POST without CSRF", journey.path()).isEqualTo(403);
        }
    }

    @Test
    void theInformationalPagesExplainTheLimitsWithoutLeakingHowItIsBuilt() throws Exception {
        String methodology = get("/methodology").body();
        assertThat(methodology).contains("not", "guarantee");
        assertThat(methodology).doesNotContain("OpenAI", "Stripe secret", "sk_", "system prompt",
                "chain of thought", "postgres", "Flyway");

        String privacy = get("/privacy").body();
        assertThat(privacy).contains("not kept");
        assertThat(privacy).doesNotContain("jdbc:", "postgres", "sk_", "secret");
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Accept", "text/html,application/xhtml+xml").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
