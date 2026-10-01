package com.chikere.futureai.payment.professional.stripe;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

record StripeVerifiedEvent(String eventId, String sessionId, UUID entitlementId, UUID assessmentId,
                           Optional<String> paymentReference, Instant occurredAt) { }
