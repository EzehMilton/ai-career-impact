package com.chikere.futureai.product.web;

import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The landing page and the two informational pages, with no development-mode bean present. This is
 * the normal production shape, so no development chrome may appear.
 */
@WebMvcTest(HomeController.class)
@Import(WebSecurityConfiguration.class)
class HomeControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void theHomePageIsPublicAndExplainsWhatTheProductDoes() throws Exception {
        String html = mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(view().name("home")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("AI Career &amp; Future Assessment",
                "How will AI affect your career or education?",
                "the main reasons behind them and a plain-English explanation");
        // The visitor learns the practical terms before starting.
        assertThat(html).contains("no sign-up or email needed", "Your result is saved for later");
        assertThat(html).contains("<title>Understand how AI may affect your choices | AI Career Impact</title>");
    }

    @Test
    void theHomePageOffersTheThreeJourneysInPlainLanguage() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Choose the assessment that fits you.");
        // Each distinct journey carries its question, description and route-specific action.
        assertThat(html).contains("Professional", "How could AI affect my current job or tasks?",
                "We assess the work you describe, using your responsibilities and skills, not just your job title.",
                "Assess my job");
        assertThat(html).contains("University",
                "Is my university course preparing me for an AI-driven future?",
                "Applying?", "Studying?", "Assess a course");
        assertThat(html).contains("A-level", "Which opportunities could my A-level choices open up?",
                "Still deciding?", "Assess my choices");

        assertThat(html).contains("href=\"/professional-assessment\"");
        assertThat(html).contains("href=\"/university-assessment\"");
        assertThat(html).contains("href=\"/a-level-assessment\"");
        // No internal jargon. The sample measures say what each headline measure is not, and the
        // FAQ says scores from different assessment types are not comparable.
        assertThat(html).doesNotContain("journey type", "JourneyType");
        assertThat(html).contains("not the likelihood of losing your job",
                "rather than guaranteeing future employment", "rather than guaranteeing university admission",
                "measure different things and shouldn't be compared");
    }

    @Test
    void theHomePageIsHonestAboutFreePaidAndWhatAScoreIsNot() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Free assessment", "Detailed report", "£1.99", "downloadable PDF",
                "one-off payment");
        assertThat(html).contains("made after you've seen your free result",
                "The written explanation is added afterwards and can’t change them.");
        // How results are produced is stated plainly.
        assertThat(html).contains("Structured reasoning, not a single AI verdict.",
                "It doesn’t decide your overall result.");
        assertThat(html).contains("cannot predict your individual future or guarantee particular outcomes");
        // None of the hype the product deliberately avoids.
        assertThat(html).doesNotContain("AI-proof", "Beat AI", "future-proof", "Future-proof",
                "risk score", "will take your job", "guaranteed");
    }

    @Test
    void developmentOnlyUiIsAbsentWhenNoDevelopmentModeBeanIsPresent() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Development mode", "DEVELOPMENT MODE",
                "Development shortcuts", "fixture", "local-fixture");
    }

    @Test
    void theMethodologyPageExplainsTheApproachWithoutLeakingInternals() throws Exception {
        String html = mvc.perform(get("/methodology")).andExpect(status().isOk())
                .andExpect(view().name("methodology")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("AI makes structured judgments", "Versioned rules produce the scores",
                "AI explains the result afterwards", "External evidence stays separate");
        assertThat(html).contains("provisional product policy",
                "a transparent starting point, not validated educational or economic science");
        assertThat(html).contains("The three assessments are not comparable",
                "Paying adds depth, not a better score");
        // Each journey's headline measure is named with its own caveat.
        assertThat(html).contains("not</em> a probability of job loss");
        assertThat(html).contains("not</em> a guarantee of employability");
        assertThat(html).contains("not</em> a guarantee of university admission");
        // Nothing internal is exposed.
        assertThat(html).doesNotContain("SYSTEM_INSTRUCTIONS", "You explain a completed",
                "api-key", "apiKey", "sk_", "whsec_", "chain-of-thought");
    }

    @Test
    void thePrivacyPageIsTruthfulAboutWhatIsSavedAndShared() throws Exception {
        String html = mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andExpect(view().name("privacy")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("You pay on Stripe's secure checkout page rather than on this site.");
        assertThat(html).contains("do not see or store your card details");
        assertThat(html).contains("There are no accounts.");
        assertThat(html).doesNotContain("we comply with", "GDPR-compliant", "legally binding");
    }

    @Test
    void everyShellPageCarriesConsistentHeaderAndFooterNavigation() throws Exception {
        for (String path : new String[] {"/", "/methodology", "/privacy"}) {
            String html = mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("%s header", path)
                    .contains("class=\"site-header\"", "AI Career Impact home",
                            ">Professional</a>", ">University</a>", ">A-level</a>", ">How this works</a>");
            assertThat(html).as("%s footer", path)
                    .contains("class=\"site-footer\"", "How this works", "Privacy");
            assertThat(html).as("%s accessibility basics", path)
                    .contains("Skip to main content", "id=\"main\"", "<html lang=\"en\">", "<h1");
        }
    }

    @Test
    void theCurrentJourneyIsMarkedInTheHeader() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        // The brand link is both styled and announced as the current page.
        assertThat(html).contains("aria-current=\"page\"", "class=\"site-brand is-current\"");
    }
}