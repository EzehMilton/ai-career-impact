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
        assertThat(html).contains("no sign-up or email needed", "saved and won't change later");
        assertThat(html).contains("<title>Understand how AI may affect your choices | AI Career Impact</title>");
    }

    @Test
    void theHomePageOffersTheThreeJourneysInPlainLanguage() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Choose the question that matters to you.");
        // Each distinct journey carries its question, description and route-specific action.
        assertThat(html).contains("Professional", "How could AI affect my current job or tasks?",
                "what you actually do, not just the title", "Assess my job");
        assertThat(html).contains("University",
                "Is my university course preparing me for an AI-driven future?",
                "Already studying?", "not you or your university", "Assess a course");
        assertThat(html).contains("A-level", "Which opportunities could my A-level choices open up?",
                "Just your subjects", "Assess my choices");

        assertThat(html).contains("href=\"/professional-assessment\"");
        assertThat(html).contains("href=\"/university-assessment\"");
        assertThat(html).contains("href=\"/a-level-assessment\"");
        // No internal jargon. Measure names now appear deliberately, each with a plain-English
        // definition and what it is not, so visitors know what they will receive.
        assertThat(html).doesNotContain("journey type", "JourneyType");
        assertThat(html).contains("Not a probability of job loss.", "Not a guarantee of employability.",
                "A focused combination can be the right choice.",
                "so their scores aren't comparable with each other");
    }

    @Test
    void theHomePageIsHonestAboutFreePaidAndWhatAScoreIsNot() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Free assessment", "Detailed report", "£1.99", "downloadable PDF",
                "one-off payment");
        assertThat(html).contains("Your scores are the same either way. Paying adds depth and explanation. "
                + "It doesn't buy a more accurate or more favourable result.");
        // How results are produced is stated plainly, including the limits of the weights.
        assertThat(html).contains("It never gives an overall verdict.",
                "a transparent starting point, not validated economic or educational science");
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

        assertThat(html).contains("Structured AI judgments", "Deterministic policy in code",
                "A written explanation", "External context, kept separate");
        assertThat(html).contains("provisional product policy");
        assertThat(html).contains("Why the three assessments are not comparable");
        // Each journey's headline measure is named with its own caveat.
        assertThat(html).contains("not</em> a probability of job loss");
        assertThat(html).contains("not</em> a guarantee of employability");
        assertThat(html).contains("not</em> a guarantee of university admission");
        // Nothing internal is exposed.
        assertThat(html).doesNotContain("SYSTEM_INSTRUCTIONS", "You explain a completed",
                "api-key", "apiKey", "sk_", "whsec_", "chain-of-thought");
    }

    @Test
    void thePrivacyPageIsTruthfulAndLabelledAsInformationNotPolicy() throws Exception {
        String html = mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andExpect(view().name("privacy")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("This is a product information notice, not a legal privacy policy");
        assertThat(html).contains("Card details are entered on Stripe's hosted checkout page");
        assertThat(html).contains("There are currently no user accounts");
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