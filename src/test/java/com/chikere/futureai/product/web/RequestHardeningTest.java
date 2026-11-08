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

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Request correlation, cache behaviour and output escaping. These keep the shipped rate limits,
 * because none of them is about throttling; {@link RateLimitTest} turns the allowances down.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestHardeningTest {

    private static final String UNISSUED = "8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c";

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;

    @Test
    void everyResponseCarriesARequestIdEvenWhenTheRequestFailed() throws Exception {
        var browser = new TestBrowser(port);
        for (String path : List.of("/", "/methodology", "/no-such-page")) {
            HttpResponse<String> response = browser.get(path);

            assertThat(response.headers().firstValue("X-Request-ID"))
                    .as("%s request id", path).get().asString().matches("[A-Za-z0-9._-]{8,64}");
        }
    }

    @Test
    void aSensibleInboundRequestIdIsKeptSoACallersTraceSurvives() throws Exception {
        HttpResponse<String> response =
                new TestBrowser(port).getWithHeader("/", "X-Request-ID", "edge-7f3a9c21");

        assertThat(response.headers().firstValue("X-Request-ID")).hasValue("edge-7f3a9c21");
    }

    @Test
    void anUnusableInboundRequestIdIsReplacedRatherThanEchoed() throws Exception {
        // Each of these would end up in a log line and in a response header, so none of them may be
        // reflected: the long one is noise, the rest are not identifiers at all.
        var browser = new TestBrowser(port);
        for (String supplied : List.of("short", "has spaces in it", "x".repeat(200),
                "<script>alert(1)</script>", "../../etc/passwd")) {
            HttpResponse<String> response = browser.getWithHeader("/", "X-Request-ID", supplied);

            assertThat(response.headers().firstValue("X-Request-ID"))
                    .as("%s", supplied).get().asString()
                    .isNotEqualTo(supplied).matches("[A-Za-z0-9._-]{8,64}");
        }
    }

    @Test
    void readingPagesIsNeverThrottled() throws Exception {
        var browser = new TestBrowser(port);
        for (int attempt = 0; attempt < 30; attempt++) {
            assertThat(browser.get("/").statusCode()).isEqualTo(200);
            assertThat(browser.get("/css/app.css").statusCode()).isEqualTo(200);
            assertThat(browser.get("/professional-assessment/" + UNISSUED).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void sensitivePagesAreNeverStoredWhileTheStylesheetMayBeCached() throws Exception {
        var browser = new TestBrowser(port);

        for (String sensitive : List.of("/professional-assessment/" + UNISSUED + "/report",
                "/professional-assessment/" + UNISSUED + "/report.pdf",
                "/university-assessment")) {
            assertThat(browser.get(sensitive).headers().firstValue("Cache-Control"))
                    .as("%s", sensitive).get().asString().contains("no-store");
        }

        // The stylesheet carries no personal data and is the one thing worth letting a browser
        // keep. Spring Security only writes its no-store header when nothing has set one already,
        // so the resource handler's value is what survives here.
        assertThat(browser.get("/css/app.css").headers().firstValue("Cache-Control"))
                .get().asString().contains("max-age=300").doesNotContain("no-store");
    }

    @Test
    void submittedHtmlIsShownBackAsTextRatherThanBeingRendered() throws Exception {
        var browser = new TestBrowser(port);
        String token = browser.csrfTokenFrom("/university-assessment");

        HttpResponse<String> response = browser.postForm("/university-assessment", token,
                "courseTitle", "<script>alert(1)</script><img src=x onerror=alert(1)>");

        // The payload comes back, but only as text: every angle bracket is escaped, so no tag is
        // ever opened. The words "onerror" and "alert" survive inside that text, which is harmless
        // and is why the assertion looks for unopened tags rather than for scary-looking strings.
        assertThat(response.body()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(response.body()).doesNotContain("<img");
        // The only script tags are the application's own same-origin files (the form's loading state); the
        // submitted payload never opens one.
        assertThat(java.util.Arrays.stream(response.body().split("<script", -1)).skip(1))
                .allSatisfy(tag -> assertThat(tag).startsWith(" src=\"/js/"));
    }
}
