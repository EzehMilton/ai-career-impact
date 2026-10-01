package com.chikere.futureai.payment.professional.application;

public final class InvalidPaymentNotificationException extends RuntimeException {
    public InvalidPaymentNotificationException(String message) { super(message); }
    public InvalidPaymentNotificationException(String message, Throwable cause) { super(message, cause); }
}
