package com.chikere.futureai.cv.professional.local;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The resource ceilings CV extraction runs under.
 *
 * <p>Upload size is already bounded before a document reaches the parser, but a small file can
 * still be expensive to read: a DOCX is a ZIP, and a few hundred kilobytes of it can inflate into
 * hundreds of megabytes of XML. These limits bound the work rather than the download.</p>
 *
 * @param maximumExtractedCharacters most characters of text any CV may yield
 * @param maximumPages               most pages a PDF CV may contain
 * @param maximumEntryBytes          most bytes any single entry inside a DOCX may inflate to
 * @param minimumInflateRatio        least compressed-to-expanded ratio a DOCX entry may have
 */
@Component
public record ProfessionalCvParserLimits(
        int maximumExtractedCharacters,
        int maximumPages,
        long maximumEntryBytes,
        double minimumInflateRatio) {

    public ProfessionalCvParserLimits(
            @Value("${app.professional.cv.maximum-extracted-characters:100000}") int maximumExtractedCharacters,
            @Value("${app.professional.cv.maximum-pages:50}") int maximumPages,
            @Value("${app.professional.cv.maximum-entry-bytes:16777216}") long maximumEntryBytes,
            @Value("${app.professional.cv.minimum-inflate-ratio:0.01}") double minimumInflateRatio) {
        if (maximumExtractedCharacters < 1_000) {
            throw new IllegalArgumentException("maximumExtractedCharacters must be at least 1000");
        }
        if (maximumPages < 1) {
            throw new IllegalArgumentException("maximumPages must be positive");
        }
        if (maximumEntryBytes < 1) {
            throw new IllegalArgumentException("maximumEntryBytes must be positive");
        }
        if (minimumInflateRatio <= 0 || minimumInflateRatio >= 1) {
            throw new IllegalArgumentException("minimumInflateRatio must be between 0 and 1");
        }
        this.maximumExtractedCharacters = maximumExtractedCharacters;
        this.maximumPages = maximumPages;
        this.maximumEntryBytes = maximumEntryBytes;
        this.minimumInflateRatio = minimumInflateRatio;
    }

    /** The shipped defaults, for tests and for callers that do not configure anything. */
    public static ProfessionalCvParserLimits defaults() {
        return new ProfessionalCvParserLimits(100_000, 50, 16L * 1024 * 1024, 0.01);
    }
}
