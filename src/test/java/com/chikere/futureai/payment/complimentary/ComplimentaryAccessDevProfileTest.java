package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.Journey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@Import({TestcontainersConfiguration.class, ComplimentaryAccessFlowTestSupport.RecordGatewayCalls.class})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "app.complimentary-report.enabled=true",
        "app.complimentary-report.code=" + ComplimentaryJourneys.CODE})
class ComplimentaryAccessDevProfileTest extends ComplimentaryAccessFlowTestSupport {

    /** The dev profile can pay end to end locally, so compare against the real checkout path too. */
    @Test
    void theReportFromTheLocalCheckoutPathMatchesTheComplimentaryReport() throws Exception {
        for (Journey journey : journeys) {
            UUID paid = journey.seedAssessment().get();
            UUID complimentary = journey.seedAssessment().get();
            mvc.perform(post(journey.route() + "/" + paid + "/checkout").with(csrf()));
            String session = (String) entitlement(journey, paid).get("provider_checkout_session_id");
            mvc.perform(post(journey.route() + "/" + paid + "/checkout/local/confirm").with(csrf())
                            .param("session", session))
                    .andExpect(redirectedUrl(journey.route() + "/" + paid + "/report"));
            redeem(journey, complimentary, ComplimentaryJourneys.CODE);

            assertThat(entitlement(journey, paid)).as("%s", journey).containsEntry("payment_provider", "LOCAL_FIXTURE");
            assertThat(journey.purchasedReport().apply(complimentary)).as("%s", journey).isPresent()
                    .isEqualTo(journey.purchasedReport().apply(paid));
        }
        // The recorder sees real gateway traffic, so its silence elsewhere means something.
        assertThat(gatewayCalls.calls()).contains("LocalFixtureProfessionalPaymentGateway.createCheckoutSession",
                "LocalFixtureUniversityPaymentGateway.createCheckoutSession",
                "LocalFixtureALevelPaymentGateway.createCheckoutSession");
    }
}
