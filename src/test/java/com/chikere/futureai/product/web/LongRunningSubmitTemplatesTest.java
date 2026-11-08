package com.chikere.futureai.product.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which forms show the long-running loading state: the free assessments and the paid-report preparation or retry,
 * and nothing else. Read from the template sources, so a new form is never wired by accident.
 */
class LongRunningSubmitTemplatesTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");
    private static final Pattern FORM = Pattern.compile("<form\\b[^>]*>", Pattern.DOTALL);

    /** Template, then the loading kind it includes and how many of its forms are long-running. */
    private static final Map<String, Wiring> WIRED = Map.of(
            "university-assessment/form.html", new Wiring("assessment", 1),
            "a-level-assessment/form.html", new Wiring("assessment", 1),
            "professional-assessment/form.html", new Wiring("assessment", 1),
            "university-assessment/paid-report-v2-prepare.html", new Wiring("report", 2),
            "university-assessment/paid-report-v3.html", new Wiring("report", 1),
            "a-level-assessment/paid-report-v2-prepare.html", new Wiring("report", 1),
            "a-level-assessment/paid-report-v2.html", new Wiring("report", 1),
            "professional-assessment/paid-report.html", new Wiring("report", 2),
            "professional-assessment/paid-report-v3.html", new Wiring("report", 1),
            "professional-assessment/paid-report-v4.html", new Wiring("report", 1));

    private record Wiring(String kind, int forms) {
    }

    @Test
    void eachLongRunningPageIncludesThePanelOnceAndMarksItsGenerationForms() throws IOException {
        WIRED.forEach((template, wiring) -> {
            String source = read(template);
            assertThat(source.split("fragments/loading :: panel\\('" + wiring.kind() + "'\\)", -1))
                    .as(template).hasSize(2);
            List<String> forms = forms(source);
            assertThat(forms.stream().filter(form -> form.contains("data-long-running")))
                    .as(template).hasSize(wiring.forms())
                    .allSatisfy(form -> assertThat(form).as(template)
                            .containsAnyOf("/report/prepare", "@{/university-assessment}", "@{/a-level-assessment}",
                                    "@{/professional-assessment}"));
        });
    }

    @Test
    void checkoutPaymentComplimentaryAndOtherFormsAreNeverWired() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                String template = TEMPLATES.relativize(file).toString().replace('\\', '/');
                for (String form : forms(Files.readString(file))) {
                    if (!form.contains("data-long-running")) continue;
                    assertThat(WIRED).as(template + " has an unexpected long-running form").containsKey(template);
                    assertThat(form).as(template).doesNotContain("checkout", "complimentary", "local/confirm");
                }
            }
        }
    }

    @Test
    void theHistoricalUniversityV2ReportPageIsUnchangedAndLoadsNoScript() {
        assertThat(read("university-assessment/paid-report-v2.html"))
                .doesNotContain("data-long-running", "fragments/loading", "long-running-submit.js");
    }

    @Test
    void thePanelCarriesOnlyStaticTextItsScriptAndTheAccessibilityHooks() {
        String panel = read("fragments/loading.html");
        assertThat(panel).contains("data-loading-panel hidden", "role=\"status\" aria-live=\"polite\"",
                "data-loading-heading tabindex=\"-1\"", "aria-hidden=\"true\"",
                "th:src=\"@{/js/long-running-submit.js}\"", "We’re analysing your information",
                "Preparing your detailed report", "This can take up to a minute. Please keep this page open.",
                "Reviewing your information…", "Writing your personalised guidance…", "Almost there…");
        // Nothing the visitor typed can reach the panel, and nothing claims a percentage.
        assertThat(panel).doesNotContain("${form", "${report", "${assessment", "%", "style=\"");
    }

    private static List<String> forms(String source) {
        Matcher matcher = FORM.matcher(source);
        List<String> forms = new java.util.ArrayList<>();
        while (matcher.find()) forms.add(matcher.group());
        return forms;
    }

    private static String read(String template) {
        try {
            return Files.readString(TEMPLATES.resolve(template));
        }
        catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
