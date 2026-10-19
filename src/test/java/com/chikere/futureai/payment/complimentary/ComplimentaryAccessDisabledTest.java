package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.Journey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static com.chikere.futureai.payment.complimentary.ComplimentaryJourneys.CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A code is configured but the flag is off: the feature must disappear, in the page and on the server. */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "app.complimentary-report.enabled=false",
        "app.complimentary-report.code=" + CODE})
class ComplimentaryAccessDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;

    @Test
    void theFormIsHiddenAndTheRouteRefusesEvenTheCorrectCode() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        for (Journey journey : ComplimentaryJourneys.all(context)) {
            UUID id = journey.seedAssessment().get();

            String result = mvc.perform(get(journey.route() + "/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(result).as("%s", journey).doesNotContain("access code", "accessCode",
                    "complimentary", "Unlock full report", CODE);

            String refused = mvc.perform(post(journey.route() + "/" + id + "/complimentary-access").with(csrf())
                            .param("accessCode", CODE))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(refused).as("%s", journey).doesNotContain("access code", CODE);

            assertThat(jdbc.queryForObject("select count(*) from " + journey.entitlementTable()
                    + " where assessment_id = ?", Integer.class, id)).as("%s", journey).isZero();
            mvc.perform(get(journey.route() + "/" + id + "/report")).andExpect(status().isForbidden());
        }
    }
}
