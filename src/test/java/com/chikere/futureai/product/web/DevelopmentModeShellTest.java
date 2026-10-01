package com.chikere.futureai.product.web;

import com.chikere.futureai.product.application.DevelopmentModeStatus;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same pages with an active development-mode bean, as under the local-fixture profile.
 *
 * <p>This is the mirror of {@code HomeControllerTest}: the development chrome must appear here and
 * must be absent there, and nothing about the request can switch between the two.</p>
 */
@WebMvcTest(HomeController.class)
@Import({WebSecurityConfiguration.class, DevelopmentModeShellTest.ActiveDevelopmentMode.class})
class DevelopmentModeShellTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ActiveDevelopmentMode {
        @Bean
        DevelopmentModeStatus developmentModeStatus() {
            return DevelopmentModeStatus.active("professional: electrician · university: nursing "
                    + "· a-level: english_history_politics");
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void theDevelopmentBannerNamesTheActiveFixtureForEveryJourney() throws Exception {
        String html = mvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("class=\"dev-banner\"", "Development mode");
        assertThat(html).contains("Fixture data and simulated payment. No live AI, evidence or "
                + "payment provider is contacted.");
        // Which fixture is active is always stated, so development output is never ambiguous.
        assertThat(html).contains("professional: electrician", "university: nursing",
                "a-level: english_history_politics");
    }

    @Test
    void developmentShortcutsAppearOnlyAsAClearlySeparatedDevelopmentSection() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Development shortcuts", "class=\"panel dev-section results-section\"");
        assertThat(html).contains("only under the <code>local-fixture</code> profile");
        // No database internals or ready-made assessment identifiers are ever exposed.
        assertThat(html).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(html).doesNotContain("jdbc:", "postgres", "schema", "sk_test", "whsec_");
    }

    @Test
    void theBannerAppearsOnTheInformationalPagesToo() throws Exception {
        for (String path : new String[] {"/methodology", "/privacy"}) {
            String html = mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as("%s banner", path).contains("class=\"dev-banner\"", "Development mode");
            // The shortcut section belongs to the home page only.
            assertThat(html).as("%s shortcuts", path).doesNotContain("Development shortcuts");
        }
    }
}
