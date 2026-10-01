package com.chikere.futureai.payment.university.fixture;

import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;

public interface LocalUniversityPaymentSimulator {
    UniversityPaymentNotification successfulNotification(UniversityReportEntitlement entitlement);
}
