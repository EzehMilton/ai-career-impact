package com.chikere.futureai.product.web;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What someone sees when their CV is too big.
 *
 * <p>The size limit is turned down to 64 KB here rather than sending a six megabyte body. That is
 * not only faster: at the shipped limit the server stops reading and closes the connection partway
 * through the upload, so an HTTP client sees a broken stream rather than a reply. A browser
 * handles that and still renders the page — verified by hand against the running application — but
 * a test written that way measures the client's behaviour instead of the server's.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=64KB",
        "spring.servlet.multipart.max-request-size=128KB"})
class OversizedUploadTest {

    @MockitoBean ProfessionalJudgmentProvider professionalJudgmentProvider;
    @MockitoBean ProfessionalNarrativeGenerator professionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider universityJudgmentProvider;
    @MockitoBean UniversityNarrativeGenerator universityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider aLevelJudgmentProvider;
    @MockitoBean ALevelNarrativeGenerator aLevelNarrativeGenerator;

    @LocalServerPort int port;

    @Test
    void anOversizedCvGetsAnUnderstandablePageRatherThanAServerError() throws Exception {
        var browser = new TestBrowser(port);
        String token = browser.csrfTokenFrom("/professional-assessment");

        HttpResponse<String> response = browser.postFile("/professional-assessment/cv", token,
                "cv", "huge.pdf", pdfOfSize(96 * 1024));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body())
                .contains("That file is too large.", "Please upload a smaller CV.", "Back to home");
        assertThat(response.body()).doesNotContain("Whitelabel", "Exception", "java.lang",
                "MaxUploadSize", "max-file-size", "tomcat", "at com.chikere");
    }

    @Test
    void anUploadInsideTheLimitIsStillAcceptedAndJudgedOnItsContent() throws Exception {
        // Proves the refusal above is the size limit and not uploads being broken outright.
        var browser = new TestBrowser(port);
        String token = browser.csrfTokenFrom("/professional-assessment");

        HttpResponse<String> response = browser.postFile("/professional-assessment/cv", token,
                "cv", "small.pdf", pdfOfSize(2 * 1024));

        assertThat(response.statusCode()).isNotEqualTo(413);
        assertThat(response.body()).doesNotContain("That file is too large.");
    }

    /** Bytes that start with a PDF signature so the upload validator is reached, not the parser. */
    private byte[] pdfOfSize(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }
}
