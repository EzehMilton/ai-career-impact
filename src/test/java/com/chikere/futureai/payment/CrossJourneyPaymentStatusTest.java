package com.chikere.futureai.payment;

import com.chikere.futureai.payment.alevel.application.ALevelPurchaseAccess;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.web.ALevelPaymentStatusController;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseAccess;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.payment.professional.web.ProfessionalPaymentStatusController;
import com.chikere.futureai.payment.university.application.UniversityPurchaseAccess;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.web.UniversityPaymentStatusController;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each journey's status route asks only its own purchase service, so an ACTIVE purchase in one
 * journey can never be reported, and therefore never followed, through another journey's route.
 */
@WebMvcTest({ProfessionalPaymentStatusController.class, UniversityPaymentStatusController.class,
        ALevelPaymentStatusController.class})
@Import(WebSecurityConfiguration.class)
class CrossJourneyPaymentStatusTest {
    private static final UUID ID = UUID.fromString("9c4d5e6f-7a8b-4c3d-8e4f-5a6b7c8d9e0f");

    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase professional;
    @MockitoBean UniversityReportPurchaseUseCase university;
    @MockitoBean ALevelReportPurchaseUseCase aLevel;

    @Test
    void anActivePurchaseInOneJourneyIsInvisibleToTheOtherJourneysStatusRoutes() throws Exception {
        when(professional.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(ProfessionalEntitlementStatus.ACTIVE), false, Optional.empty()));
        when(university.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(Optional.empty(), true, Optional.empty()));
        when(aLevel.accessFor(ID)).thenReturn(new ALevelPurchaseAccess(Optional.empty(), true, Optional.empty()));

        mvc.perform(get("/university-assessment/" + ID + "/payment-status")).andExpect(status().isNotFound());
        mvc.perform(get("/a-level-assessment/" + ID + "/payment-status")).andExpect(status().isNotFound());
        verifyNoInteractions(professional);

        mvc.perform(get("/professional-assessment/" + ID + "/payment-status"))
                .andExpect(status().isOk()).andExpect(content().string("{\"status\":\"ACTIVE\"}"));
        verify(university, times(1)).accessFor(ID);
        verify(aLevel, times(1)).accessFor(ID);
        verifyNoMoreInteractions(university, aLevel);
    }
}
