package com.chikere.futureai;

import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiCareerImpactApplicationTests {

	@MockitoBean
	ProfessionalJudgmentProvider professionalJudgmentProvider;

	@MockitoBean
	ProfessionalNarrativeGenerator professionalNarrativeGenerator;

	@Test
	void contextLoads() {
	}

}
