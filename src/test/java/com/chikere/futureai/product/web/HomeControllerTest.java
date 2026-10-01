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

        assertThat(html).contains("AI is changing how people work and what they study");
        assertThat(html).contains("It will not tell you what will happen to you");
        assertThat(html).contains("<title>Think clearly about AI and your future | AI Career Impact</title>");
    }

    @Test
    void theHomePageOffersTheThreeJourneysInPlainLanguage() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("What are you thinking about?");
        // Plain-language framing, then the journey name, question and call to action.
        assertThat(html).contains("My current job", "Professional",
                "How might AI affect the work I do?", "Assess my role");
        assertThat(html).contains("A university course", "University",
                "How well does this course prepare me for an AI-shaped labour market?", "Assess a course");
        assertThat(html).contains("My A-level subjects", "A-level",
                "How many future options do these subjects preserve?", "Assess A-level choices");

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

        assertThat(html).contains("Free assessment", "Full report", "downloadable PDF");
        assertThat(html).contains("Both tiers use exactly the same assessment");
        assertThat(html).contains("is never a guarantee of employment, university admission or career success");
        assertThat(html).contains("indicator");
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
    void everyShellPageCarriesConsistentHeaderAndFooterNavigation() throws Exception {
        for (String path : new String[] {"/", "/methodology", "/privacy"}) {
            String html = mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).as("%s header", path)
                    .contains("class=\"site-header\"", ">AI Career Impact</a>",
                            ">Home</a>", ">Professional</a>", ">University</a>", ">A-level</a>");
            assertThat(html).as("%s footer", path)
                    .contains("class=\"site-footer\"", "How this works", "Your information");
            assertThat(html).as("%s accessibility basics", path)
                    .contains("Skip to main content", "id=\"main\"", "<html lang=\"en\">", "<h1");
        }
    }

    @Test
    void theCurrentJourneyIsMarkedInTheHeader() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        // The home link is both styled and announced as the current page.
        assertThat(html).contains("aria-current=\"page\" class=\"is-current\">Home</a>");
    }
}
