package com.chikere.futureai.responsibility.professional.persistence;

import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisInput;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisStore;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailure;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisStatus;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Stores completed snapshots insert-only. The unique assessment_id constraint is the guard that works across
 * application instances: a losing concurrent insert fails in its own transaction and the winner is reloaded.
 */
@Repository
class JpaProfessionalResponsibilityAnalysisStore implements ProfessionalResponsibilityAnalysisStore {

    private final ProfessionalResponsibilityAnalysisJpaRepository analyses;
    private final ProfessionalResponsibilityAnalysisFailureJpaRepository failures;
    private final EntityManager entityManager;
    private final TransactionTemplate newTransaction;
    private final TransactionTemplate readOnly;

    JpaProfessionalResponsibilityAnalysisStore(ProfessionalResponsibilityAnalysisJpaRepository analyses,
            ProfessionalResponsibilityAnalysisFailureJpaRepository failures, EntityManager entityManager,
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
    public Optional<ProfessionalResponsibilityAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> analyses.findByAssessmentId(assessmentId).map(this::toSnapshot));
    }

    @Override
    public ProfessionalResponsibilityAnalysisSnapshot saveCompletedIfAbsent(ProfessionalResponsibilityAnalysisSnapshot snapshot) {
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
                .orElseThrow(() -> new IllegalStateException("the responsibility analysis was not persisted"));
    }

    @Override
    public void recordFailure(ResponsibilityAnalysisFailure failure) {
        newTransaction.executeWithoutResult(status -> {
            var entity = new ProfessionalResponsibilityAnalysisFailureEntity();
            entity.id = failure.failureId();
            entity.assessmentId = failure.assessmentId();
            entity.failedAt = failure.failedAt();
            entity.stage = failure.stage().name();
            entity.errorType = failure.errorType();
            entity.errorMessage = failure.errorMessage();
            entity.schemaVersion = failure.schemaVersion();
            entity.policyVersion = failure.policyVersion();
            entityManager.persist(entity);
        });
    }

    @Override
    public List<ResponsibilityAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId) {
        return readOnly.execute(status -> failures.findByAssessmentIdOrderByFailedAtAsc(assessmentId).stream()
                .map(entity -> new ResponsibilityAnalysisFailure(entity.id, entity.assessmentId, entity.failedAt,
                        ResponsibilityAnalysisFailure.Stage.valueOf(entity.stage), entity.errorType, entity.errorMessage,
                        entity.schemaVersion, entity.policyVersion))
                .toList());
    }

    private ProfessionalResponsibilityAnalysisEntity toEntity(ProfessionalResponsibilityAnalysisSnapshot snapshot) {
        var entity = new ProfessionalResponsibilityAnalysisEntity();
        entity.id = snapshot.snapshotId();
        entity.assessmentId = snapshot.assessmentId();
        entity.status = snapshot.status().name();
        entity.schemaVersion = snapshot.schemaVersion();
        entity.policyVersion = snapshot.policyVersion();
        entity.judgmentSource = snapshot.judgmentSource().name();
        entity.model = snapshot.model().orElse(null);
        entity.providerRequestId = snapshot.providerRequestId().orElse(null);
        entity.inputTokens = snapshot.inputTokens().isPresent() ? snapshot.inputTokens().getAsLong() : null;
        entity.outputTokens = snapshot.outputTokens().isPresent() ? snapshot.outputTokens().getAsLong() : null;
        entity.profession = snapshot.input().profession();
        entity.workDescription = snapshot.input().workDescription();
        entity.skills = new ArrayList<>(snapshot.input().skills());
        var regulated = snapshot.regulatedContext();
        entity.regulatedContextValue = regulated.value();
        entity.regulatedContextLevel = snapshot.regulatedContextLevel().name();
        entity.regulatedContextConfidence = optional(regulated.providerConfidence());
        entity.regulatedContextDistribution = distribution(regulated.distribution());
        entity.regulatedContextNearestLevel = optional(regulated.nearestLevel());
        entity.regulatedContextNearestLabel = regulated.nearestLabel().orElse(null);
        entity.policyParameters = new LinkedHashMap<>(snapshot.policyParameters());
        entity.createdAt = snapshot.createdAt();
        entity.completedAt = snapshot.completedAt();
        for (ResponsibilityAnalysisItem item : snapshot.items()) {
            var row = new ProfessionalResponsibilityAnalysisEntity.ItemRow();
            row.responsibilityIndex = item.responsibilityIndex();
            row.responsibilityKey = item.responsibilityKey();
            row.responsibilityText = item.responsibilityText();
            row.describedClearly = item.describedClearly();
            row.category = item.category().name();
            row.displayLabel = item.displayLabel();
            row.needsCloseHumanOversight = item.needsCloseHumanOversight();
            row.oversightRule = item.oversightRule().orElse(null);
            row.classificationRule = item.classificationRule();
            row.closeCall = item.closeCall();
            entity.items.add(row);
            for (ResponsibilityJudgmentKind kind : ResponsibilityJudgmentKind.values()) {
                ResponsibilityScoredJudgment judgment = item.judgments().get(kind);
                var judgmentRow = new ProfessionalResponsibilityAnalysisEntity.JudgmentRow();
                judgmentRow.responsibilityIndex = item.responsibilityIndex();
                judgmentRow.judgment = kind.name();
                judgmentRow.value = judgment.value();
                judgmentRow.level = item.levels().get(kind).name();
                judgmentRow.providerConfidence = optional(judgment.providerConfidence());
                judgmentRow.distribution = distribution(judgment.distribution());
                judgmentRow.nearestLevel = optional(judgment.nearestLevel());
                judgmentRow.nearestLabel = judgment.nearestLabel().orElse(null);
                entity.judgments.add(judgmentRow);
            }
        }
        return entity;
    }

    private ProfessionalResponsibilityAnalysisSnapshot toSnapshot(ProfessionalResponsibilityAnalysisEntity entity) {
        List<ProfessionalResponsibilityAnalysisEntity.ItemRow> rows = new ArrayList<>(entity.items);
        rows.sort(Comparator.comparingInt(row -> row.responsibilityIndex));
        List<ResponsibilityAnalysisItem> items = new ArrayList<>();
        for (var row : rows) {
            Map<ResponsibilityJudgmentKind, ResponsibilityScoredJudgment> judgments = new EnumMap<>(ResponsibilityJudgmentKind.class);
            Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels = new EnumMap<>(ResponsibilityJudgmentKind.class);
            for (var judgment : entity.judgments) {
                if (judgment.responsibilityIndex != row.responsibilityIndex) continue;
                ResponsibilityJudgmentKind kind = ResponsibilityJudgmentKind.valueOf(judgment.judgment);
                judgments.put(kind, judgment(judgment.value, judgment.distribution, judgment.providerConfidence,
                        judgment.nearestLevel, judgment.nearestLabel));
                levels.put(kind, ResponsibilityLevel.valueOf(judgment.level));
            }
            items.add(new ResponsibilityAnalysisItem(row.responsibilityIndex, row.responsibilityKey, row.responsibilityText,
                    row.describedClearly, judgments, levels, ResponsibilityCategory.valueOf(row.category), row.displayLabel,
                    row.needsCloseHumanOversight, Optional.ofNullable(row.oversightRule), row.classificationRule,
                    row.closeCall));
        }
        return new ProfessionalResponsibilityAnalysisSnapshot(entity.id, entity.assessmentId,
                ResponsibilityAnalysisStatus.valueOf(entity.status), entity.schemaVersion, entity.policyVersion,
                ResponsibilityJudgmentSource.valueOf(entity.judgmentSource), Optional.ofNullable(entity.model),
                Optional.ofNullable(entity.providerRequestId),
                entity.inputTokens == null ? OptionalLong.empty() : OptionalLong.of(entity.inputTokens),
                entity.outputTokens == null ? OptionalLong.empty() : OptionalLong.of(entity.outputTokens),
                new ProfessionalResponsibilityAnalysisInput(entity.profession, entity.workDescription,
                        items.stream().map(ResponsibilityAnalysisItem::responsibilityText).toList(), entity.skills),
                judgment(entity.regulatedContextValue, entity.regulatedContextDistribution,
                        entity.regulatedContextConfidence, entity.regulatedContextNearestLevel,
                        entity.regulatedContextNearestLabel),
                ResponsibilityLevel.valueOf(entity.regulatedContextLevel), entity.policyParameters, items,
                entity.createdAt, entity.completedAt);
    }

    private static ResponsibilityScoredJudgment judgment(double value, Map<String, Double> distribution,
            Double confidence, Integer nearestLevel, String nearestLabel) {
        Map<Integer, Double> levels = new LinkedHashMap<>();
        distribution.forEach((level, probability) -> levels.put(Integer.parseInt(level), probability));
        return new ResponsibilityScoredJudgment(value, levels,
                confidence == null ? OptionalDouble.empty() : OptionalDouble.of(confidence),
                nearestLevel == null ? OptionalInt.empty() : OptionalInt.of(nearestLevel),
                Optional.ofNullable(nearestLabel));
    }

    private static Map<String, Double> distribution(Map<Integer, Double> distribution) {
        Map<String, Double> stored = new LinkedHashMap<>();
        distribution.forEach((level, probability) -> stored.put(Integer.toString(level), probability));
        return stored;
    }

    private static Double optional(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private static Integer optional(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }
}
