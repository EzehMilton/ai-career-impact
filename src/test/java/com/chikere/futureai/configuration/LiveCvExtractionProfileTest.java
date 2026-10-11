package com.chikere.futureai.configuration;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.cv.professional.application.ProfessionalProfileExtractor;
import com.chikere.futureai.cv.professional.fixture.LocalFixtureProfessionalProfileExtractor;
import com.chikere.futureai.cv.professional.openai.OpenAiProfessionalProfileExtractor;
import com.chikere.futureai.evidence.professional.application.EvidenceSource;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "OPENAI_API_KEY=test-only-not-a-real-key",
        "spring.ai.openai.api-key=test-only-not-a-real-key"
})
@ActiveProfiles({"local-fixture", "live-cv-extraction"})
class LiveCvExtractionProfileTest {

    @Autowired ApplicationContext context;
    @Autowired ProfessionalProfileExtractor profileExtractor;
    @Autowired ProfessionalJudgmentProvider judgmentProvider;
    @Autowired ProfessionalNarrativeGenerator narrativeGenerator;
    @Autowired ProfessionalEvidenceProvider evidenceProvider;
    @Autowired ProfessionalPaymentGateway paymentGateway;

    // The extractor is built at startup, so build() must already return a ChatClient mock then.
    @MockitoBean(answers = Answers.RETURNS_MOCKS) ChatClient.Builder openAiChatClientBuilder;

    @Test
    void onlyCvExtractionIsLiveAndEveryDownstreamProviderRemainsFixtureBacked() {
        assertThat(profileExtractor).isInstanceOf(OpenAiProfessionalProfileExtractor.class);
        assertThat(context.getBeansOfType(ProfessionalProfileExtractor.class)).hasSize(1);
        assertThat(context.getBeansOfType(LocalFixtureProfessionalProfileExtractor.class)).isEmpty();

        assertThat(judgmentProvider).isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
        assertThat(judgmentProvider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(narrativeGenerator).isInstanceOf(LocalFixtureProfessionalNarrativeGenerator.class);
        assertThat(narrativeGenerator.source()).isEqualTo(NarrativeSource.LOCAL_FIXTURE);
        assertThat(evidenceProvider).isInstanceOf(LocalFixtureProfessionalEvidenceProvider.class);
        assertThat(evidenceProvider.source()).isEqualTo(EvidenceSource.LOCAL_FIXTURE);
        assertThat(paymentGateway).isInstanceOf(LocalFixtureProfessionalPaymentGateway.class);

        assertThat(context.getBeansOfType(TypeSafeClient.class)).isEmpty();
        assertThat(context.getBeansOfType(NomisProfessionalEvidenceProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(StripeProfessionalPaymentGateway.class)).isEmpty();
    }
}
