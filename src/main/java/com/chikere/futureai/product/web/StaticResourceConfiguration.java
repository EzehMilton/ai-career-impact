package com.chikere.futureai.product.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Lets a browser keep the stylesheet for a short while.
 *
 * <p>Every other response is deliberately {@code no-store}, because almost every page in this
 * application can show someone their own assessment. The stylesheet is the one exception: it is
 * shared by every visitor and contains nothing personal, so revalidating it on every navigation is
 * cost without benefit.</p>
 *
 * <p>The handler is registered here rather than configured through {@code spring.web.resources}
 * because that property did not reach the response in this application, and a cache policy that
 * silently does nothing is worse than none. Registering the mapping applies it directly, which is
 * verifiable — and RequestHardeningTest verifies it.</p>
 */
@Configuration(proxyBeanMethods = false)
public class StaticResourceConfiguration implements WebMvcConfigurer {

    private final Duration cacheFor;

    public StaticResourceConfiguration(
            @Value("${app.static-resources.cache-seconds:300}") long cacheSeconds) {
        if (cacheSeconds < 0) {
            throw new IllegalArgumentException("cacheSeconds must not be negative");
        }
        this.cacheFor = Duration.ofSeconds(cacheSeconds);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(cacheFor).cachePublic());
    }
}
