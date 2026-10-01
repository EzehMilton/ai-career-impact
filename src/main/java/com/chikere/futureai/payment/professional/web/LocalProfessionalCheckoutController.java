package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseService;
import com.chikere.futureai.payment.professional.fixture.LocalProfessionalPaymentSimulator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@Profile("local-fixture")
public final class LocalProfessionalCheckoutController {
    private final ProfessionalReportPurchaseService purchaseService;
    private final LocalProfessionalPaymentSimulator simulator;

    public LocalProfessionalCheckoutController(ProfessionalReportPurchaseService purchaseService,
            LocalProfessionalPaymentSimulator simulator) {
        this.purchaseService = purchaseService;
        this.simulator = simulator;
    }

    @GetMapping("/professional-assessment/{assessmentId}/checkout/local")
    public String show(@PathVariable UUID assessmentId, @RequestParam String session,
            Model model, HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != ProfessionalEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "professional-assessment/not-found";
        }
        model.addAttribute("assessmentId", assessmentId);
        model.addAttribute("sessionId", session);
        return "professional-assessment/local-checkout";
    }

    @PostMapping("/professional-assessment/{assessmentId}/checkout/local/confirm")
    public String confirm(@PathVariable UUID assessmentId, @RequestParam String session,
            HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != ProfessionalEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "professional-assessment/not-found";
        }
        purchaseService.processPaymentNotification(simulator.successfulNotification(entitlement.orElseThrow()));
        return "redirect:/professional-assessment/" + assessmentId + "/report";
    }
}
