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
        "OPENAI_API_KEY=test-only-openai-key",
        "spring.ai.openai.decision.api-key=test-only-openai-key",
        "spring.ai.model.chat=none",
        "app.stripe.enabled=false",
        "app.professional.evidence.live-enabled=false"})
class ComplimentaryAccessProdProfileTest extends ComplimentaryAccessFlowTestSupport {
    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;
}
