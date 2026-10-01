package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.payment.alevel.fixture.LocalALevelPaymentSimulator;
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
public final class LocalALevelCheckoutController {

    private final ALevelReportPurchaseService purchaseService;
    private final LocalALevelPaymentSimulator simulator;

    public LocalALevelCheckoutController(ALevelReportPurchaseService purchaseService,
            LocalALevelPaymentSimulator simulator) {
        this.purchaseService = purchaseService;
        this.simulator = simulator;
    }

    @GetMapping("/a-level-assessment/{assessmentId}/checkout/local")
    public String show(@PathVariable UUID assessmentId, @RequestParam String session,
            Model model, HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != ALevelEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "a-level-assessment/not-found";
        }
        model.addAttribute("assessmentId", assessmentId);
        model.addAttribute("sessionId", session);
        return "a-level-assessment/local-checkout";
    }

    @PostMapping("/a-level-assessment/{assessmentId}/checkout/local/confirm")
    public String confirm(@PathVariable UUID assessmentId, @RequestParam String session,
            HttpServletResponse response) {
        var entitlement = purchaseService.findEntitlement(assessmentId);
        if (entitlement.isEmpty() || entitlement.orElseThrow().status() != ALevelEntitlementStatus.PENDING
                || !entitlement.orElseThrow().providerCheckoutSessionId().equals(session)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "a-level-assessment/not-found";
        }
        purchaseService.processPaymentNotification(simulator.successfulNotification(entitlement.orElseThrow()));
        return "redirect:/a-level-assessment/" + assessmentId + "/report";
    }
}
