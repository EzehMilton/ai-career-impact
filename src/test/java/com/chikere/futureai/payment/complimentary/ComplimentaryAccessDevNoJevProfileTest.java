package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.TestcontainersConfiguration;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real Stripe gateway beans are present here; the recorder proves redemption never calls one. */
@Import({TestcontainersConfiguration.class, ComplimentaryAccessFlowTestSupport.RecordGatewayCalls.class})
@AutoConfigureMockMvc
@ActiveProfiles("dev-no-jev")
@SpringBootTest(properties = {
        "app.complimentary-report.enabled=true",
        "app.complimentary-report.code=" + ComplimentaryJourneys.CODE,
        "spring.ai.model.chat=openai",
        "OPENAI_API_KEY=test-only-not-a-real-key",
        "spring.ai.openai.api-key=test-only-not-a-real-key",
        "app.stripe.secret-key=sk_test_not_called",
        "app.stripe.webhook-secret=whsec_not_called",
        "app.stripe.university-webhook-secret=whsec_not_called",
        "app.stripe.a-level-webhook-secret=whsec_not_called",
        "app.professional.report.stripe-price-id=price_not_called",
        "app.university.report.stripe-price-id=price_not_called",
        "app.a-level.report.stripe-price-id=price_not_called"})
class ComplimentaryAccessDevNoJevProfileTest extends ComplimentaryAccessFlowTestSupport {
    // The OpenAI clients are built at startup; nothing here generates a narrative.
    @MockitoBean(answers = Answers.RETURNS_MOCKS) ChatClient.Builder openAiChatClientBuilder;
}
