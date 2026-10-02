package com.chikere.futureai.configuration;

import java.net.URI;
import java.util.Objects;

/**
 * The one address the application believes it is reachable at.
 *
 * <p>Every absolute URL the application hands to someone else — a Stripe success URL, a cancel URL —
 * is built from this. It is configuration rather than something derived from a {@code Host} header,
 * because a header is supplied by the caller: deriving a payment return address from one would let
 * a visitor choose where a payment comes back to.</p>
 *
 * <p>The value is normalised to end in a slash so {@code resolve} behaves the same whether or not
 * the operator remembered one.</p>
 */
public record PublicBaseUrl(URI value) {

    public PublicBaseUrl {
        Objects.requireNonNull(value, "value must not be null");
        if (!value.isAbsolute()) {
            throw new ConfigurationProblemException(
                    "app.public-base-url must be an absolute URL, for example https://example.com");
        }
        String scheme = value.getScheme() == null ? "" : value.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ConfigurationProblemException(
                    "app.public-base-url must use http or https");
        }
        if (value.getHost() == null || value.getHost().isBlank()) {
            throw new ConfigurationProblemException("app.public-base-url must name a host");
        }
        if (value.getQuery() != null || value.getFragment() != null) {
            throw new ConfigurationProblemException(
                    "app.public-base-url must not contain a query string or fragment");
        }
        String text = value.toString();
        value = URI.create(text.endsWith("/") ? text : text + "/");
    }

    public static PublicBaseUrl of(String text) {
        if (text == null || text.isBlank()) {
            throw new ConfigurationProblemException("Missing required configuration: app.public-base-url");
        }
        try {
            return new PublicBaseUrl(URI.create(text.strip()));
        }
        catch (IllegalArgumentException malformed) {
            // The value itself is not echoed: it is not a secret, but a parse error quoting raw
            // configuration is how configuration ends up in logs that get shared.
            throw new ConfigurationProblemException("app.public-base-url is not a valid URL");
        }
    }

    public boolean isSecure() {
        return "https".equalsIgnoreCase(value.getScheme());
    }

    public URI resolve(String path) {
        return value.resolve(path.startsWith("/") ? path.substring(1) : path);
    }
}
