package com.chikere.futureai.payment.professional.fixture;

import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;

public interface LocalProfessionalPaymentSimulator {
    ProfessionalPaymentNotification successfulNotification(ProfessionalReportEntitlement entitlement);
}
