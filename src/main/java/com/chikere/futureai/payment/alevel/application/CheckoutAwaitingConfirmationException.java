package com.chikere.futureai.payment.alevel.application;

/** Checkout was refused because the provider has already completed payment for this purchase. */
public final class CheckoutAwaitingConfirmationException extends RuntimeException {
    public CheckoutAwaitingConfirmationException() {
        super("payment is complete at the provider and awaiting webhook confirmation");
    }
}
