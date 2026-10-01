package com.chikere.futureai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/professional-assessment", "/professional-assessment/*",
                        "/professional-assessment/*/report", "/professional-assessment/*/checkout/local",
                        "/professional-assessment/*/report.pdf",
                        "/university-assessment", "/university-assessment/*",
                        "/university-assessment/*/report", "/university-assessment/*/checkout/local",
                        "/university-assessment/*/report.pdf",
                        "/css/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/professional-assessment",
                        "/professional-assessment/cv",
                        "/professional-assessment/*/checkout",
                        "/professional-assessment/*/checkout/local/confirm",
                        "/university-assessment",
                        "/university-assessment/*/checkout",
                        "/university-assessment/*/checkout/local/confirm",
                        "/payments/stripe/webhook",
                        "/payments/stripe/university-webhook").permitAll()
                .anyRequest().authenticated())
                // Only the Stripe webhook paths are CSRF-exempt: they are authenticated by Stripe's
                // signature instead. Every browser POST keeps CSRF protection.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/payments/stripe/webhook", "/payments/stripe/university-webhook"))
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
