package com.chikere.futureai.judgment.typesafe;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Noul;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Release blocker H1, reproduced offline against a local HTTP server; no Jev call. The TypeSafe client is built exactly
 * as {@code TypeSafeAutoConfiguration} builds it: Boot's RestClient with a {@link JdkClientHttpRequestFactory} whose read
 * timeout is the configured {@code spring.ai.typesafe.timeout}. In Spring Framework 7 that factory decompresses gzip
 * responses by default, and its timeout covers the whole exchange from the moment the request is sent: when it
 * elapses after the headers arrived but before the body has been read, it closes the body under the JSON parser.
 * With a gzip body that surfaces exactly as the 28 October live failure: {@code JSON parse error: Stream closed} from
 * {@code GZIPInputStream}.
 */
class JevTransportTimeoutTest {

    private static final String BODY = "{\"model\":\"jev-test\",\"answers\":{\"information_sufficient\":"
            + "{\"type\":\"noul\",\"noul\":0.9}},\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
    /** How long the server takes to finish sending the body after sending the headers. */
    private static final Duration SLOW_BODY = Duration.ofMillis(1500);

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startSlowGzipServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] gzip = gzip(BODY);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, gzip.length);
            try (OutputStream out = exchange.getResponseBody()) {
                int half = gzip.length / 2;
                out.write(gzip, 0, half);
                out.flush();
                sleep(SLOW_BODY);
                out.write(gzip, half, gzip.length - half);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aTimeoutShorterThanTheWholeExchangeClosesTheGzipBodyMidReadAndIsNotRetried() {
        var client = autoConfiguredClient(Duration.ofMillis(500));

        // Which layer reports the close depends on timing: GZIPInputStream itself ("Stream closed", as in the live
        // failure) or the JDK body stream beneath it ("closed"). Both are the timer closing the body mid-read.
        assertThatThrownBy(() -> client.systemOne(Map.of("selected_subjects", "x"), question()))
                .isInstanceOf(TypeSafeException.class)
                .hasMessageContaining("response could not be read")
                .hasStackTraceContaining("JSON parse error")
                .hasStackTraceContaining("GZIPInputStream")
                .satisfies(failure -> assertThat(rootCause(failure).getMessage()).containsIgnoringCase("closed"));
        // A failure while reading a response that did arrive is not retried: one request, no duplicate charge.
        assertThat(requests).hasValue(1);
    }

    @Test
    void aTimeoutLongerThanTheWholeExchangeReadsTheSameGzipResponse() {
        var client = autoConfiguredClient(Duration.ofSeconds(10));

        var response = client.systemOne(Map.of("selected_subjects", "x"), question());

        assertThat(response.noul("information_sufficient").value()).isEqualTo(0.9);
        assertThat(requests).hasValue(1);
    }

    @Test
    void theApplicationConfiguresAJevTimeoutThatCoversTheWholeExchange() throws IOException {
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.PropertiesPropertySource("app",
                org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(
                        // The shipped file: the test classpath has its own application.properties.
                        new org.springframework.core.io.FileSystemResource("src/main/resources/application.properties"))));
        environment.getPropertySources().remove(
                org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

        var properties = org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("spring.ai.typesafe", org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties.class)
                .get();

        // Not the 10s SDK default: long enough for System One plus the body on a slow link.
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        // One System One request per judgment: no automatic re-send of a charged request.
        assertThat(properties.getRetry().getMaxRetries()).isZero();
    }

    /** As TypeSafeAutoConfiguration: Boot's builder cloned with a JDK request factory carrying the timeout. */
    private TypeSafeClient autoConfiguredClient(Duration timeout) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        return TypeSafeClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .defaultModel("jev-test")
                .timeout(timeout)
                .retryPolicy(RetryPolicy.defaults())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("closed")) {
                return cause;
            }
        }
        return cause;
    }

    private static Map<String, Noul> question() {
        return Map.of("information_sufficient", Noul.builder().instructions("Is it clear?").whenTrue("Yes")
                .whenFalse("No").build());
    }

    private static byte[] gzip(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
