package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.payment.alevel.application.*;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.payment.university.application.*;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The three journeys described uniformly, so every complimentary-access rule is asserted for each.
 * Assessments are seeded straight through their stores with fixture judgments, which works the same
 * under every profile and makes no Jev, OpenAI or Stripe call.
 */
final class ComplimentaryJourneys {
    static final String CODE = "test-only-complimentary-code-7f3a9c";
    private static final Instant ASSESSED_AT = Instant.parse("2026-10-01T12:00:00Z");

    record Journey(String name, String route, String table, Supplier<UUID> seedAssessment,
            Consumer<UUID> seedActiveStripePurchase, Consumer<UUID> seedPendingStripeCheckout,
            Function<UUID, Optional<?>> purchasedReport) {
        String entitlementTable() { return table + "_report_entitlement"; }
        String snapshotTable() { return "purchased_" + table + "_report_snapshot"; }
        String eventTable() { return "processed_" + table + "_payment_event"; }
        @Override public String toString() { return name; }
    }

    private ComplimentaryJourneys() { }

    static List<Journey> all(ApplicationContext context) {
        return List.of(professional(context), university(context), aLevel(context));
    }

    private static Journey professional(ApplicationContext context) {
        var assessments = context.getBean(ProfessionalAssessmentStore.class);
        var purchases = context.getBean(ProfessionalPurchaseStore.class);
        var reports = context.getBean(ProfessionalReportQuery.class);
        var purchaseService = context.getBean(ProfessionalReportPurchaseUseCase.class);
        Function<UUID, UUID> pending = assessmentId -> {
            UUID entitlementId = UUID.randomUUID();
            purchases.savePending(new ProfessionalReportEntitlement(entitlementId, assessmentId,
                    ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.PENDING,
                    ProfessionalPaymentProvider.STRIPE, "cs_test_" + entitlementId,
                    URI.create("https://checkout.stripe.test/" + entitlementId), "price_test",
                    Optional.empty(), Optional.empty(), ASSESSED_AT, Optional.empty()));
            return entitlementId;
        };
        return new Journey("Professional", "/professional-assessment", "professional",
                () -> {
                    var state = new ProfessionalAssessmentState("Software Architect",
                            "I design software and make consequential architecture decisions.",
                            List.of("Design platforms"), List.of("System design"), OptionalInt.of(14));
                    var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect")
                            .judge(state);
                    var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
                    UUID id = UUID.randomUUID();
                    assessments.save(new StoredProfessionalAssessment(id, ASSESSED_AT, state, judgments,
                            new ProfessionalAssessmentResult(decision,
                                    com.chikere.futureai.assessment.professional.application.AssessmentSource.LOCAL_FIXTURE,
                                    judgments.schemaVersion(), decision.policyVersion()),
                            Optional.empty(), ProfessionalAssessmentInputMetadata.manual()));
                    return id;
                },
                // Exactly what a verified Stripe webhook does: freeze the current PAID report and activate.
                assessmentId -> {
                    UUID entitlementId = pending.apply(assessmentId);
                    var report = reports.assemble(assessmentId, ProfessionalReportTier.PAID).orElseThrow();
                    purchases.confirmAndFreeze(new ProfessionalPaymentEvent("evt_" + entitlementId,
                                    ProfessionalPaymentProvider.STRIPE, "cs_test_" + entitlementId, entitlementId,
                                    assessmentId, Optional.of("pi_test"), ASSESSED_AT),
                            new PurchasedProfessionalReportSnapshot(entitlementId, assessmentId,
                                    report.reportVersion(), ASSESSED_AT, report));
                },
                pending::apply,
                purchaseService::findPurchasedReport);
    }

    private static Journey university(ApplicationContext context) {
        var assessments = context.getBean(UniversityAssessmentStore.class);
        var purchases = context.getBean(UniversityPurchaseStore.class);
        var reports = context.getBean(UniversityReportQuery.class);
        var purchaseService = context.getBean(UniversityReportPurchaseUseCase.class);
        Function<UUID, UUID> pending = assessmentId -> {
            UUID entitlementId = UUID.randomUUID();
            purchases.savePending(new UniversityReportEntitlement(entitlementId, assessmentId,
                    UniversityReportTier.PAID, UniversityEntitlementStatus.PENDING,
                    UniversityPaymentProvider.STRIPE, "cs_test_" + entitlementId,
                    URI.create("https://checkout.stripe.test/" + entitlementId), "price_test",
                    Optional.empty(), Optional.empty(), ASSESSED_AT, Optional.empty()));
            return entitlementId;
        };
        return new Journey("University", "/university-assessment", "university",
                () -> {
                    var state = new UniversityAssessmentState("Computer Science",
                            "Programming, algorithms, systems and a substantial final-year project.",
                            List.of("Algorithms", "Databases"), List.of("Programming", "Problem solving"),
                            List.of("Software engineering"), Optional.of("A UK university"),
                            Optional.of("United Kingdom"));
                    var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
                    var decision = new UniversityDecisionPolicyV1().decide(judgments);
                    UUID id = UUID.randomUUID();
                    assessments.save(new StoredUniversityAssessment(id, ASSESSED_AT, state, judgments,
                            new UniversityAssessmentResult(decision,
                                    com.chikere.futureai.assessment.university.application.AssessmentSource.LOCAL_FIXTURE,
                                    judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest")));
                    return id;
                },
                assessmentId -> {
                    UUID entitlementId = pending.apply(assessmentId);
                    var report = reports.assemble(assessmentId, UniversityReportTier.PAID).orElseThrow();
                    purchases.confirmAndFreeze(new UniversityPaymentEvent("evt_" + entitlementId,
                                    UniversityPaymentProvider.STRIPE, "cs_test_" + entitlementId, entitlementId,
                                    assessmentId, Optional.of("pi_test"), ASSESSED_AT),
                            new PurchasedUniversityReportSnapshot(entitlementId, assessmentId,
                                    report.reportVersion(), ASSESSED_AT, report));
                },
                pending::apply,
                purchaseService::findPurchasedReport);
    }

    private static Journey aLevel(ApplicationContext context) {
        var assessments = context.getBean(ALevelAssessmentStore.class);
        var purchases = context.getBean(ALevelPurchaseStore.class);
        var reports = context.getBean(ALevelReportQuery.class);
        var purchaseService = context.getBean(ALevelReportPurchaseUseCase.class);
        Function<UUID, UUID> pending = assessmentId -> {
            UUID entitlementId = UUID.randomUUID();
            purchases.savePending(new ALevelReportEntitlement(entitlementId, assessmentId,
                    ALevelReportTier.PAID, ALevelEntitlementStatus.PENDING,
                    ALevelPaymentProvider.STRIPE, "cs_test_" + entitlementId,
                    URI.create("https://checkout.stripe.test/" + entitlementId), "price_test",
                    Optional.empty(), Optional.empty(), ASSESSED_AT, Optional.empty()));
            return entitlementId;
        };
        return new Journey("A-level", "/a-level-assessment", "a_level",
                () -> {
                    var state = new ALevelAssessmentState(List.of("maths", "Economics", "geog"),
                            Map.of("Mathematics", "A", "Economics", "B"), List.of("Problem solving", "Essays"),
                            List.of("Economics"), List.of("Data analysis"),
                            Optional.of("United Kingdom"), Optional.empty());
                    var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
                    var decision = new ALevelDecisionPolicyV1().decide(judgments);
                    UUID id = UUID.randomUUID();
                    assessments.save(new StoredALevelAssessment(id, ASSESSED_AT, state, judgments,
                            new ALevelAssessmentResult(decision,
                                    com.chikere.futureai.assessment.alevel.application.AssessmentSource.LOCAL_FIXTURE,
                                    judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest")));
                    return id;
                },
                assessmentId -> {
                    UUID entitlementId = pending.apply(assessmentId);
                    var report = reports.assemble(assessmentId, ALevelReportTier.PAID).orElseThrow();
                    purchases.confirmAndFreeze(new ALevelPaymentEvent("evt_" + entitlementId,
                                    ALevelPaymentProvider.STRIPE, "cs_test_" + entitlementId, entitlementId,
                                    assessmentId, Optional.of("pi_test"), ASSESSED_AT),
                            new PurchasedALevelReportSnapshot(entitlementId, assessmentId,
                                    report.reportVersion(), ASSESSED_AT, report));
                },
                pending::apply,
                purchaseService::findPurchasedReport);
    }

    /**
     * Wraps every payment gateway bean (Stripe under prod and dev-no-jev, the local fixture under dev)
     * so a test can prove complimentary access never reaches one. A JDK proxy keeps every interface the
     * bean implements, including the local payment simulator.
     */
    static final class GatewayCallRecorder implements BeanPostProcessor {
        private final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof ProfessionalPaymentGateway || bean instanceof UniversityPaymentGateway
                    || bean instanceof ALevelPaymentGateway)) {
                return bean;
            }
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), ClassUtils.getAllInterfaces(bean),
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() != Object.class) {
                            calls.add(bean.getClass().getSimpleName() + "." + method.getName());
                        }
                        try {
                            return method.invoke(bean, args);
                        }
                        catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        List<String> calls() { return List.copyOf(calls); }

        void clear() { calls.clear(); }
    }
}
