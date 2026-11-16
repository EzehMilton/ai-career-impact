package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.GatewayCallRecorder;
import com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.Journey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Complimentary access end to end, against a migrated PostgreSQL database and the real filter chain,
 * for all three journeys. Each subclass runs these under one profile with the feature enabled, which
 * is what shows the flag rather than the profile decides it.
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class ComplimentaryAccessFlowTestSupport {
    static final String INVALID_CODE_MESSAGE = "That access code is not valid.";

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired GatewayCallRecorder gatewayCalls;

    List<Journey> journeys;
    JdbcTemplate jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordGatewayCalls {
        @Bean
        static GatewayCallRecorder gatewayCallRecorder() {
            return new GatewayCallRecorder();
        }
    }

    @BeforeEach
    void prepare() {
        journeys = ComplimentaryJourneys.all(context);
        jdbc = new JdbcTemplate(dataSource);
        gatewayCalls.clear();
    }

    @Test
    void theFreeResultPageOffersTheAccessCodeFormButNeverTheCode() throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();
            String html = mvc.perform(get(journey.route() + "/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("%s", journey).contains("Have a beta access code?", "Unlock full report",
                    "name=\"accessCode\"", "action=\"" + journey.route() + "/" + id + "/complimentary-access\"",
                    "Enter it to unlock your detailed report.",
                    "aria-describedby=\"accessCode-hint\"",
                    "<button type=\"submit\" class=\"button-secondary\">Unlock full report</button>")
                    .doesNotContain("Paid report checkout is not currently available.");
            assertThat(html).as("%s", journey).doesNotContain(CODE);
        }
    }

    @Test
    void aValidCodeUnlocksTheFullReportWithoutStripeOrAWebhook(CapturedOutput output) throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();

            redeem(journey, id, CODE).andExpect(redirectedUrl(journey.route() + "/" + id + "/report"));

            String report = mvc.perform(get(journey.route() + "/" + id + "/report")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(report).as("%s", journey).doesNotContain(CODE);
            assertThat(journey.purchasedReport().apply(id)).as("%s", journey).isPresent();
            assertThat(entitlement(journey, id)).as("%s", journey)
                    .containsEntry("payment_provider", "COMPLIMENTARY").containsEntry("status", "ACTIVE");
            assertThat(count(journey.snapshotTable(), id)).as("%s", journey).isEqualTo(1);
            assertThat(paymentEvents(journey, id)).as("%s: no webhook event", journey).isZero();
        }
        assertThat(gatewayCalls.calls()).as("payment gateway calls").isEmpty();
        assertThat(output.getAll()).contains("complimentary access assessmentId=", "outcome=UNLOCKED")
                .doesNotContain(CODE);
    }

    @Test
    void anInvalidCodeUnlocksNothing(CapturedOutput output) throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();

            for (String wrong : List.of("not-the-code", CODE.toUpperCase(), CODE + "x", "", " ")) {
                redeem(journey, id, wrong).andExpect(redirectedUrl(journey.route() + "/" + id))
                        .andExpect(flash().attribute("complimentaryMessage", INVALID_CODE_MESSAGE));
            }

            assertThat(count(journey.entitlementTable(), id)).as("%s", journey).isZero();
            mvc.perform(get(journey.route() + "/" + id + "/report")).andExpect(status().isForbidden());
            String html = mvc.perform(get(journey.route() + "/" + id)
                            .flashAttr("complimentaryMessage", INVALID_CODE_MESSAGE))
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as("%s", journey).contains(INVALID_CODE_MESSAGE, "Have a beta access code?")
                    .doesNotContain(CODE);
        }
        assertThat(output.getAll()).doesNotContain(CODE, "not-the-code");
    }

    @Test
    void redeemingTheSameAssessmentAgainChangesNothing() throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();
            redeem(journey, id, CODE);
            Map<String, Object> first = entitlement(journey, id);
            Object frozenAt = frozenAt(journey, id);

            redeem(journey, id, CODE).andExpect(redirectedUrl(journey.route() + "/" + id + "/report"));

            assertThat(count(journey.entitlementTable(), id)).as("%s", journey).isEqualTo(1);
            assertThat(count(journey.snapshotTable(), id)).as("%s", journey).isEqualTo(1);
            assertThat(entitlement(journey, id)).as("%s", journey).isEqualTo(first);
            assertThat(frozenAt(journey, id)).as("%s: report not regenerated", journey).isEqualTo(frozenAt);
        }
    }

    @Test
    void oneSharedCodeUnlocksAnyNumberOfDifferentAssessments() throws Exception {
        for (Journey journey : journeys) {
            Set<Object> entitlementIds = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                UUID id = journey.seedAssessment().get();
                redeem(journey, id, CODE).andExpect(redirectedUrl(journey.route() + "/" + id + "/report"));
                entitlementIds.add(entitlement(journey, id).get("entitlement_id"));
            }
            assertThat(entitlementIds).as("%s", journey).hasSize(5);
        }
    }

    @Test
    void anActiveStripePurchaseIsNeitherDuplicatedNorRefrozen() throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();
            journey.seedActiveStripePurchase().accept(id);
            Map<String, Object> paid = entitlement(journey, id);
            Object frozenAt = frozenAt(journey, id);

            redeem(journey, id, CODE).andExpect(redirectedUrl(journey.route() + "/" + id + "/report"));

            assertThat(count(journey.entitlementTable(), id)).as("%s", journey).isEqualTo(1);
            assertThat(entitlement(journey, id)).as("%s", journey).isEqualTo(paid)
                    .containsEntry("payment_provider", "STRIPE");
            assertThat(frozenAt(journey, id)).as("%s", journey).isEqualTo(frozenAt);
        }
        assertThat(gatewayCalls.calls()).isEmpty();
    }

    @Test
    void aCheckoutInProgressIsNeverOverriddenByACode() throws Exception {
        for (Journey journey : journeys) {
            UUID id = journey.seedAssessment().get();
            journey.seedPendingStripeCheckout().accept(id);

            String html = mvc.perform(get(journey.route() + "/" + id)).andReturn().getResponse().getContentAsString();
            assertThat(html).as("%s", journey).doesNotContain("Have a beta access code?");
            redeem(journey, id, CODE).andExpect(redirectedUrl(journey.route() + "/" + id))
                    .andExpect(flash().attribute("complimentaryMessage",
                            "An access code cannot be used for this assessment."));

            assertThat(entitlement(journey, id)).as("%s", journey)
                    .containsEntry("payment_provider", "STRIPE").containsEntry("status", "PENDING");
        }
        assertThat(gatewayCalls.calls()).isEmpty();
    }

    @Test
    void complimentaryAndPayingCustomersReceiveTheSameFullReport() throws Exception {
        for (Journey journey : journeys) {
            UUID paid = journey.seedAssessment().get();
            UUID complimentary = journey.seedAssessment().get();
            journey.seedActiveStripePurchase().accept(paid);
            redeem(journey, complimentary, CODE);

            assertThat(journey.purchasedReport().apply(complimentary)).as("%s", journey).isPresent()
                    .isEqualTo(journey.purchasedReport().apply(paid));
            assertThat(reportPage(journey, complimentary).replace(complimentary.toString(), "<id>"))
                    .as("%s", journey).isEqualTo(reportPage(journey, paid).replace(paid.toString(), "<id>"));
        }
    }

    ResultActions redeem(Journey journey, UUID id, String code) throws Exception {
        return mvc.perform(post(journey.route() + "/" + id + "/complimentary-access").with(csrf())
                .param("accessCode", code)).andExpect(status().is3xxRedirection());
    }

    /** The report page, with per-request CSRF token values normalised (a form token is unique to each response). */
    String reportPage(Journey journey, UUID id) throws Exception {
        return mvc.perform(get(journey.route() + "/" + id + "/report")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\" value=\"<token>\"");
    }

    Map<String, Object> entitlement(Journey journey, UUID id) {
        return jdbc.queryForMap("select * from " + journey.entitlementTable() + " where assessment_id = ?", id);
    }

    Object frozenAt(Journey journey, UUID id) {
        return jdbc.queryForObject("select frozen_at from " + journey.snapshotTable() + " where assessment_id = ?",
                Object.class, id);
    }

    int count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where assessment_id = ?", Integer.class, id);
    }

    int paymentEvents(Journey journey, UUID id) {
        return jdbc.queryForObject("select count(*) from " + journey.eventTable() + " e join "
                + journey.entitlementTable() + " t on t.entitlement_id = e.entitlement_id where t.assessment_id = ?",
                Integer.class, id);
    }
}
