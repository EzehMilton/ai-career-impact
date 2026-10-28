package com.chikere.futureai.coursemodule.university.persistence;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisFailure;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleRule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.AI_LEARNING_ASSISTANCE;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.DESCRIBED_CLEARLY;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.HUMAN_JUDGEMENT;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.INDEPENDENT_DEMONSTRATION;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.INTERPERSONAL_WORK;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.PRACTICAL_APPLICATION;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.PROFESSIONAL_STANDARDS;
import static com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema.TECHNICAL_QUANTITATIVE;

/**
 * Stores completed module-analysis snapshots insert-only. The unique assessment_id constraint is the guard that works
 * across application instances: a losing concurrent insert fails in its own transaction and the winner is reloaded.
 */
@Repository
class JpaUniversityModuleAnalysisStore implements UniversityModuleAnalysisStore {

    private final UniversityModuleAnalysisJpaRepository analyses;
    private final UniversityModuleAnalysisFailureJpaRepository failures;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaUniversityModuleAnalysisStore(UniversityModuleAnalysisJpaRepository analyses,
            UniversityModuleAnalysisFailureJpaRepository failures, EntityManager entityManager,
            PlatformTransactionManager transactions) {
        this.analyses = analyses;
        this.failures = failures;
        this.entityManager = entityManager;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<UniversityModuleAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> analyses.findByAssessmentId(assessmentId).map(this::toSnapshot));
    }

    @Override
    public UniversityModuleAnalysisSnapshot saveCompletedIfAbsent(UniversityModuleAnalysisSnapshot snapshot) {
        try {
            newTransaction.executeWithoutResult(status -> {
                entityManager.persist(toEntity(snapshot));
                entityManager.flush();
            });
        }
        catch (DataIntegrityViolationException | PersistenceException duplicateOrInvalid) {
            // Inside this repository the JPA exception is not yet translated, so both forms are caught. Most likely
            // another request completed first; if no snapshot exists, the original failure stands.
            return findCompletedByAssessmentId(snapshot.assessmentId()).orElseThrow(() -> duplicateOrInvalid);
        }
        return findCompletedByAssessmentId(snapshot.assessmentId())
                .orElseThrow(() -> new IllegalStateException("the module analysis was not persisted"));
    }

    @Override
    public void recordFailure(UniversityModuleAnalysisFailure failure) {
        newTransaction.executeWithoutResult(status -> {
            var entity = new UniversityModuleAnalysisFailureEntity();
            entity.id = failure.failureId();
            entity.assessmentId = failure.assessmentId();
            entity.failedAt = failure.failedAt();
            entity.category = failure.category().name();
            entity.retryable = failure.retryable();
            entity.errorType = failure.errorType();
            entity.errorMessage = failure.errorMessage();
            entity.schemaVersion = failure.schemaVersion();
            entity.policyVersion = failure.policyVersion();
            entityManager.persist(entity);
        });
    }

    @Override
    public List<UniversityModuleAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> failures.findByAssessmentIdOrderByFailedAtAsc(assessmentId).stream()
                .map(entity -> new UniversityModuleAnalysisFailure(entity.id, entity.assessmentId, entity.failedAt,
                        UniversityModuleAnalysisFailure.Category.valueOf(entity.category), entity.retryable,
                        entity.errorType, entity.errorMessage, entity.schemaVersion, entity.policyVersion))
                .toList());
    }

    private UniversityModuleAnalysisEntity toEntity(UniversityModuleAnalysisSnapshot snapshot) {
        var entity = new UniversityModuleAnalysisEntity();
        entity.id = snapshot.snapshotId();
        entity.assessmentId = snapshot.assessmentId();
        entity.status = UniversityModuleAnalysisSnapshot.STATUS;
        entity.schemaVersion = snapshot.schemaVersion();
        entity.policyVersion = snapshot.policyVersion();
        entity.judgmentSource = snapshot.judgmentSource().name();
        entity.model = snapshot.model().orElse(null);
        entity.providerRequestId = snapshot.providerRequestId().orElse(null);
        entity.inputTokens = snapshot.inputTokens().isPresent() ? snapshot.inputTokens().getAsLong() : null;
        entity.outputTokens = snapshot.outputTokens().isPresent() ? snapshot.outputTokens().getAsLong() : null;
        entity.courseTitle = snapshot.courseTitle();
        entity.courseDescription = snapshot.courseDescription();
        entity.skillsDeveloped = new ArrayList<>(snapshot.skillsDeveloped());
        entity.selectedModuleCount = snapshot.items().size();
        entity.createdAt = snapshot.createdAt();
        entity.completedAt = snapshot.completedAt();
        for (UniversityModuleAnalysisItem item : snapshot.items()) {
            UniversityModuleJudgmentSet set = item.judgments();
            UniversityModuleClassification classification = item.classification();
            var row = new UniversityModuleAnalysisEntity.ItemRow();
            row.requestIndex = set.moduleIndex();
            row.moduleKey = set.moduleKey();
            row.moduleId = item.moduleId();
            row.moduleTitle = set.moduleTitle();
            row.category = classification.category().name();
            row.displayLabel = item.displayLabel();
            row.classificationRule = classification.rule().name();
            row.dominantValue = classification.dominantValue();
            row.competingValue = classification.competingValue();
            row.dominanceGap = classification.dominanceGap();
            row.independentDemonstrationSensitive =
                    classification.hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE);
            row.professionalStandardsRelevant = classification.hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT);
            row.closeCall = classification.closeCall();
            row.closeCallReasons = new ArrayList<>(classification.closeCallReasons());
            entity.items.add(row);
            entity.judgments.add(truthRow(set.moduleIndex(), DESCRIBED_CLEARLY, set.describedClearly()));
            entity.judgments.add(truthRow(set.moduleIndex(), PROFESSIONAL_STANDARDS, set.professionalStandards()));
            scored(set).forEach((judgment, value) -> entity.judgments.add(scoredRow(set.moduleIndex(), judgment, value)));
        }
        return entity;
    }

    private UniversityModuleAnalysisSnapshot toSnapshot(UniversityModuleAnalysisEntity entity) {
        List<UniversityModuleAnalysisEntity.ItemRow> rows = new ArrayList<>(entity.items);
        rows.sort(Comparator.comparingInt(row -> row.requestIndex));
        Map<Integer, Map<String, UniversityModuleAnalysisEntity.JudgmentRow>> judgments = new HashMap<>();
        for (var judgment : entity.judgments) {
            judgments.computeIfAbsent(judgment.requestIndex, index -> new HashMap<>()).put(judgment.judgment, judgment);
        }
        List<UniversityModuleAnalysisItem> items = new ArrayList<>();
        for (var row : rows) {
            var byName = judgments.getOrDefault(row.requestIndex, Map.of());
            var set = new UniversityModuleJudgmentSet(row.requestIndex, row.moduleKey, row.moduleTitle,
                    required(byName, DESCRIBED_CLEARLY).value, required(byName, PROFESSIONAL_STANDARDS).value,
                    judgment(required(byName, AI_LEARNING_ASSISTANCE)), judgment(required(byName, INDEPENDENT_DEMONSTRATION)),
                    judgment(required(byName, HUMAN_JUDGEMENT)), judgment(required(byName, INTERPERSONAL_WORK)),
                    judgment(required(byName, PRACTICAL_APPLICATION)), judgment(required(byName, TECHNICAL_QUANTITATIVE)));
            Set<UniversityModuleFlag> flags = EnumSet.noneOf(UniversityModuleFlag.class);
            if (row.independentDemonstrationSensitive) flags.add(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE);
            if (row.professionalStandardsRelevant) flags.add(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT);
            var classification = new UniversityModuleClassification(row.moduleKey, row.moduleTitle,
                    UniversityModuleCategory.valueOf(row.category), UniversityModuleRule.valueOf(row.classificationRule),
                    inputs(set), row.dominantValue, row.competingValue, row.dominanceGap, flags, row.closeCallReasons);
            items.add(new UniversityModuleAnalysisItem(row.moduleId, set, classification, row.displayLabel));
        }
        return new UniversityModuleAnalysisSnapshot(entity.id, entity.assessmentId, entity.schemaVersion,
                entity.policyVersion, UniversityModuleJudgmentSource.valueOf(entity.judgmentSource),
                Optional.ofNullable(entity.model), Optional.ofNullable(entity.providerRequestId),
                entity.inputTokens == null ? OptionalLong.empty() : OptionalLong.of(entity.inputTokens),
                entity.outputTokens == null ? OptionalLong.empty() : OptionalLong.of(entity.outputTokens),
                entity.courseTitle, entity.courseDescription, entity.skillsDeveloped, items, entity.createdAt,
                entity.completedAt);
    }

    private static Map<String, ModuleScoredJudgment> scored(UniversityModuleJudgmentSet set) {
        Map<String, ModuleScoredJudgment> scored = new LinkedHashMap<>();
        scored.put(AI_LEARNING_ASSISTANCE, set.aiLearningAssistance());
        scored.put(INDEPENDENT_DEMONSTRATION, set.independentDemonstration());
        scored.put(HUMAN_JUDGEMENT, set.humanJudgement());
        scored.put(INTERPERSONAL_WORK, set.interpersonalWork());
        scored.put(PRACTICAL_APPLICATION, set.practicalApplication());
        scored.put(TECHNICAL_QUANTITATIVE, set.technicalQuantitative());
        return scored;
    }

    /** The classification's input values, rebuilt from the stored judgments exactly as the policy recorded them. */
    private static Map<String, Double> inputs(UniversityModuleJudgmentSet set) {
        Map<String, Double> inputs = new LinkedHashMap<>();
        inputs.put(DESCRIBED_CLEARLY, set.describedClearly());
        inputs.put(PROFESSIONAL_STANDARDS, set.professionalStandards());
        scored(set).forEach((judgment, value) -> inputs.put(judgment, value.value()));
        return inputs;
    }

    private static UniversityModuleAnalysisEntity.JudgmentRow truthRow(int requestIndex, String judgment, double value) {
        var row = new UniversityModuleAnalysisEntity.JudgmentRow();
        row.requestIndex = requestIndex;
        row.judgment = judgment;
        row.value = value;
        row.distribution = Map.of();
        return row;
    }

    private static UniversityModuleAnalysisEntity.JudgmentRow scoredRow(int requestIndex, String judgment,
            ModuleScoredJudgment value) {
        var row = new UniversityModuleAnalysisEntity.JudgmentRow();
        row.requestIndex = requestIndex;
        row.judgment = judgment;
        row.value = value.value();
        row.providerConfidence = value.providerConfidence().isPresent() ? value.providerConfidence().getAsDouble() : null;
        Map<String, Double> distribution = new LinkedHashMap<>();
        value.distribution().forEach((level, probability) -> distribution.put(Integer.toString(level), probability));
        row.distribution = distribution;
        row.nearestLevel = value.nearestLevel().isPresent() ? value.nearestLevel().getAsInt() : null;
        row.nearestLabel = value.nearestLabel().orElse(null);
        return row;
    }

    private static ModuleScoredJudgment judgment(UniversityModuleAnalysisEntity.JudgmentRow row) {
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        row.distribution.forEach((level, probability) -> distribution.put(Integer.parseInt(level), probability));
        return new ModuleScoredJudgment(row.value, distribution,
                row.providerConfidence == null ? OptionalDouble.empty() : OptionalDouble.of(row.providerConfidence),
                row.nearestLevel == null ? OptionalInt.empty() : OptionalInt.of(row.nearestLevel),
                Optional.ofNullable(row.nearestLabel));
    }

    private static UniversityModuleAnalysisEntity.JudgmentRow required(
            Map<String, UniversityModuleAnalysisEntity.JudgmentRow> byName, String judgment) {
        var row = byName.get(judgment);
        if (row == null) {
            throw new IllegalStateException("stored module analysis is missing judgment " + judgment);
        }
        return row;
    }
}
