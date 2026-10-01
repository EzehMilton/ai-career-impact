package com.chikere.futureai.payment.alevel.fixture;

import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;

public interface LocalALevelPaymentSimulator {
    ALevelPaymentNotification successfulNotification(ALevelReportEntitlement entitlement);
}
