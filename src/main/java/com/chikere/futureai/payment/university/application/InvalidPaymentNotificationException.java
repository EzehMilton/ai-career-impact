package com.chikere.futureai.payment.university.application;

public class InvalidPaymentNotificationException extends RuntimeException {
    public InvalidPaymentNotificationException(String message) {
        super(message);
    }

    public InvalidPaymentNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
