package com.chikere.futureai.cv.professional.openai;

import com.chikere.futureai.cv.professional.application.ProfessionalProfileExtractionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiProfessionalProfileExtractorTest {
    private static final String STRUCTURED_CV = """
            Profile
            Skills
            Java, Spring Boot, PostgreSQL, clear communication

            Experience

            Software Engineer
            Company A
            08/2025 - Present
            - current responsibility A
            - current responsibility B

            Software Developer
            Company B
            04/2023 - 08/2025
            - previous responsibility C
            - previous responsibility D

            Security Engineer
            Company C
            06/2021 - 04/2023
            - older responsibility E
            """;

    @SuppressWarnings("unchecked")
    @Test
    void keepsTheCurrentRoleBoundaryAndRejectsZeroContradictedByItsDatedTenure() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        var structured = new OpenAiProfessionalProfileResponse("Software Engineer",
                "Builds and maintains software systems for current Company A products and services.",
                List.of("current responsibility A", "current responsibility B"),
                List.of("Java", "Spring Boot", "PostgreSQL"), List.of("clear communication"),
                List.of("Mentor engineers"), List.of("Approve designs"), List.of("Payments"), 0,
                List.of());
        when(response.entity(eq(OpenAiProfessionalProfileResponse.class), any(Consumer.class)))
                .thenReturn(structured);

        var extractor = new OpenAiProfessionalProfileExtractor(client,
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        var profile = extractor.extract(STRUCTURED_CV);

        assertThat(profile.currentRole()).isEqualTo("Software Engineer");
        assertThat(profile.responsibilities()).containsExactly(
                "current responsibility A", "current responsibility B");
        assertThat(profile.responsibilities()).doesNotContain(
                "previous responsibility C", "previous responsibility D", "older responsibility E");
        assertThat(profile.technicalSkills()).containsExactly("Java", "Spring Boot", "PostgreSQL");
        assertThat(profile.humanSkills()).containsExactly("clear communication");
        assertThat(profile.yearsExperience()).isEmpty();
        assertThat(profile.warnings()).contains(
                "Years of experience was omitted because zero conflicted with the dated current role.");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(request).system(system.capture());
        assertThat(system.getValue()).contains("Do not invent a job title", "Use ONLY that entry",
                "Never combine duties", "Count overlapping periods once", "Never use zero as a",
                "Separate responsibilities from skills", "Do not calculate AI exposure", "replace Jev");
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(request).user(user.capture());
        assertThat(user.getValue()).contains("2026-10-01", "current responsibility A",
                "previous responsibility C", "older responsibility E");
        ArgumentCaptor<Consumer<ChatClient.EntityParamSpec>> options = ArgumentCaptor.forClass(Consumer.class);
        verify(response).entity(eq(OpenAiProfessionalProfileResponse.class), options.capture());
        ChatClient.EntityParamSpec spec = mock(ChatClient.EntityParamSpec.class);
        options.getValue().accept(spec);
        verify(spec).useProviderStructuredOutput();
    }

    @SuppressWarnings("unchecked")
    @Test
    void ambiguousCurrentRolesAreMappedAsUnknownWithTheProviderWarning() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.entity(eq(OpenAiProfessionalProfileResponse.class), any(Consumer.class)))
                .thenReturn(new OpenAiProfessionalProfileResponse(null, null, List.of(),
                        List.of("Java"), List.of(), List.of(), List.of(), List.of(), null,
                        List.of("More than one role appears current; no role-specific details were selected.")));

        var profile = new OpenAiProfessionalProfileExtractor(client).extract("Two roles both say Present");

        assertThat(profile.currentRole()).isEmpty();
        assertThat(profile.roleSummary()).isEmpty();
        assertThat(profile.responsibilities()).isEmpty();
        assertThat(profile.yearsExperience()).isEmpty();
        assertThat(profile.warnings()).contains(
                "More than one role appears current; no role-specific details were selected.");
    }

    @SuppressWarnings("unchecked")
    @Test
    void malformedOrMissingStructuredResponseFailsClosed() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.entity(eq(OpenAiProfessionalProfileResponse.class), any(Consumer.class))).thenReturn(null);

        assertThatThrownBy(() -> new OpenAiProfessionalProfileExtractor(client).extract("cv"))
                .isInstanceOf(ProfessionalProfileExtractionException.class);
    }
}
