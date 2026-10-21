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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throttling behaviour, with the allowances turned right down so a limit can be reached in a few
 * requests.
 *
 * <p>The limiter keys on the client address, so every test in this class shares one allowance per
 * group. Each test therefore uses a <em>different</em> group — one submits assessments, the other
 * starts checkouts — rather than relying on execution order.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.assessment-submission.permits=2",
        "app.rate-limit.checkout-start.permits=1",
        "app.rate-limit.window=PT5M"})
class RateLimitTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;

    @Test
    void submissionsBeyondTheAllowanceGetASafe429WithARetryAfter() throws Exception {
        var browser = new TestBrowser(port);
        String token = browser.csrfTokenFrom("/professional-assessment");

        // Deliberately invalid submissions: they redisplay the form, so this measures the limiter
        // and never reaches a judgment provider.
        assertThat(browser.postForm("/professional-assessment", token).statusCode()).isEqualTo(200);
        assertThat(browser.postForm("/professional-assessment", token).statusCode()).isEqualTo(200);

        HttpResponse<String> throttled = browser.postForm("/professional-assessment", token);

        assertThat(throttled.statusCode()).isEqualTo(429);
        assertThat(throttled.headers().firstValue("Retry-After")).get().asString().matches("\\d+");
        assertThat(throttled.body())
                .contains("You've made a lot of requests just now.", "Back to home")
                .doesNotContain("bucket", "permits", "RateLimit", "Exception", "java.lang",
                        "Whitelabel");
    }

    @Test
    void anExhaustedAllowanceStillAnswersAMissingCsrfTokenWith403() throws Exception {
        // Security runs before the limiter, so a stale or broken form never spends an allowance and
        // is never told it is being throttled when the real problem is its token.
        var browser = new TestBrowser(port);
        String token = browser.csrfTokenFrom("/professional-assessment");
        String checkout = "/professional-assessment/8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c/checkout";
        browser.postForm(checkout, token);
        assertThat(browser.postForm(checkout, token).statusCode())
                .as("the checkout-start allowance is now spent").isEqualTo(429);

        HttpResponse<String> withoutToken = browser.postForm(checkout, null);

        assertThat(withoutToken.statusCode()).isEqualTo(403);
    }
}
