package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfessionalReportServiceTest {
    @Test
    void loadsOnlySavedComponentsAndNeverRegeneratesExternalInputs() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var assessments = mock(ProfessionalAssessmentStore.class);
        var narratives = mock(ProfessionalNarrativeStore.class);
        var evidence = mock(ProfessionalEvidenceStore.class);
        var classifications = mock(ProfessionalOccupationClassificationStore.class);
        var id = source.assessment().id();
        when(assessments.findById(id)).thenReturn(Optional.of(source.assessment()));
        when(narratives.findByAssessmentId(id)).thenReturn(source.narrative());
        when(evidence.findByAssessmentId(id)).thenReturn(source.evidence());
        when(classifications.findByAssessmentId(id)).thenReturn(source.classification());
        var service = new ProfessionalReportService(assessments, narratives, evidence, classifications,
                new ProfessionalReportAssembler());

        var report = service.assemble(id, ProfessionalReportTier.PAID);

        assertThat(report).isPresent();
        verify(assessments).findById(id);
        verify(narratives).findByAssessmentId(id);
        verify(evidence).findByAssessmentId(id);
        verify(classifications).findByAssessmentId(id);
        verifyNoMoreInteractions(assessments, narratives, evidence, classifications);
    }
}
