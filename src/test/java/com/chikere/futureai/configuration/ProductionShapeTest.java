package com.chikere.futureai.configuration;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.product.application.DevelopmentModeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application started the way production starts it.
 *
 * <p>The judgment and narrative providers are replaced with mocks, because the real ones are the
 * live Jev and OpenAI adapters and this test makes no external call. Everything else — the profile,
 * the property file, the filter chain, Actuator, the cookie and proxy settings — is the real
 * production configuration, so the shape being asserted is the shape that would be deployed.</p>
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.public-base-url=https://ai-career-impact.example",
        "TYPESAFE_API_KEY=test-only-jev-key",
        "OPENAI_API_KEY=test-only-openai-key",
        "spring.ai.openai.decision.api-key=test-only-openai-key",
        "spring.ai.model.chat=none",
        "app.stripe.secret-key=sk_test_not_called",
        "app.stripe.webhook-secret=whsec_not_called",
        "app.stripe.university-webhook-secret=whsec_not_called",
        "app.stripe.a-level-webhook-secret=whsec_not_called",
        "app.professional.report.stripe-price-id=price_not_called",
        "app.university.report.stripe-price-id=price_not_called",
        "app.a-level.report.stripe-price-id=price_not_called",
        "app.a-level.presentation-v3-cutover=2026-11-20T09:30:00Z",
        "app.professional.evidence.classified-profession=Software Developer",
        "app.professional.evidence.canonical-occupation=Programmers and software development professionals",
        "app.professional.evidence.soc2020-code=2134"})
class ProductionShapeTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @Autowired ApplicationContext context;
    @Autowired Environment environment;
    @LocalServerPort int port;

    @Test
    void noFixtureBeanExistsAnywhereInTheContext() {
        // Not "is hidden" but "does not exist": a fixture result can never be served in production
        // because there is nothing there to serve one.
        assertThat(context.getBeanDefinitionNames())
                .filteredOn(name -> name.toLowerCase().contains("fixture"))
                .isEmpty();
    }

    @Test
    void noSimulatedPaymentControllerIsRegistered() {
        assertThat(context.getBeanDefinitionNames())
                .filteredOn(name -> name.toLowerCase().contains("localprofessionalcheckout")
                        || name.toLowerCase().contains("localuniversitycheckout")
                        || name.toLowerCase().contains("localalevelcheckout")
                        || name.toLowerCase().contains("paymentsimulator"))
                .isEmpty();
    }

    @Test
    void developmentModeIsInactiveAndNoPageAdvertisesIt() throws Exception {
        assertThat(context.getBean(DevelopmentModeStatus.class).active()).isFalse();

        String home = get("/").body();
        assertThat(home).doesNotContain("Development mode", "Development shortcuts", "local-fixture");
    }

    @Test
    void theSimulatedCheckoutRoutesAreNotServed() throws Exception {
        String unissued = "8c7b6a59-4d3e-4f2a-8b1c-0d9e8f7a6b5c";
        for (String journey : List.of("/professional-assessment", "/university-assessment",
                "/a-level-assessment")) {
            HttpResponse<String> response = get(journey + "/" + unissued + "/checkout/local");

            assertThat(response.statusCode()).as("%s", journey).isEqualTo(404);
            assertThat(response.body()).doesNotContain("Simulate payment confirmation");
        }
    }

    @Test
    void theProductStillWorksInProductionShape() throws Exception {
        // A hardened application that cannot serve its own pages is not hardened, it is broken.
        for (String path : List.of("/", "/methodology", "/privacy", "/professional-assessment",
                "/university-assessment", "/a-level-assessment")) {
            assertThat(get(path).statusCode()).as("%s", path).isEqualTo(200);
        }
    }

    @Test
    void secureCookieAndProxySettingsAreInEffect() {
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site"))
                .isEqualTo("lax");
        assertThat(environment.getProperty("server.forward-headers-strategy"))
                .isEqualTo("framework");
    }

    @Test
    void healthAndItsProbesAreAvailableToInfrastructure() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness")) {
            HttpResponse<String> response = getJson(path);

            assertThat(response.statusCode()).as("%s", path).isEqualTo(200);
            assertThat(response.body()).as("%s", path).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void healthRevealsOnlyAStatusAndNeverTheComponentsBehindIt() throws Exception {
        String health = getJson("/actuator/health").body();

        assertThat(health).doesNotContain("components", "diskSpace", "database", "PostgreSQL",
                "validationQuery", "jdbc", "free", "total");
    }

    @Test
    void livenessDoesNotDependOnAnyExternalProvider() {
        // A provider outage must not look like a dead process, or an orchestrator will restart
        // healthy containers in a loop while the outage lasts.
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
                .contains("db")
                .doesNotContain("openai", "stripe", "typesafe", "nomis");
    }

    @Test
    void nothingThatDescribesTheInsideOfTheApplicationIsReachable() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/heapdump", "/actuator/threaddump",
                "/actuator/loggers", "/actuator/conditions", "/actuator/metrics",
                "/actuator/prometheus")) {
            HttpResponse<String> response = getJson(path);

            assertThat(response.statusCode()).as("%s", path).isIn(403, 404);
            assertThat(response.body()).as("%s", path)
                    .doesNotContain("SPRING_DATASOURCE", "jdbc:", "password", "api-key", "secret");
        }
    }

    @Test
    void the17ASecurityShapeIsUnchangedInProduction() throws Exception {
        assertThat(get("/no-such-page").statusCode()).isEqualTo(404);
        assertThat(get("/login").statusCode()).isEqualTo(404);
        assertThat(get("/no-such-page").headers().firstValue("www-authenticate")).isEmpty();
        assertThat(get("/").headers().firstValue("content-security-policy")).isPresent();

        HttpResponse<String> withoutCsrf = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/professional-assessment"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("profession=x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(withoutCsrf.statusCode()).isEqualTo(403);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(path, "text/html,application/xhtml+xml");
    }

    private HttpResponse<String> getJson(String path) throws IOException, InterruptedException {
        return send(path, "application/json");
    }

    private HttpResponse<String> send(String path, String accept)
            throws IOException, InterruptedException {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Accept", accept).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
