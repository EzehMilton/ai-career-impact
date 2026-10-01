package com.chikere.futureai.payment.university.stripe;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

record StripeCheckoutData(String sessionId, URI redirectUrl, Optional<Instant> expiresAt) { }
