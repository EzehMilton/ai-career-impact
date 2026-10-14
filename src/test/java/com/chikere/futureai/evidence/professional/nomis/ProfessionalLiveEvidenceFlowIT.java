package com.chikere.futureai.evidence.professional.nomis;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.evidence.professional.application.EvidenceStatus;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationService;
import com.chikere.futureai.report.professional.application.ProfessionalReportService;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit live report flow using the verified 18A Jev snapshot; no new Jev or Stripe call. */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "app.public-base-url=https://ai-career-impact.example",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "spring.ai.openai.chat.model=${OPENAI_MODEL:gpt-4o-mini}",
        "spring.ai.typesafe.api-key=${TYPESAFE_API_KEY}",
        "spring.ai.typesafe.model=jev-latest",
        "app.professional.evidence.classified-profession=Software Developer",
        "app.professional.evidence.canonical-occupation=Programmers and software development professionals",
        "app.professional.evidence.soc2020-code=2134",
        "app.professional.evidence.geography=United Kingdom",
        "app.stripe.secret-key=${STRIPE_SECRET_KEY:sk_test_not_called}",
        "app.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_not_called}",
        "app.stripe.university-webhook-secret=${UNIVERSITY_STRIPE_WEBHOOK_SECRET:whsec_not_called}",
        "app.stripe.a-level-webhook-secret=${A_LEVEL_STRIPE_WEBHOOK_SECRET:whsec_not_called}",
        "app.professional.report.stripe-price-id=${PROFESSIONAL_STRIPE_PRICE_ID:price_not_called}",
        "app.university.report.stripe-price-id=${UNIVERSITY_STRIPE_PRICE_ID:price_not_called}",
        "app.a-level.report.stripe-price-id=${A_LEVEL_STRIPE_PRICE_ID:price_not_called}"})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "evidence.live.evaluation.enabled", matches = "true")
class ProfessionalLiveEvidenceFlowIT {
    private static final Pattern ROW = Pattern.compile(
            "^\\| [^|]*\\(`([^`]+)`\\) \\| ([0-9.]+) \\| (.*?) \\| ([0-9.]+) \\| (.*?) \\|$");

    @Autowired ProfessionalAssessmentStore assessments;
    @Autowired ProfessionalNarrativeService narratives;
    @Autowired ProfessionalOccupationClassificationService classifications;
    @Autowired ProfessionalEvidenceService evidence;
    @Autowired ProfessionalReportService reports;
    @Autowired ProfessionalReportViewMapper viewMapper;
    @Autowired SpringTemplateEngine templateEngine;

    @Test
    void savedJevDecisionRealNarrativeRealNomisPersistedPaidRendering() throws Exception {
        var state = new ProfessionalAssessmentState("Software Developer",
                "I implement specified software features, maintain tests and resolve routine defects.",
                List.of("Implement specified features", "Maintain automated tests", "Resolve routine defects"),
                List.of("Java", "Testing", "Debugging"), OptionalInt.of(5));
        var judgments = replayVerifiedJudgments();
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        assertThat(decision.measures().get(com.chikere.futureai.decision.professional.ProfessionalMeasure.AI_EXPOSURE)
                .indicator()).isCloseTo(9.446, org.assertj.core.data.Offset.offset(0.005));
        UUID id = UUID.randomUUID();
        assessments.save(new StoredProfessionalAssessment(id, Instant.now(), state, judgments,
                new ProfessionalAssessmentResult(decision, AssessmentSource.JEV,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest")));

        long narrativeStart = System.nanoTime();
        var narrative = narratives.generateFor(id);
        long narrativeMs = (System.nanoTime() - narrativeStart) / 1_000_000;
        assertThat(narrative.status()).isEqualTo(NarrativeStatus.AVAILABLE);
        assertThat(classifications.classifyFor(id)).isPresent();
        long evidenceStart = System.nanoTime();
        var gathered = evidence.gatherFor(id).orElseThrow();
        long evidenceMs = (System.nanoTime() - evidenceStart) / 1_000_000;
        assertThat(gathered.status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(gathered.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceReference()).contains("APS218 / SOC 2020 2134");
            assertThat(item.geography()).contains("United Kingdom");
        });

        var saved = evidence.findFor(id).orElseThrow();
        assertThat(saved).isEqualTo(gathered);
        var report = reports.assemble(id, ProfessionalReportTier.PAID).orElseThrow();
        assertThat(report.evidence().items()).hasSize(1);
        assertThat(report.narrative().available()).isTrue();
        var rendered = viewMapper.toView(report);
        var request = new MockHttpServletRequest();
        request.setContextPath("");
        var context = new WebContext(JakartaServletWebApplication.buildApplication(
                new MockServletContext()).buildExchange(request, new MockHttpServletResponse()));
        context.setVariable("assessmentId", id);
        context.setVariable("report", rendered);
        context.setVariable("developmentMode", false);
        String html = templateEngine.process("professional-assessment/paid-report", context);
        assertThat(html).contains("Office for National Statistics", "United Kingdom", "Source quality",
                "Official", "Occupation outlook", "Retrieved", "2134");
        assertThat(html).contains(saved.items().getFirst().metricValue().orElseThrow()
                .stripTrailingZeros().toPlainString());
        assertThat(reports.assemble(id, ProfessionalReportTier.PAID).orElseThrow()).isEqualTo(report);
        assertThat(evidence.gatherFor(id)).contains(saved);

        Path path = Path.of("target", "live-evidence", "professional-paid-flow-report.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "# Professional saved-Jev / real-OpenAI / real-Nomis flow\n\n"
                + "- Assessment ID: `" + id + "`\n"
                + "- Jev calls in this flow: 0; replayed verified 18A routine judgments\n"
                + "- OpenAI narrative status: " + narrative.status() + "; elapsed " + narrativeMs + " ms\n"
                + "- Nomis evidence status: " + saved.status() + "; elapsed " + evidenceMs + " ms\n"
                + "- Nomis requests: 2 (SOC codelist, observation)\n"
                + "- Source: " + saved.source() + " / " + saved.providerIdentifier() + "\n"
                + "- Reference: " + saved.items().getFirst().sourceReference().orElseThrow() + "\n"
                + "- Metric: " + saved.items().getFirst().metricName().orElseThrow() + ": "
                + saved.items().getFirst().metricValue().orElseThrow() + " people\n"
                + "- Retrieved at: " + saved.retrievedAt() + "\n"
                + "- Saved evidence reloaded unchanged: yes\n"
                + "- Paid report rendered with source, date, quality, category and geography: yes\n");
        System.out.println("Live paid-flow report written to " + path.toAbsolutePath());
    }

    private AssessmentJudgmentSet replayVerifiedJudgments() throws Exception {
        var rows = Files.readAllLines(Path.of("src", "test", "resources", "evaluations",
                "professional-routine-live-18a.md"));
        List<DimensionJudgment> values = new ArrayList<>();
        for (String line : rows) {
            Matcher matcher = ROW.matcher(line);
            if (!matcher.matches()) continue;
            Map<String, Double> probabilities = new LinkedHashMap<>();
            for (String token : matcher.group(5).split(", ")) {
                String[] parts = token.split("=", 2);
                probabilities.put(parts[0], Double.parseDouble(parts[1]));
            }
            String dimension = matcher.group(1);
            values.add(new DimensionJudgment(dimension, dimension,
                    OptionalDouble.of(Double.parseDouble(matcher.group(2))),
                    Optional.of(matcher.group(3)), Optional.empty(),
                    OptionalDouble.of(Double.parseDouble(matcher.group(4))), probabilities));
        }
        assertThat(values).hasSize(8);
        values.add(new DimensionJudgment("information_sufficient", "information_sufficient",
                OptionalDouble.of(0.840), Optional.empty(), Optional.empty(),
                OptionalDouble.empty(), Map.of()));
        return new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, "professional-v1", values);
    }
}
