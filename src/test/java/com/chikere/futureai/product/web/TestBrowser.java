package com.chikere.futureai.product.web;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal cookie-keeping HTTP client for tests that need to behave like a browser — chiefly
 * because a CSRF token is only valid for the session that was issued it.
 *
 * <p>Each instance has its own cookie jar, so one test's session never leaks into another's.</p>
 */
final class TestBrowser {

    private static final Pattern CSRF_TOKEN =
            Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private final String base;
    private final HttpClient client;

    TestBrowser(int port) {
        this.base = "http://localhost:" + port;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager())
                .build();
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(request(path).GET().build());
    }

    HttpResponse<String> getWithHeader(String path, String name, String value)
            throws IOException, InterruptedException {
        return send(request(path).header(name, value).GET().build());
    }

    String csrfTokenFrom(String path) throws IOException, InterruptedException {
        Matcher matcher = CSRF_TOKEN.matcher(get(path).body());
        if (!matcher.find()) {
            throw new IllegalStateException("no CSRF token found on " + path);
        }
        return matcher.group(1);
    }

    /** Posts a form. A null token sends no {@code _csrf} field at all. */
    HttpResponse<String> postForm(String path, String csrfToken, String... fields)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        if (csrfToken != null) {
            body.append("_csrf=").append(URLEncoder.encode(csrfToken, StandardCharsets.UTF_8));
        }
        for (int index = 0; index + 1 < fields.length; index += 2) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(fields[index]).append('=')
                    .append(URLEncoder.encode(fields[index + 1], StandardCharsets.UTF_8));
        }
        return send(request(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path))
                .header("Accept", "text/html,application/xhtml+xml");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
