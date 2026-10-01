package com.chikere.futureai.evidence.alevel.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record ALevelEvidenceItem(
        ALevelEvidenceCategory category,
        String title,
        String summary,
        String sourceName,
        Optional<String> sourceReference,
        Optional<URI> sourceUrl,
        Optional<String> publisher,
        Optional<String> geography,
        Optional<LocalDate> publicationDate,
        Instant retrievedAt,
        Optional<String> metricName,
        Optional<BigDecimal> metricValue,
        Optional<String> metricUnit,
        EvidenceSourceQuality sourceQuality,
        String relevanceNote) {

    public ALevelEvidenceItem {
        Objects.requireNonNull(category);
        title = required(title, "title");
        summary = required(summary, "summary");
        sourceName = required(sourceName, "sourceName");
        sourceReference = clean(sourceReference);
        sourceUrl = Objects.requireNonNull(sourceUrl);
        sourceUrl.ifPresent(ALevelEvidenceItem::requireSafeHttpUrl);
        publisher = clean(publisher);
        geography = clean(geography);
        publicationDate = Objects.requireNonNull(publicationDate);
        Objects.requireNonNull(retrievedAt);
        metricName = clean(metricName);
        metricValue = Objects.requireNonNull(metricValue);
        metricUnit = clean(metricUnit);
        long metricParts = java.util.stream.Stream.of(metricName, metricValue, metricUnit)
                .filter(Optional::isPresent).count();
        if (metricParts != 0 && metricParts != 3) {
            throw new IllegalArgumentException("metric name, value and unit must be supplied together");
        }
        Objects.requireNonNull(sourceQuality);
        relevanceNote = required(relevanceNote, "relevanceNote");
    }

    private static Optional<String> clean(Optional<String> value) {
        return Objects.requireNonNull(value).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }

    private static void requireSafeHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        if (uri.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return;
        throw new IllegalArgumentException("sourceUrl must be an absolute HTTP(S) URL");
    }
}
