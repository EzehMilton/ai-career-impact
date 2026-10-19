package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The production configuration, with only the live Jev and OpenAI adapters replaced. */
@Import({TestcontainersConfiguration.class, ComplimentaryAccessFlowTestSupport.RecordGatewayCalls.class})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "app.complimentary-report.enabled=true",
        "app.complimentary-report.code=" + ComplimentaryJourneys.CODE,
        "app.public-base-url=https://ai-career-impact.example",
        "TYPESAFE_API_KEY=test-only-jev-key",
        "OPENAI_API_KEY=test-only-openai-key",
        "spring.ai.model.chat=none",
        "app.stripe.secret-key=sk_test_not_called",
        "app.stripe.webhook-secret=whsec_not_called",
        "app.stripe.university-webhook-secret=whsec_not_called",
        "app.stripe.a-level-webhook-secret=whsec_not_called",
        "app.professional.report.stripe-price-id=price_not_called",
        "app.university.report.stripe-price-id=price_not_called",
        "app.a-level.report.stripe-price-id=price_not_called",
        "app.professional.evidence.classified-profession=Software Developer",
        "app.professional.evidence.canonical-occupation=Programmers and software development professionals",
        "app.professional.evidence.soc2020-code=2134"})
class ComplimentaryAccessProdProfileTest extends ComplimentaryAccessFlowTestSupport {
    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;
}
