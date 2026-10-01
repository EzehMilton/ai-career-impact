package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.cv.professional.application.*;
import com.chikere.futureai.cv.professional.local.ProfessionalCvTestDocuments;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalCvUploadController.class)
@Import({WebSecurityConfiguration.class, ProfessionalCvUploadValidator.class, ProfessionalCvFormPrefillMapper.class})
class ProfessionalCvUploadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalCvUploadService uploadService;

    @Test
    void csrfIsRequiredAndSuccessfulUploadPrefillsEditableExistingForm() throws Exception {
        var file = pdf("../../software.pdf", ProfessionalCvTestDocuments.pdf("Synthetic content"));
        var result = new ProfessionalCvParseResult(Optional.of("Software Architect"),
                Optional.of("Designs dependable platforms and resolves complex technical trade-offs."),
                List.of("Design platforms", "Guide technical decisions"),
                List.of("Architecture", "Communication"), OptionalInt.of(12),
                ProfessionalCvParseStatus.EXTRACTED, List.of("Review the selected current role."),
                ProfessionalCvVersions.PARSER_V1);
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload("software.pdf", "application/pdf",
                ProfessionalCvVersions.PARSER_V1, Instant.parse("2026-10-01T08:00:00Z"));
        when(uploadService.parse(any())).thenReturn(new ProfessionalCvPrefill(result, metadata));

        mvc.perform(multipart("/professional-assessment/cv").file(file)).andExpect(status().isForbidden());
        var response = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/form"))
                .andExpect(model().attribute("cvPrefill", true))
                .andReturn();

        String html = response.getResponse().getContentAsString();
        assertThat(html).contains("Review your extracted details", "Software Architect",
                "Design platforms", "Review the selected current role.");
        assertThat(response.getRequest().getSession(false).getAttribute(ProfessionalCvSession.INPUT_METADATA))
                .isEqualTo(metadata);
        verify(uploadService).parse(any());
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
        when(uploadService.parse(any())).thenThrow(new ProfessionalCvParsingException("secret parser detail"));
        String html = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().is(422)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("enter your work details manually").doesNotContain("secret parser detail");
    }

    @Test
    void insufficientTextOffersManualEntryWithoutRecordingCvProvenance() throws Exception {
        var file = pdf("scan.pdf", ProfessionalCvTestDocuments.pdf(""));
        var result = new ProfessionalCvParseResult(Optional.empty(), Optional.empty(), List.of(), List.of(),
                OptionalInt.empty(), ProfessionalCvParseStatus.INSUFFICIENT_TEXT,
                List.of("The CV contained too little extractable text. Please enter your work details manually."),
                ProfessionalCvVersions.PARSER_V1);
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload("scan.pdf", "application/pdf",
                ProfessionalCvVersions.PARSER_V1, Instant.parse("2026-10-01T08:00:00Z"));
        when(uploadService.parse(any())).thenReturn(new ProfessionalCvPrefill(result, metadata));

        var response = mvc.perform(multipart("/professional-assessment/cv").file(file).with(csrf()))
                .andExpect(status().isOk()).andReturn();

        assertThat(response.getResponse().getContentAsString()).contains("too little extractable text")
                .doesNotContain("Review your extracted details");
        assertThat(response.getRequest().getSession(false).getAttribute(ProfessionalCvSession.INPUT_METADATA))
                .isNull();
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("cv", name, "application/pdf", bytes);
    }
}
