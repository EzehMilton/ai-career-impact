package com.chikere.futureai.payment.professional.application;

public final class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException(String message) { super(message); }
    public PaymentUnavailableException(String message, Throwable cause) { super(message, cause); }
}
