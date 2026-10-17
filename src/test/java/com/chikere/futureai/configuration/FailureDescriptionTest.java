package com.chikere.futureai.configuration;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureDescriptionTest {

    @Test
    void namesEveryClassInTheCauseChainOutermostFirst() {
        var failure = new IllegalStateException("checkout could not be created",
                new UncheckedIOException(new IOException("connection reset")));

        assertThat(FailureDescription.of(failure))
                .isEqualTo("IllegalStateException<-UncheckedIOException<-IOException");
    }

    @Test
    void neverIncludesAMessageBecauseAMessageCanEchoRequestContent() {
        var failure = new IllegalArgumentException("job title: Senior Engineer at Acme");

        assertThat(FailureDescription.of(failure)).isEqualTo("IllegalArgumentException");
    }

    @Test
    void staysBoundedForALongChain() {
        RuntimeException failure = new RuntimeException();
        for (int depth = 0; depth < 50; depth++) {
            failure = new RuntimeException(failure);
        }

        assertThat(FailureDescription.of(failure).split("<-")).hasSize(5);
    }

    @Test
    void stopsAtACycleRatherThanLoopingForever() {
        var outer = new IllegalStateException();
        var inner = new IllegalArgumentException();
        outer.initCause(inner);
        inner.initCause(outer);

        assertThat(FailureDescription.of(outer)).isEqualTo("IllegalStateException<-IllegalArgumentException");
    }
}
