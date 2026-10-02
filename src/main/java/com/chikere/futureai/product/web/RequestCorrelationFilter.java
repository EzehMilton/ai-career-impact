package com.chikere.futureai.product.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an identifier so a report of "it failed at about half past two" can be traced
 * to the lines that describe it, without any user content being written to the log to find it by.
 *
 * <p>An inbound identifier is reused so a value assigned by a proxy or a caller survives, but only
 * after it has been checked: it ends up in log lines and in a response header, so a control
 * character or a megabyte of text would be a log-injection and a response-splitting problem rather
 * than a convenience. Anything that does not look like an identifier is replaced rather than
 * rejected, because a malformed header is not worth failing a request over.</p>
 */
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    /** Deliberately narrow: the characters an identifier needs and nothing that can break a log line. */
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = acceptable(request.getHeader(HEADER))
                ? request.getHeader(HEADER)
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        }
        finally {
            // Threads are pooled, so leaving this behind would label the next request with the
            // previous one's identifier.
            MDC.remove(MDC_KEY);
        }
    }

    private boolean acceptable(String candidate) {
        return candidate != null && ACCEPTABLE.matcher(candidate).matches();
    }
}
