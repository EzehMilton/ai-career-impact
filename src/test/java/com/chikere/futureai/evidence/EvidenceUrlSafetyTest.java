package com.chikere.futureai.evidence;

import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceCategory;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceItem;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceCategory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidence carries the only externally supplied links the product ever shows, and they are rendered
 * as clickable anchors in HTML and in PDFs. A link is never fetched by the server, so the risk is
 * not SSRF but what a scheme can do inside a browser: {@code javascript:} executes, and {@code data:}
 * can carry a whole document. Both are refused at construction, which is the one place every
 * adapter — fixture or live — has to pass through.
 */
class EvidenceUrlSafetyTest {

    private static final Instant RETRIEVED = Instant.parse("2026-10-01T00:00:00Z");

    private final List<Consumer<URI>> everyJourney = List.of(
            EvidenceUrlSafetyTest::professional,
            EvidenceUrlSafetyTest::university,
            EvidenceUrlSafetyTest::aLevel);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.nomisweb.co.uk/datasets/aps218",
            "http://example.org/report",
            "https://example.org/path?query=1&other=2#fragment"})
    void httpAndHttpsLinksAreAccepted(String url) {
        everyJourney.forEach(journey ->
                assertThatCode(() -> journey.accept(URI.create(url))).doesNotThrowAnyException());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "file:///etc/passwd",
            "ftp://example.org/report.pdf",
            "jar:file:///tmp/x.jar!/y",
            "classpath:application.properties",
            "vbscript:msgbox(1)"})
    void dangerousSchemesAreRefusedByEveryJourney(String url) {
        everyJourney.forEach(journey ->
                assertThatThrownBy(() -> journey.accept(URI.create(url)))
                        .as("%s", url)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("absolute HTTP(S) URL"));
    }

    @Test
    void aRelativeReferenceIsRefusedBecauseItIsNotAnExternalSource() {
        everyJourney.forEach(journey ->
                assertThatThrownBy(() -> journey.accept(URI.create("/professional-assessment")))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    private static void professional(URI url) {
        new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK, "Title",
                "Summary", "Source", Optional.empty(), Optional.of(url), Optional.empty(),
                Optional.of("United Kingdom"), Optional.empty(), RETRIEVED, Optional.empty(),
                Optional.empty(), Optional.empty(),
                com.chikere.futureai.evidence.professional.application.EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Context only");
    }

    private static void university(URI url) {
        new UniversityEvidenceItem(UniversityEvidenceCategory.values()[0], "Title", "Summary",
                "Source", Optional.empty(), Optional.of(url), Optional.empty(),
                Optional.of("United Kingdom"), Optional.empty(), RETRIEVED, Optional.empty(),
                Optional.empty(), Optional.empty(),
                com.chikere.futureai.evidence.university.application.EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Context only");
    }

    private static void aLevel(URI url) {
        new ALevelEvidenceItem(ALevelEvidenceCategory.values()[0], "Title", "Summary", "Source",
                Optional.empty(), Optional.of(url), Optional.empty(), Optional.of("United Kingdom"),
                Optional.empty(), RETRIEVED, Optional.empty(), Optional.empty(), Optional.empty(),
                com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Context only");
    }
}
