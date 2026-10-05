package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.fixture.LocalUniversityPaymentSimulator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** Explicit, clearly development-only local confirmation. It exists only under local-fixture. */
@Controller
@Profile("local-fixture")
public final class LocalUniversityCheckoutController {

    private final UniversityReportPurchaseUseCase purchaseService;
    private final LocalUniversityPaymentSimulator simulator;

    public LocalUniversityCheckoutController(UniversityReportPurchaseUseCase purchaseService,
            LocalUniversityPaymentSimulator simulator) {
        this.purchaseService = purchaseService;
        this.simulator = simulator;
    }

    @GetMapping("/university-assessment/{assessmentId}/checkout/local")
    public String show(@PathVariable UUID assessmentId, @RequestParam String session,
            Model model, HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != UniversityEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "university-assessment/not-found";
        }
        model.addAttribute("assessmentId", assessmentId);
        model.addAttribute("sessionId", session);
        return "university-assessment/local-checkout";
    }

    @PostMapping("/university-assessment/{assessmentId}/checkout/local/confirm")
    public String confirm(@PathVariable UUID assessmentId, @RequestParam String session,
            HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != UniversityEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "university-assessment/not-found";
        }
        purchaseService.processPaymentNotification(simulator.successfulNotification(entitlement.orElseThrow()));
        return "redirect:/university-assessment/" + assessmentId + "/report";
    }
}
