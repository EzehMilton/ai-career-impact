package com.chikere.futureai.livesmoke;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentWorkflow;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentWorkflow;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentUseCase;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentWorkflow;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.livesmoke.web.LiveSmokeController;
import com.chikere.futureai.livesmoke.web.LiveSmokeShellAdvice;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without the live-smoke profile and its explicit switch, none of the smoke-test infrastructure exists. */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class LiveSmokeIsolationTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgments;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrative;
    @MockitoBean UniversityJudgmentProvider universityJudgments;
    @MockitoBean UniversityNarrativeGenerator universityNarrative;
    @MockitoBean ALevelJudgmentProvider aLevelJudgments;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrative;

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void theDashboardAndPrefillRoutesDoNotExist() throws Exception {
        for (String path : List.of("/dev/live-smoke", "/dev/live-smoke/professional/registered-nurse",
                "/dev/live-smoke/university/computer-science", "/dev/live-smoke/a-level/maths-economics-geography",
                "/dev/live-smoke/professional/new", "/dev/live-smoke/university/new", "/dev/live-smoke/a-level/new",
                "/dev/live-smoke/traces/" + java.util.UUID.randomUUID())) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void theResponsibilityAnalysisAndGuidanceTriggersDoNotExist() throws Exception {
        for (String route : List.of("/responsibility-analysis", "/responsibility-guidance", "/detailed-report")) {
            String path = "/dev/live-smoke/professional/" + java.util.UUID.randomUUID() + route;
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void noSmokeBeanExistsAndSubmissionsUseTheNormalWorkflowsUndecorated() {
        for (Class<?> type : List.of(LiveSmokeConfiguration.class, LiveSmokeController.class,
                LiveSmokeShellAdvice.class, LiveSmokeCaseCatalog.class, LiveSmokeEntitlements.class,
                LiveSmokeRuns.class, LiveSmokeProviderStatus.class, LiveSmokeTraceFiles.class,
                com.chikere.futureai.trace.AssessmentTraceSink.class)) {
            assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).isEmpty();
        }
        assertThat(context.getBean(ProfessionalAssessmentUseCase.class)).isInstanceOf(ProfessionalAssessmentWorkflow.class);
        assertThat(context.getBean(UniversityAssessmentUseCase.class)).isInstanceOf(UniversityAssessmentWorkflow.class);
        assertThat(context.getBean(ALevelAssessmentUseCase.class)).isInstanceOf(ALevelAssessmentWorkflow.class);
    }

    @Test
    void noPageRendersTheSmokeBannerOrControls() throws Exception {
        for (String path : List.of("/", "/professional-assessment", "/university-assessment", "/a-level-assessment",
                "/methodology")) {
            String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            assertThat(html).as(path).doesNotContain("LIVE SMOKE", "Live smoke", "/dev/live-smoke");
        }
    }

    @Test
    void theLiveSmokeProfileWithoutItsExplicitSwitchCreatesNoSmokeBeans() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("live-smoke"))
                .withUserConfiguration(LiveSmokeConfiguration.class, LiveSmokeController.class, LiveSmokeShellAdvice.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LiveSmokeCaseCatalog.class)
                            .doesNotHaveBean(LiveSmokeEntitlements.class).doesNotHaveBean(LiveSmokeController.class)
                            .doesNotHaveBean(LiveSmokeShellAdvice.class);
                });
        // With the switch but another profile, nothing either.
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("app.live-smoke.enabled=true")
                .withUserConfiguration(LiveSmokeConfiguration.class, LiveSmokeController.class, LiveSmokeShellAdvice.class)
                .run(context -> assertThat(context).doesNotHaveBean(LiveSmokeCaseCatalog.class)
                        .doesNotHaveBean(LiveSmokeController.class));
    }
}
