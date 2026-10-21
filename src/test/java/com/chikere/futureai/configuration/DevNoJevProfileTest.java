package com.chikere.futureai.configuration;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.evidence.professional.application.EvidenceSource;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.fixture.LocalFixtureProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.stripe.StripeProfessionalPaymentGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev-no-jev profile must use real OpenAI narrative while keeping
 * fixture Jev judgments, fixture evidence and real Stripe payment. No TypeSafe client must exist.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "OPENAI_API_KEY=test-only-not-a-real-key",
        "spring.ai.openai.api-key=test-only-not-a-real-key",
        "app.stripe.secret-key=sk_test_not_called",
        "app.stripe.webhook-secret=whsec_not_called",
        "app.stripe.university-webhook-secret=whsec_not_called",
        "app.stripe.a-level-webhook-secret=whsec_not_called",
        "app.professional.report.stripe-price-id=price_not_called",
        "app.university.report.stripe-price-id=price_not_called",
        "app.a-level.report.stripe-price-id=price_not_called",
        "app.professional.fixture-id=senior_software_architect",
        "app.university.fixture-id=computer_science",
        "app.a-level.fixture-id=maths_economics_geography"
})
@ActiveProfiles("dev-no-jev")
class DevNoJevProfileTest {

    @Autowired ApplicationContext context;
    @Autowired ProfessionalJudgmentProvider judgmentProvider;
    @Autowired ProfessionalNarrativeGenerator narrativeGenerator;
    @Autowired ProfessionalEvidenceProvider evidenceProvider;
    @Autowired ProfessionalPaymentGateway paymentGateway;

    // The narrative generators are built at startup, so build() must already return a ChatClient mock then.
    @MockitoBean(answers = Answers.RETURNS_MOCKS) ChatClient.Builder openAiChatClientBuilder;

    @Test
    void narrativeIsRealOpenAiWhileJevAndEvidenceRemainFixtureBacked() {
        assertThat(judgmentProvider).isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
        assertThat(judgmentProvider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);

        assertThat(narrativeGenerator).isInstanceOf(OpenAiProfessionalNarrativeGenerator.class);
        assertThat(narrativeGenerator.source()).isEqualTo(NarrativeSource.OPENAI);
        assertThat(context.getBeansOfType(LocalFixtureProfessionalNarrativeGenerator.class)).isEmpty();

        assertThat(evidenceProvider).isInstanceOf(LocalFixtureProfessionalEvidenceProvider.class);
        assertThat(evidenceProvider.source()).isEqualTo(EvidenceSource.LOCAL_FIXTURE);

        assertThat(paymentGateway).isInstanceOf(StripeProfessionalPaymentGateway.class);

        assertThat(context.getBeansOfType(TypeSafeClient.class)).isEmpty();
        assertThat(context.getBeansOfType(NomisProfessionalEvidenceProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(LocalFixtureProfessionalPaymentGateway.class)).isEmpty();
    }
}
