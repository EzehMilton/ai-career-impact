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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared error and not-found experience, exercised over real HTTP so the security filter chain
 * and the container's error dispatch both take part. A sliced MockMvc test would bypass both.
 *
 * <p>The JDK HTTP client is used deliberately: it needs no extra test dependency and only ever
 * talks to the local random port.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductErrorPageTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;

    @Test
    void theProductShellPagesArePublicAndCarryTheSharedChrome() throws Exception {
        for (String path : new String[] {"/", "/methodology", "/privacy"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as("%s status", path).isEqualTo(200);
            assertThat(response.body()).as("%s chrome", path)
                    .contains("class=\"site-header\"", "class=\"site-footer\"", "Skip to main content");
        }
    }

    @Test
    void aMissingResourceRendersTheSharedNotFoundPageRatherThanAStackTrace() throws Exception {
        HttpResponse<String> response = get("/css/no-such-file.css");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("We couldn't find that page.", "Back to home",
                "class=\"site-header\"");
        assertThat(response.body()).doesNotContain("Whitelabel", "Exception", "java.lang",
                "at com.chikere", "jdbc:", "postgres", "Hibernate");
    }

    @Test
    void theUnexpectedFailurePageIsSafeAndGeneric() throws Exception {
        HttpResponse<String> response = get("/error");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Something went wrong at our end.", "Back to home");
        assertThat(response.body()).contains("still available from its link");
        assertThat(response.body()).doesNotContain("Whitelabel", "Exception", "java.lang",
                "at com.chikere", "jdbc:", "postgres", "trace");
    }

    @Test
    void anUnknownAssessmentGivesEachJourneysFriendlyNotFoundWithHomeAndStartLinks() throws Exception {
        record Case(String path, String startHref) { }
        var cases = new Case[] {
                new Case("/professional-assessment/", "/professional-assessment"),
                new Case("/university-assessment/", "/university-assessment"),
                new Case("/a-level-assessment/", "/a-level-assessment"),
        };
        for (Case testCase : cases) {
            HttpResponse<String> response = get(testCase.path() + "8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c");

            assertThat(response.statusCode()).as("%s status", testCase.path()).isEqualTo(404);
            assertThat(response.body()).as("%s content", testCase.path())
                    .contains("We couldn't find that assessment.", "Back to home",
                            "href=\"" + testCase.startHref() + "\"", "href=\"/\"");
            assertThat(response.body()).as("%s safety", testCase.path())
                    .doesNotContain("Exception", "java.lang", "jdbc:");
        }
    }

    @Test
    void aMalformedIdentifierIsIndistinguishableFromAnUnknownOne() throws Exception {
        // Nothing may reveal whether the identifier was even well formed, so there is no signal to
        // tell an enumerator that one shape of guess is closer than another.
        String malformed = get("/a-level-assessment/not-a-uuid").body();
        String unknown = get("/a-level-assessment/8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c").body();

        assertThat(malformed).isEqualTo(unknown);
        assertThat(malformed).doesNotContain("UUID", "uuid", "malformed", "not a valid", "not-a-uuid");
    }

    @Test
    void aWhollyUnknownUrlStillFallsThroughToTheAuthenticationEntryPoint() throws Exception {
        // Documented current behaviour, not an endorsement: `anyRequest().authenticated()` is still in
        // force, so an unmatched path redirects to the form-login entry point instead of showing the
        // friendly 404. Every route a visitor can actually reach is permitted and renders the shell.
        // Deciding the final authorisation model belongs to the security-hardening phase; this test
        // exists so that change is a deliberate one rather than an accident.
        HttpResponse<String> response = get("/no-such-page");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).get().asString().endsWith("/login");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", "text/html,application/xhtml+xml").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
