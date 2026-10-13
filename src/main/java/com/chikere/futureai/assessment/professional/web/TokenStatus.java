package com.chikere.futureai.assessment.professional.web;

import java.util.UUID;

public sealed interface TokenStatus permits TokenStatus.Registered, TokenStatus.InProgress, TokenStatus.Completed {
    record Registered() implements TokenStatus {}
    record InProgress() implements TokenStatus {}
    record Completed(UUID assessmentId) implements TokenStatus {}
}