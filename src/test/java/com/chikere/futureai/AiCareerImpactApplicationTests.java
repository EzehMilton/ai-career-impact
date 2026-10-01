package com.chikere.futureai;

import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
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

	@MockitoBean
	UniversityJudgmentProvider universityJudgmentProvider;

	@MockitoBean
	UniversityNarrativeGenerator universityNarrativeGenerator;

	@MockitoBean
	ALevelJudgmentProvider aLevelJudgmentProvider;

	@MockitoBean
	ALevelNarrativeGenerator aLevelNarrativeGenerator;

	@Test
	void contextLoads() {
	}

}
