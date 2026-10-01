package com.chikere.futureai.evidence.university.persistence;

import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUniversityEvidenceStore implements UniversityEvidenceStore {
    private final UniversityEvidenceBundleJpaRepository bundles;
    private final UniversityEvidenceItemJpaRepository items;

    JpaUniversityEvidenceStore(UniversityEvidenceBundleJpaRepository bundles,
            UniversityEvidenceItemJpaRepository items) {
        this.bundles = bundles;
        this.items = items;
    }

    @Override
    @Transactional
    public void save(UniversityEvidenceBundle stored) {
        var entity = bundles.findById(stored.assessmentId()).orElseGet(UniversityEvidenceBundleEntity::new);
        entity.assessmentId = stored.assessmentId();
        entity.status = stored.status();
        entity.evidenceVersion = stored.evidenceVersion();
        entity.source = stored.source();
        entity.providerIdentifier = stored.providerIdentifier();
        entity.retrievedAt = stored.retrievedAt();
        bundles.saveAndFlush(entity);
        items.deleteByAssessmentId(stored.assessmentId());
        var entities = new ArrayList<UniversityEvidenceItemEntity>();
        for (int index = 0; index < stored.items().size(); index++) {
            UniversityEvidenceItem value = stored.items().get(index);
            var item = new UniversityEvidenceItemEntity();
            item.id = UUID.randomUUID();
            item.assessmentId = stored.assessmentId();
            item.itemOrder = index;
            item.category = value.category();
            item.title = value.title();
            item.summary = value.summary();
            item.sourceName = value.sourceName();
            item.sourceReference = value.sourceReference().orElse(null);
            item.sourceUrl = value.sourceUrl().map(URI::toString).orElse(null);
            item.publisher = value.publisher().orElse(null);
            item.geography = value.geography().orElse(null);
            item.publicationDate = value.publicationDate().orElse(null);
            item.retrievedAt = value.retrievedAt();
            item.metricName = value.metricName().orElse(null);
            item.metricValue = value.metricValue().orElse(null);
            item.metricUnit = value.metricUnit().orElse(null);
            item.sourceQuality = value.sourceQuality();
            item.relevanceNote = value.relevanceNote();
            entities.add(item);
        }
        items.saveAllAndFlush(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UniversityEvidenceBundle> findByAssessmentId(UUID assessmentId) {
        return bundles.findById(assessmentId).map(bundle -> new UniversityEvidenceBundle(
                bundle.assessmentId, bundle.evidenceVersion, bundle.source, bundle.providerIdentifier,
                bundle.status, bundle.retrievedAt,
                items.findByAssessmentIdOrderByItemOrder(assessmentId).stream().map(this::toItem).toList()));
    }

    private UniversityEvidenceItem toItem(UniversityEvidenceItemEntity item) {
        return new UniversityEvidenceItem(item.category, item.title, item.summary, item.sourceName,
                Optional.ofNullable(item.sourceReference), Optional.ofNullable(item.sourceUrl).map(URI::create),
                Optional.ofNullable(item.publisher), Optional.ofNullable(item.geography),
                Optional.ofNullable(item.publicationDate), item.retrievedAt,
                Optional.ofNullable(item.metricName), Optional.ofNullable(item.metricValue),
                Optional.ofNullable(item.metricUnit), item.sourceQuality, item.relevanceNote);
    }
}
