package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.cv.professional.application.*;
import com.chikere.futureai.cv.professional.local.ProfessionalCvTestDocuments;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalCvUploadController.class)
@Import({WebSecurityConfiguration.class, ProfessionalCvUploadValidator.class})
@ExtendWith(OutputCaptureExtension.class)
class ProfessionalCvUploadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalCvAnalysisUseCase uploadService;

    @Test
    void csrfIsRequiredAndSuccessfulUploadStartsDedicatedReviewFlow(CapturedOutput output) throws Exception {
        var file = pdf("../../software.pdf", ProfessionalCvTestDocuments.pdf("Synthetic content"));
        var profile = new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable platforms and resolves complex technical trade-offs.",
                List.of("Design platforms", "Guide technical decisions"),
                List.of("Architecture"), List.of("Communication"), List.of(), List.of(), List.of(),
                OptionalInt.of(12), List.of(), ProfessionalProfileCompleteness.PARTIAL,
                List.of("Review the selected current role."));
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload("software.pdf", "application/pdf",
                ProfessionalCvVersions.PARSER_V2, "professional-cv-extractor-v2", Instant.parse("2026-10-01T08:00:00Z"));
        when(uploadService.extractProfile(any())).thenReturn(new ProfessionalCvProfileResult(profile, metadata));

        mvc.perform(multipart("/professional-assessment/cv").file(file)).andExpect(status().isForbidden());
        var response = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/cv/review"))
                .andReturn();

        assertThat(response.getRequest().getSession(false).getAttribute(ProfessionalCvSession.INPUT_METADATA))
                .isEqualTo(metadata);
        assertThat(response.getRequest().getSession(false).getAttribute(ProfessionalCvSession.PROFILE))
                .isEqualTo(profile);
        assertThat(output.getAll()).doesNotContain("Synthetic content", "Software Architect", "Design platforms");
        verify(uploadService).extractProfile(any());
    }

    @Test
    void unsupportedUploadIsSafeAndDoesNotInvokeParser() throws Exception {
        var file = new MockMultipartFile("cv", "malware.exe", "application/octet-stream", "secret".getBytes());
        String html = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Upload a genuine PDF or DOCX file").doesNotContain("Exception");
        verifyNoInteractions(uploadService);
    }

    @Test
    void parserFailureDoesNotExposeDetailsAndStillOffersManualForm() throws Exception {
        var file = pdf("cv.pdf", ProfessionalCvTestDocuments.pdf("Synthetic content"));
        when(uploadService.extractProfile(any())).thenThrow(new ProfessionalCvParsingException("secret parser detail"));
        String html = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().is(422)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Describe your work manually").doesNotContain("secret parser detail");
    }

    @Test
    void extractionFailureOffersManualEntryWithoutRecordingCvProvenance() throws Exception {
        var file = pdf("scan.pdf", ProfessionalCvTestDocuments.pdf(""));
        when(uploadService.extractProfile(any())).thenThrow(
                new ProfessionalProfileExtractionException("insufficient text"));

        var response = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(response.getResponse().getContentAsString()).contains("Describe your work manually")
                .doesNotContain("insufficient text");
        assertThat(response.getRequest().getSession(false).getAttribute(ProfessionalCvSession.INPUT_METADATA))
                .isNull();
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("cv", name, "application/pdf", bytes);
    }
}
