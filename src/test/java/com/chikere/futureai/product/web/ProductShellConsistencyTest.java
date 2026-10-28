package com.chikere.futureai.product.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the product shell as a structural invariant rather than a habit, so a future page cannot
 * quietly ship without the shared chrome, leak development-only wording into production HTML, or
 * grow a download link on a page a visitor has not paid for.
 *
 * <p>This reads the templates as text on purpose: it covers every page at once, including ones no
 * controller test renders.</p>
 */
class ProductShellConsistencyTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void everyPageCarriesTheSharedChromeAndExactlyOneTopLevelHeading() throws IOException {
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            String html = page.getValue();
            assertThat(html).as("%s shell", page.getKey())
                    .contains("fragments/layout :: head(", "fragments/layout :: siteHeader(",
                            "fragments/layout :: siteFooter", "class=\"skip-link\"", "id=\"main\"");
            assertThat(html.split("<h1", -1)).as("%s has one h1", page.getKey()).hasSize(2);
        }
    }

    @Test
    void noPageUsesARawHtmlCommentThatWouldReachTheBrowser() throws IOException {
        // Thymeleaf passes `<!-- ... -->` straight through to the response, which once leaked the
        // words "local-fixture" onto a production page. Parser-level `<!--/* ... */-->` is removed.
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            List<String> leaked = new ArrayList<>();
            for (String fragment : page.getValue().split("<!--", -1)) {
                if (!fragment.isEmpty() && !fragment.startsWith("/*")) {
                    leaked.add(fragment.substring(0, Math.min(60, fragment.length())));
                }
            }
            // The first split fragment is the text before any comment, so one entry is expected.
            assertThat(leaked).as("%s renders a comment to the browser", page.getKey()).hasSize(1);
        }
    }

    @Test
    void onlyThePaidReportPagesOfferThePdfDownload() throws IOException {
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            String name = page.getKey().getFileName().toString();
            boolean paidReport = name.equals("paid-report.html") || name.equals("paid-report-v3.html")
                    || name.equals("paid-report-v2.html");
            // Strip parser comments (<!--/* ... */-->) — they are never sent to the browser,
            // so a sample link placeholder inside one is not a real download offer.
            String live = page.getValue().replaceAll("(?s)<!--/\\*.*?\\*/-->", "");
            // The paid download is per assessment. A static sample PDF is not a paid download.
            assertThat(live.contains("/{id}/report.pdf"))
                    .as("%s offers a PDF download", page.getKey()).isEqualTo(paidReport);
        }
    }

    @Test
    void noTemplateMentionsDevelopmentWordingWithoutAlsoBranchingOnTheDevelopmentFlag() throws IOException {
        // A file-level guard only: that the wording is positioned inside the branch, and therefore
        // absent from production HTML, is proven at render time by HomeControllerTest and
        // DevelopmentModeShellTest. This test catches the case of someone adding development copy to
        // a template that has no branch at all.
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            String rendered = page.getValue().replaceAll("(?s)<!--/\\*.*?\\*/-->", "");
            boolean mentionsDevelopment = rendered.contains("local-fixture")
                    || rendered.contains("Development shortcuts")
                    || rendered.contains("Development mode");
            if (mentionsDevelopment) {
                // Two guards are legitimate: the shell's profile flag, and a stored report's or
                // assessment's own fixture provenance. Either keeps the wording off a production page.
                assertThat(rendered).as("%s development wording is unguarded", page.getKey())
                        .containsAnyOf("${developmentMode}", "${report.developmentFixture}", "${developmentFixture}");
            }
        }
    }

    @Test
    void everyPageUsesTheSingleStylesheetAndNoUiFrameworkWasIntroduced() throws IOException {
        String layout = Files.readString(TEMPLATES.resolve("fragments/layout.html"));
        assertThat(layout).contains("/css/app.css");
        assertThat(Files.exists(Path.of("src/main/resources/static/css/app.css"))).isTrue();

        for (Map.Entry<Path, String> page : Stream.concat(pages().entrySet().stream(),
                Stream.of(Map.entry(Path.of("fragments/layout.html"), layout))).toList()) {
            assertThat(page.getValue()).as("%s stylesheets", page.getKey())
                    .doesNotContain("professional-assessment.css", "cdn.", "tailwind", "bootstrap",
                            "unpkg", "<script src");
        }
    }

    @Test
    void noTemplateRendersUserContentWithoutEscapingIt() throws IOException {
        // Everything a visitor types is reflected back somewhere: a job title on a result page, a
        // course name in a report heading. Thymeleaf escapes with th:text and does not with
        // th:utext, so the safe default has to be the only option in the codebase.
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            assertThat(page.getValue()).as("%s unescaped output", page.getKey())
                    .doesNotContain("th:utext", "[(${", "data-th-utext");
        }
        assertThat(Files.readString(TEMPLATES.resolve("fragments/layout.html")))
                .doesNotContain("th:utext", "[(${");
    }

    @Test
    void everyPageLetsAVisitorReachHomeAgain() throws IOException {
        for (Map.Entry<Path, String> page : pages().entrySet()) {
            // Either an explicit in-page link or the shared footer navigation, which every page has.
            assertThat(page.getValue()).as("%s route home", page.getKey())
                    .contains("fragments/layout :: siteFooter");
        }
    }

    private Map<Path, String> pages() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            return files.filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> !path.toString().contains("fragments"))
                    .collect(java.util.stream.Collectors.toMap(TEMPLATES::relativize, path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }));
        }
    }
}
