package com.chikere.futureai.configuration;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.payment.alevel.stripe.StripeALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.web.ALevelStripeWebhookController;
import com.chikere.futureai.payment.professional.stripe.StripeProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.web.StripeWebhookController;
import com.chikere.futureai.payment.university.stripe.StripeUniversityPaymentGateway;
import com.chikere.futureai.payment.university.web.UniversityStripeWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/** The commercial production capability still wires every Stripe product when explicitly enabled. */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "app.public-base-url=https://ai-career-impact.example",
        "OPENAI_API_KEY=test-only-openai-key",
        "spring.ai.openai.decision.api-key=test-only-openai-key",
        "spring.ai.model.chat=none",
        "app.stripe.enabled=true",
        "app.stripe.secret-key=sk_test_not_called",
        "app.stripe.webhook-secret=whsec_not_called",
        "app.stripe.university-webhook-secret=whsec_not_called",
        "app.stripe.a-level-webhook-secret=whsec_not_called",
        "app.professional.report.stripe-price-id=price_professional_not_called",
        "app.university.report.stripe-price-id=price_university_not_called",
        "app.a-level.report.stripe-price-id=price_a_level_not_called",
        "app.professional.evidence.live-enabled=false"})
class StripeEnabledProductionShapeTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @Autowired ApplicationContext context;

    @Test
    void allGatewaysAndWebhookControllersExistOnlyAfterExplicitEnablement() {
        assertThat(context.getBeansOfType(StripeProfessionalPaymentGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(StripeUniversityPaymentGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(StripeALevelPaymentGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(StripeWebhookController.class)).hasSize(1);
        assertThat(context.getBeansOfType(UniversityStripeWebhookController.class)).hasSize(1);
        assertThat(context.getBeansOfType(ALevelStripeWebhookController.class)).hasSize(1);
    }
}
