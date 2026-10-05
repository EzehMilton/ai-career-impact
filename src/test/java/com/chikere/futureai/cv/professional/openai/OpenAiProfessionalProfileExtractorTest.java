package com.chikere.futureai.cv.professional.openai;

import com.chikere.futureai.cv.professional.application.ProfessionalProfileExtractionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiProfessionalProfileExtractorTest {
    @SuppressWarnings("unchecked")
    @Test
    void usesStrictStructuredOutputAndMapsDistinctFields() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        var structured = new OpenAiProfessionalProfileResponse("Software Architect",
                "Designs complex software systems and guides teams through trade-offs.",
                List.of("Design systems"), List.of("Java"), List.of("Communication"),
                List.of("Mentor engineers"), List.of("Approve designs"), List.of("Payments"), null,
                List.of("Several roles were present."));
        when(response.entity(eq(OpenAiProfessionalProfileResponse.class), any(Consumer.class)))
                .thenReturn(structured);

        var profile = new OpenAiProfessionalProfileExtractor(client).extract("PRIVATE-CV-CONTENT");

        assertThat(profile.currentRole()).isEqualTo("Software Architect");
        assertThat(profile.responsibilities()).containsExactly("Design systems");
        assertThat(profile.technicalSkills()).containsExactly("Java");
        assertThat(profile.humanSkills()).containsExactly("Communication");
        assertThat(profile.yearsExperience()).isEmpty();
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(request).system(system.capture());
        assertThat(system.getValue()).contains("Do not invent a job title", "never estimate it from seniority",
                "Separate responsibilities from skills", "Do not calculate AI exposure", "replace Jev");
        ArgumentCaptor<Consumer<ChatClient.EntityParamSpec>> options = ArgumentCaptor.forClass(Consumer.class);
        verify(response).entity(eq(OpenAiProfessionalProfileResponse.class), options.capture());
        ChatClient.EntityParamSpec spec = mock(ChatClient.EntityParamSpec.class);
        options.getValue().accept(spec);
        verify(spec).useProviderStructuredOutput();
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
