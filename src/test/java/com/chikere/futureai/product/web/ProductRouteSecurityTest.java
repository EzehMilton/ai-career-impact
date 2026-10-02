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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The routing and access shape of the whole application, exercised over real HTTP so the security
 * filter chain, Spring MVC and the container's error dispatch all take part.
 *
 * <p>The filter chain ends in {@code permitAll} so that an unknown URL reaches MVC and becomes an
 * ordinary 404 instead of a login redirect. That is only safe because nothing in this application is
 * guarded by authentication: paid reports and PDFs are guarded by an ACTIVE entitlement checked in
 * the application layer. {@link #paidContentIsRefusedByTheApplicationLayerNotByTheFilterChain()} is
 * the evidence for that claim, and it is the reason the catch-all is allowed to be permissive.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductRouteSecurityTest {

    /** A syntactically valid identifier that was never issued, so no entitlement can exist for it. */
    private static final String UNISSUED = "8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c";

    private static final List<String> JOURNEYS =
            List.of("/professional-assessment", "/university-assessment", "/a-level-assessment");

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;
    @Autowired ApplicationContext context;

    @Test
    void anUnknownRouteIs404WithNoLoginRedirectAndNoAuthenticationChallenge() throws Exception {
        for (String path : List.of("/this-route-definitely-does-not-exist", "/admin", "/api/v1/users")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as("%s status", path).isEqualTo(404);
            assertThat(response.headers().firstValue("location")).as("%s redirect", path).isEmpty();
            assertThat(response.headers().firstValue("www-authenticate")).as("%s challenge", path).isEmpty();
            assertThat(response.body()).as("%s body", path)
                    .contains("We couldn't find that page.")
                    .doesNotContain("Whitelabel", "Exception", "java.lang", "at com.chikere");
        }
    }

    @Test
    void thereIsNoGeneratedLoginPageAndNoBasicAuthenticationChallenge() throws Exception {
        for (String path : List.of("/login", "/logout", "/login?error")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as("%s status", path).isEqualTo(404);
            assertThat(response.body()).as("%s body", path)
                    .doesNotContain("Please sign in", "_spring_security", "name=\"username\"",
                            "name=\"password\"", "type=\"password\"");
        }
        // Offering credentials must not be answered with a challenge, because none is ever issued.
        HttpResponse<String> offered = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/"))
                        .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(offered.statusCode()).isEqualTo(200);
        assertThat(offered.headers().firstValue("www-authenticate")).isEmpty();
    }

    @Test
    void theProductsPublicRoutesAreAllStillReachable() throws Exception {
        for (String path : List.of("/", "/methodology", "/privacy",
                "/professional-assessment", "/university-assessment", "/a-level-assessment")) {
            assertThat(get(path).statusCode()).as("%s", path).isEqualTo(200);
        }
    }

    @Test
    void everyBrowserPostStillRequiresACsrfTokenAndIsRefusedWith403() throws Exception {
        List<String> browserPosts = List.of(
                "/professional-assessment",
                "/professional-assessment/cv",
                "/professional-assessment/" + UNISSUED + "/checkout",
                "/professional-assessment/" + UNISSUED + "/checkout/local/confirm",
                "/university-assessment",
                "/university-assessment/" + UNISSUED + "/checkout",
                "/university-assessment/" + UNISSUED + "/checkout/local/confirm",
                "/a-level-assessment",
                "/a-level-assessment/" + UNISSUED + "/checkout",
                "/a-level-assessment/" + UNISSUED + "/checkout/local/confirm");

        for (String path : browserPosts) {
            // 403, never 404: the caller reached a real endpoint and was refused. Disguising a stale
            // form as a missing page would be both untrue and impossible to act on.
            assertThat(post(path).statusCode()).as("%s without CSRF", path).isEqualTo(403);
        }
    }

    @Test
    void theStripeWebhookPathsAreCsrfExemptButNotThereforeTrusted() throws Exception {
        // Outside the live-payment profile the webhook controllers are not registered at all, so the
        // meaningful assertion is that CSRF did not reject the request: the exemption is in force.
        // Signature verification is mandatory and is covered by the three webhook controller tests.
        for (String path : List.of("/payments/stripe/webhook", "/payments/stripe/university-webhook",
                "/payments/stripe/a-level-webhook")) {
            assertThat(post(path).statusCode()).as("%s CSRF exemption", path).isNotEqualTo(403);
        }
    }

    @Test
    void paidContentIsRefusedByTheApplicationLayerNotByTheFilterChain() throws Exception {
        // The filter chain permits these requests, so reaching the controller proves the refusal
        // below comes from the entitlement check rather than from authorisation. 403 and not 404:
        // the resource may well exist, it is simply not unlocked.
        for (String journey : JOURNEYS) {
            assertThat(get(journey + "/" + UNISSUED + "/report").statusCode())
                    .as("%s paid report without entitlement", journey).isEqualTo(403);
            assertThat(get(journey + "/" + UNISSUED + "/report.pdf").statusCode())
                    .as("%s PDF without entitlement", journey).isEqualTo(403);
        }
    }

    @Test
    void aRefusedPdfStillCarriesItsPrivateNoStoreHeaders() throws Exception {
        HttpResponse<String> response = get("/professional-assessment/" + UNISSUED + "/report.pdf");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("cache-control")).get().asString()
                .contains("no-store").contains("private");
    }

    @Test
    void onlyTheHealthEndpointsArePublishedAndNothingDescribesTheInside() throws Exception {
        // Infrastructure has to be able to ask whether the application is up before anyone is
        // signed in, and there is nobody to sign in as. Health is therefore reachable and says
        // only "UP". Everything else Actuator can expose is denied here as well as unexposed.
        HttpResponse<String> health = getJson("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\"")
                .doesNotContain("diskSpace", "db", "components", "ping", "validationQuery");

        for (String path : List.of("/actuator", "/actuator/env", "/actuator/beans",
                "/actuator/configprops", "/actuator/mappings", "/actuator/heapdump",
                "/actuator/threaddump", "/actuator/loggers", "/actuator/conditions",
                "/actuator/metrics", "/actuator/prometheus")) {
            HttpResponse<String> response = getJson(path);

            assertThat(response.statusCode()).as("%s", path).isIn(403, 404);
            assertThat(response.body()).as("%s body", path)
                    .doesNotContain("java.version", "SPRING_DATASOURCE", "secret", "password",
                            "classpath", "jdbc:");
        }
    }

    @Test
    void anOrdinaryPageCarriesTheConfiguredSecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.headers().firstValue("content-security-policy")).get().asString()
                .contains("default-src 'self'").contains("script-src 'none'")
                .contains("frame-ancestors 'none'").contains("object-src 'none'")
                .contains("form-action 'self' https://checkout.stripe.com")
                .doesNotContain("unsafe-inline").doesNotContain("unsafe-eval").doesNotContain("*");
        assertThat(response.headers().firstValue("x-content-type-options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("x-frame-options")).hasValue("DENY");
        assertThat(response.headers().firstValue("referrer-policy"))
                .hasValue("strict-origin-when-cross-origin");
        assertThat(response.headers().firstValue("permissions-policy")).get().asString()
                .contains("camera=()").contains("geolocation=()").contains("microphone=()");
    }

    @Test
    void theStylesheetTheCspAllowsIsActuallyServed() throws Exception {
        // A policy that forbids everything is easy to pass and useless. The one resource the pages
        // genuinely load must still arrive.
        HttpResponse<String> response = get("/css/app.css");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type")).get().asString().contains("css");
    }

    @Test
    void noUsableAccountExistsAndNoPasswordIsGenerated() {
        // Spring Boot creates an in-memory user and prints its generated password whenever the
        // context holds no authentication bean; a SecurityFilterChain alone does not stop that.
        // The replacement must therefore exist, and must be incapable of authenticating anyone.
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).containsExactly("noAccountsExist");
        assertThat(context.getBeanNamesForType(InMemoryUserDetailsManager.class)).isEmpty();
        assertThatThrownBy(() -> context.getBean(UserDetailsService.class).loadUserByUsername("user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void simulatedPaymentRoutesDoNotExistOutsideTheFixtureProfile() throws Exception {
        // This context runs with no profile, so the development-only controllers are never
        // registered. The route is reachable by the filter chain and still has nothing behind it.
        for (String journey : JOURNEYS) {
            HttpResponse<String> response = get(journey + "/" + UNISSUED + "/checkout/local");

            assertThat(response.statusCode()).as("%s local checkout", journey).isEqualTo(404);
            assertThat(response.body()).as("%s local checkout body", journey)
                    .doesNotContain("Simulate payment confirmation", "No real Stripe checkout");
        }
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> getJson(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Accept", "application/json").GET().build());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Accept", "text/html,application/xhtml+xml").GET().build());
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("field=value")).build());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
