package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentWorkflow;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentWorkflow;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentUseCase;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentWorkflow;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.configuration.ApplicationProfiles;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseService;
import com.chikere.futureai.payment.university.application.UniversityPaymentGateway;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseService;
import com.chikere.futureai.trace.AssessmentTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.List;

/**
 * Live smoke testing: local, deliberate end-to-end runs against the real providers. Everything here exists only
 * under the {@code live-smoke} profile AND {@code app.live-smoke.enabled=true}; the
 * {@link com.chikere.futureai.configuration.StartupConfigurationValidator} refuses the profile without the switch.
 *
 * <p>Submissions still go through each journey's normal workflow. The only additions are the case catalog that
 * pre-fills the normal forms, and decorators that grant detailed-report access after a submission through
 * {@link LiveSmokeEntitlements}, instead of a Stripe checkout.</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile(ApplicationProfiles.LIVE_SMOKE)
@ConditionalOnProperty(prefix = "app.live-smoke", name = "enabled", havingValue = "true")
public class LiveSmokeConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveSmokeConfiguration.class);

    @Bean
    LiveSmokeCaseCatalog liveSmokeCaseCatalog() {
        return new LiveSmokeCaseCatalog();
    }

    /** Configuration that holds a secret; any of its values is scrubbed from trace files. */
    private static final List<String> SECRET_SETTINGS = List.of("OPENAI_API_KEY", "TYPESAFE_API_KEY",
            "spring.ai.openai.api-key", "spring.ai.typesafe.api-key", "spring.datasource.password",
            "app.stripe.secret-key", "app.stripe.webhook-secret", "app.stripe.university-webhook-secret",
            "app.stripe.a-level-webhook-secret", "app.complimentary-report.code");

    /** Per-assessment trace files, fed by the existing AssessmentTrace as its sink. */
    @Bean
    LiveSmokeTraceFiles liveSmokeTraceFiles(Environment environment,
            @Value("${app.live-smoke.trace-directory:target/live-smoke/traces}") String directory) {
        return new LiveSmokeTraceFiles(Path.of(directory),
                SECRET_SETTINGS.stream().map(name -> environment.getProperty(name, "")).toList());
    }

    @Bean
    LiveSmokeRuns liveSmokeRuns(LiveSmokeTraceFiles traceFiles) {
        return new LiveSmokeRuns(traceFiles);
    }

    @Bean
    LiveSmokeEntitlements liveSmokeEntitlements(ProfessionalReportPurchaseService professional,
            UniversityReportPurchaseService university, ALevelReportPurchaseService aLevel) {
        return new LiveSmokeEntitlements(professional, university, aLevel);
    }

    @Bean
    @Primary
    ProfessionalAssessmentUseCase liveSmokeProfessionalAssessmentUseCase(ProfessionalAssessmentWorkflow workflow,
            LiveSmokeEntitlements entitlements, LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        return new LiveSmokeProfessionalAssessmentUseCase(workflow, entitlements, catalog, runs);
    }

    @Bean
    @Primary
    UniversityAssessmentUseCase liveSmokeUniversityAssessmentUseCase(UniversityAssessmentWorkflow workflow,
            LiveSmokeEntitlements entitlements, LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        return new LiveSmokeUniversityAssessmentUseCase(workflow, entitlements, catalog, runs);
    }

    @Bean
    @Primary
    ALevelAssessmentUseCase liveSmokeALevelAssessmentUseCase(ALevelAssessmentWorkflow workflow,
            LiveSmokeEntitlements entitlements, LiveSmokeCaseCatalog catalog, LiveSmokeRuns runs) {
        return new LiveSmokeALevelAssessmentUseCase(workflow, entitlements, catalog, runs);
    }

    @Bean
    LiveSmokeProviderStatus liveSmokeProviderStatus(ProfessionalJudgmentProvider professionalJudgments,
            UniversityJudgmentProvider universityJudgments, ALevelJudgmentProvider aLevelJudgments,
            ProfessionalNarrativeGenerator professionalNarrative, UniversityNarrativeGenerator universityNarrative,
            ALevelNarrativeGenerator aLevelNarrative, ObjectProvider<ProfessionalPaymentGateway> professionalPayment,
            ObjectProvider<UniversityPaymentGateway> universityPayment,
            ObjectProvider<ALevelPaymentGateway> aLevelPayment, AssessmentTrace trace,
            ObjectProvider<JdbcConnectionDetails> database) {
        List<String> judgmentSources = List.of(String.valueOf(professionalJudgments.source()),
                String.valueOf(universityJudgments.source()), String.valueOf(aLevelJudgments.source()));
        List<String> narrativeSources = List.of(String.valueOf(professionalNarrative.source()),
                String.valueOf(universityNarrative.source()), String.valueOf(aLevelNarrative.source()));
        boolean jev = judgmentSources.stream().allMatch(AssessmentSource.JEV.name()::equals);
        boolean openAi = narrativeSources.stream().allMatch(NarrativeSource.OPENAI.name()::equals);
        boolean stripeAbsent = professionalPayment.getIfAvailable() == null
                && universityPayment.getIfAvailable() == null && aLevelPayment.getIfAvailable() == null;
        var status = new LiveSmokeProviderStatus(List.of(
                new LiveSmokeProviderStatus.Row("Jev", jev ? "LIVE" : "NOT LIVE",
                        "Professional, University and A-level judgment sources: " + String.join(", ", judgmentSources),
                        jev),
                new LiveSmokeProviderStatus.Row("OpenAI", openAi ? "LIVE" : "NOT LIVE",
                        "Professional, University and A-level narrative sources: " + String.join(", ", narrativeSources),
                        openAi),
                new LiveSmokeProviderStatus.Row("Stripe", stripeAbsent ? "BYPASSED" : "CONFIGURED",
                        stripeAbsent ? "No payment gateway exists; detailed reports use the complimentary entitlement"
                                : "A payment gateway is configured, which live smoke testing never expects", stripeAbsent),
                new LiveSmokeProviderStatus.Row("Assessment trace", trace.isEnabled() ? "ENABLED" : "DISABLED",
                        "Every event is labelled executionMode LIVE_SMOKE", trace.isEnabled()),
                new LiveSmokeProviderStatus.Row("Database", "PostgreSQL",
                        database(database.getIfAvailable()), true)));
        status.rows().forEach(row -> LOGGER.info("LIVE SMOKE TEST MODE {}: {} ({})", row.name(), row.value(), row.detail()));
        if (trace.isEnabled()) {
            LOGGER.warn("Assessment tracing enabled for LIVE SMOKE TEST MODE. Logs will contain the submitted test "
                    + "data and complete AI prompts and responses.");
        }
        else {
            LOGGER.warn("LIVE SMOKE TEST MODE is running with assessment tracing DISABLED (ASSESSMENT_TRACE_ENABLED=false).");
        }
        return status;
    }

    /** The JDBC URL without its query string, where PostgreSQL drivers carry credentials. */
    private static String database(JdbcConnectionDetails details) {
        if (details == null) {
            return "configured datasource";
        }
        String url = details.getJdbcUrl();
        if (url == null) {
            return "configured datasource";
        }
        int query = url.indexOf('?');
        String withoutQuery = query < 0 ? url : url.substring(0, query);
        return withoutQuery.replaceAll("//[^/@]*@", "//");
    }
}
