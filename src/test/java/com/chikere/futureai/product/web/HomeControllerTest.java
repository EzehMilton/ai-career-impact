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
                "Understand how AI may affect your work, course, or subject choices.",
                "clear explanations and practical next steps");
        assertThat(html).contains("<title>Understand how AI may affect your choices | AI Career Impact</title>");
    }

    @Test
    void theHomePageOffersTheThreeJourneysInPlainLanguage() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Choose the question that matters to you.");
        // Each distinct journey carries its question, description and route-specific action.
        assertThat(html).contains("Professional", "How will AI affect the work I currently do?",
                "where human strengths remain", "Assess my work");
        assertThat(html).contains("University",
                "How well does this course position me for an AI-shaped labour market?",
                "transferable, human and AI-complementary capabilities", "Assess a course");
        assertThat(html).contains("A-level", "Do these choices preserve strong future options?",
                "future study pathways, transferable skills and adaptability", "Assess my choices");

        assertThat(html).contains("href=\"/professional-assessment\"");
        assertThat(html).contains("href=\"/university-assessment\"");
        assertThat(html).contains("href=\"/a-level-assessment\"");
        // No jargon, and no technical measure names on the landing page.
        assertThat(html).doesNotContain("journey type", "JourneyType", "AI Exposure", "Future Readiness",
                "Future Optionality", "Human Advantage", "Skill Transferability");
    }

    @Test
    void theHomePageIsHonestAboutFreePaidAndWhatAScoreIsNot() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Free assessment", "Deeper report", "£4.99", "Downloadable PDF");
        assertThat(html).contains("The paid report uses the same assessment. Payment adds depth and explanation; it does not change or re-run your result.");
        assertThat(html).contains("These assessments describe characteristics of the work, course or choices you tell us about. They do not predict your personal outcome.");
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
        assertThat(html).contains("the file itself and the extracted text are not kept");
        assertThat(html).contains("Card details are entered on Stripe's own hosted checkout page");
        assertThat(html).contains("There are no accounts yet");
        assertThat(html).doesNotContain("we comply with", "GDPR-compliant", "legally binding");
    }

    @Test
    void thePrivacyPageSaysContactDetailsAreRemovedBeforeCvTextIsSent() throws Exception {
        String html = mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().replaceAll("\\s+", " ");

        assertThat(html).contains("Before your CV text is sent to our AI provider, we remove email addresses, "
                + "phone numbers, web links and postcodes. Other details in your CV, including your name, may "
                + "still be sent.");
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
